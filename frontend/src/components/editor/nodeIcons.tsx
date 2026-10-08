import type { ReactElement } from 'react';
import type { NodeType } from '../../types/diagram';

interface IconProps {
  type: NodeType;
  className?: string;
}

/**
 * A distinct line icon per node type. Each icon evokes its real-world asset so
 * nodes are told apart at a glance. Uses `currentColor`, so the colour is set by
 * the surrounding element.
 */
const paths: Record<NodeType, ReactElement> = {
  // Derrick with a wellhead below it.
  WELL: (
    <>
      <path d="M6 21 12 4l6 17" />
      <path d="M9 13h6M8 17h8" />
      <path d="M12 4V2" />
    </>
  ),
  // A hub gathering flows from several branch points.
  GATHERING_NETWORK: (
    <>
      <circle cx="18" cy="12" r="2.4" />
      <circle cx="5" cy="5" r="1.7" />
      <circle cx="4" cy="12" r="1.7" />
      <circle cx="5" cy="19" r="1.7" />
      <path d="M6.5 5.6 15.7 11M5.7 12h9.9M6.5 18.4 15.7 13" />
    </>
  ),
  // A funnel/separator that treats the raw gas.
  TREATMENT_PLANT: (
    <>
      <path d="M3 4h18l-7 8v7l-4 2v-9L3 4Z" />
    </>
  ),
  // A run of pipe with flanges at both ends.
  PIPELINE: (
    <>
      <path d="M5 8h14v8H5z" />
      <path d="M5 8V6M5 16v2M19 8V6M19 16v2" />
      <path d="M9 12h6" />
    </>
  ),
  // A junction that ties two pipe runs together.
  PIPELINE_CONECTION: (
    <>
      <path d="M2 12h6a2 2 0 0 0 2-2V8M22 12h-6a2 2 0 0 1-2 2v2" />
      <circle cx="12" cy="12" r="2.4" />
    </>
  ),
  // A pressure gauge for the compressor.
  COMPRESSING_PLANT: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 12 16 8" />
      <path d="M12 4v1.5M20 12h-1.5M12 20v-1.5M4 12h1.5" />
    </>
  ),
  // A snowflake — cryogenic liquefaction.
  GROUND_LIQUEFACTION_PLANT: (
    <>
      <path d="M12 2v20M3.5 7l17 10M20.5 7l-17 10" />
      <path d="M12 6l-2-2M12 6l2-2M12 18l-2 2M12 18l2 2" />
      <path d="M5.5 8.6 4 8.2 4.4 6.7M18.5 8.6 20 8.2 19.6 6.7M5.5 15.4 4 15.8 4.4 17.3M18.5 15.4 20 15.8 19.6 17.3" />
    </>
  ),
  // A floating (offshore) liquefaction vessel with a plant on deck.
  FLNG_UNIT: (
    <>
      <path d="M3 16h18l-2.2 4H5.2L3 16Z" />
      <path d="M6 16V9h6l3 7" />
      <path d="M8 9V6h2v3" />
      <path d="M2 20c1.5 0 1.5 1 3 1s1.5-1 3-1 1.5 1 3 1 1.5-1 3-1 1.5 1 3 1 1.5-1 3-1" />
    </>
  ),
  // An anchor for the seaport terminal.
  SEAPORT_TERMINAL: (
    <>
      <circle cx="12" cy="4" r="2" />
      <path d="M12 6v13" />
      <path d="M7 10h10" />
      <path d="M5 13a7 7 0 0 0 14 0" />
      <path d="M5 13H3m16 0h2" />
    </>
  ),
  // A carrier with spherical LNG tanks on deck.
  LNG_CAMER: (
    <>
      <path d="M3 16h18l-2.2 4H5.2L3 16Z" />
      <path d="M5 16v-3h14v3" />
      <circle cx="9" cy="11" r="2" />
      <circle cx="15" cy="11" r="2" />
    </>
  ),
  // A flame — internal gas consumption.
  INTERNAL_CONSUMPTION: (
    <>
      <path d="M12 3c3 3 5 5.5 5 9a5 5 0 0 1-10 0c0-2 1-3.5 2.5-5C10 9 11 7 12 3Z" />
    </>
  ),
};

export function NodeIcon({ type, className }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[type] ?? <circle cx="12" cy="12" r="8" />}
    </svg>
  );
}
