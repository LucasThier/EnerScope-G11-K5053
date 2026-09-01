import { useEffect, useRef } from 'react';
import * as maplibregl from 'maplibre-gl';
import type { Diagram } from '../../types/diagram';
import { VERTICAL_COLORS } from './nodeCatalog';

interface MapViewProps {
  diagram: Diagram;
  projection: 'mercator' | 'globe';
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
  /** Persist a node's real-world position (marker drag or placing an unplaced node). */
  onMoveGeo: (nodeId: string, longitude: number, latitude: number) => void;
}

const CONNECTIONS_SOURCE = 'diagram-connections';

/** Minimal light basemap (CARTO light, no labels): land/water only, no clutter. */
const MAP_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    basemap: {
      type: 'raster',
      tiles: [
        'https://a.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png',
        'https://b.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png',
        'https://c.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png',
      ],
      tileSize: 256,
      attribution: '© OpenStreetMap © CARTO',
    },
  },
  layers: [{ id: 'basemap', type: 'raster', source: 'basemap' }],
};

/** Initial view: centred on Argentina. */
const INITIAL_CENTER: [number, number] = [-64, -38];
const INITIAL_ZOOM = 4;

/**
 * The "map" view: nodes are placed at their real-world position on a MapLibre
 * map that can render flat (mercator) or as a globe. Markers are draggable to
 * reposition a node; clicking the map places the currently selected node when it
 * has no position yet; connections are drawn as lines between placed endpoints.
 */
export function MapView({ diagram, projection, selectedNodeId, onSelectNode, onMoveGeo }: MapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<Map<string, maplibregl.Marker>>(new Map());
  const loadedRef = useRef(false);

  // Keep the latest callbacks/state in refs so the map's own listeners (bound
  // once) always see current values without re-initialising the map.
  const selectedRef = useRef(selectedNodeId);
  const onMoveGeoRef = useRef(onMoveGeo);
  const onSelectRef = useRef(onSelectNode);
  const diagramRef = useRef(diagram);
  selectedRef.current = selectedNodeId;
  onMoveGeoRef.current = onMoveGeo;
  onSelectRef.current = onSelectNode;
  diagramRef.current = diagram;

  // Initialise the map once.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const markers = markersRef.current;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: MAP_STYLE,
      center: INITIAL_CENTER,
      zoom: INITIAL_ZOOM,
    });
    mapRef.current = map;
    map.addControl(new maplibregl.NavigationControl(), 'top-right');

    map.on('load', () => {
      loadedRef.current = true;
      map.addSource(CONNECTIONS_SOURCE, {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
      });
      map.addLayer({
        id: CONNECTIONS_SOURCE,
        type: 'line',
        source: CONNECTIONS_SOURCE,
        paint: { 'line-color': '#2c2f36', 'line-width': 2 },
      });
      syncMarkers();
      syncConnections();
    });

    // Click on empty map: place the selected node if it has no position yet.
    map.on('click', (e: maplibregl.MapMouseEvent) => {
      const selectedId = selectedRef.current;
      if (!selectedId) return;
      const node = diagramRef.current.nodes.find((n) => n.id === selectedId);
      const geo = node?.graphData?.geographicalPosition;
      if (node && (!geo || geo.longitude == null || geo.latitude == null)) {
        onMoveGeoRef.current(selectedId, e.lngLat.lng, e.lngLat.lat);
      }
    });

    return () => {
      map.remove();
      mapRef.current = null;
      loadedRef.current = false;
      markers.clear();
    };
  }, []);

  // Projection (flat vs globe).
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const apply = () => {
      // setProjection is available on recent MapLibre; guard defensively.
      const anyMap = map as unknown as { setProjection?: (p: { type: string }) => void };
      anyMap.setProjection?.({ type: projection });
    };
    if (loadedRef.current) apply();
    else map.once('load', apply);
  }, [projection]);

  // Re-render markers and connection lines whenever the diagram changes.
  useEffect(() => {
    if (!loadedRef.current) return;
    syncMarkers();
    syncConnections();
  }, [diagram, selectedNodeId]);

  function syncMarkers() {
    const map = mapRef.current;
    if (!map) return;
    const markers = markersRef.current;
    const seen = new Set<string>();

    for (const node of diagramRef.current.nodes) {
      const geo = node.graphData?.geographicalPosition;
      if (!geo || geo.longitude == null || geo.latitude == null) continue;
      seen.add(node.id);
      let marker = markers.get(node.id);
      const color = VERTICAL_COLORS[node.type.vertical];
      if (!marker) {
        marker = new maplibregl.Marker({ draggable: true, color });
        marker.setLngLat([geo.longitude, geo.latitude]).addTo(map);
        marker.getElement().style.cursor = 'pointer';
        marker.getElement().addEventListener('click', (ev: MouseEvent) => {
          ev.stopPropagation();
          onSelectRef.current(node.id);
        });
        marker.on('dragend', () => {
          const ll = marker!.getLngLat();
          onMoveGeoRef.current(node.id, ll.lng, ll.lat);
        });
        markers.set(node.id, marker);
      } else {
        marker.setLngLat([geo.longitude, geo.latitude]);
        marker.getElement().style.color = color;
      }
      const el = marker.getElement();
      el.style.outline = node.id === selectedRef.current ? '3px solid #23262b' : 'none';
    }

    // Remove markers for nodes that no longer have a position / were deleted.
    for (const [id, marker] of markers) {
      if (!seen.has(id)) {
        marker.remove();
        markers.delete(id);
      }
    }
  }

  function syncConnections() {
    const map = mapRef.current;
    if (!map || !loadedRef.current) return;
    const source = map.getSource(CONNECTIONS_SOURCE) as maplibregl.GeoJSONSource | undefined;
    if (!source) return;
    const byId = new Map(diagramRef.current.nodes.map((n) => [n.id, n]));
    const features = diagramRef.current.connections
      .map((c) => {
        const from = byId.get(c.fromNodeId)?.graphData?.geographicalPosition;
        const to = byId.get(c.toNodeId)?.graphData?.geographicalPosition;
        if (!from || !to) return null;
        if (
          from.longitude == null ||
          from.latitude == null ||
          to.longitude == null ||
          to.latitude == null
        ) {
          return null;
        }
        return {
          type: 'Feature' as const,
          geometry: {
            type: 'LineString' as const,
            coordinates: [
              [from.longitude, from.latitude],
              [to.longitude, to.latitude],
            ] as number[][],
          },
          properties: {},
        };
      })
      .filter((f): f is NonNullable<typeof f> => f !== null);
    source.setData({ type: 'FeatureCollection', features });
  }

  return <div ref={containerRef} className="h-full w-full" />;
}
