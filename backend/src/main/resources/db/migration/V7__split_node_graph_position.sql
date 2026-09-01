-- V7: Split NodeGraphData's single position into two independent positions:
--   * graph position (graph_x / graph_y)  -> abstract diagram-canvas coordinates
--   * geographical position (longitude / latitude) -> real-world coordinates (map/globe view)
-- Replaces the previous x_position / y_position / coordinates columns.

ALTER TABLE node_graph_data ADD COLUMN graph_x   DOUBLE PRECISION;
ALTER TABLE node_graph_data ADD COLUMN graph_y   DOUBLE PRECISION;
ALTER TABLE node_graph_data ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE node_graph_data ADD COLUMN latitude  DOUBLE PRECISION;

-- Preserve any existing diagram positions; the old single `coordinates` value
-- has no meaningful mapping to a (longitude, latitude) pair and is dropped.
UPDATE node_graph_data SET graph_x = x_position, graph_y = y_position;

ALTER TABLE node_graph_data DROP COLUMN x_position;
ALTER TABLE node_graph_data DROP COLUMN y_position;
ALTER TABLE node_graph_data DROP COLUMN coordinates;
