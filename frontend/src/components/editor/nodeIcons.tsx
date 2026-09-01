import type { ReactElement } from 'react';
import type { NodeType } from '../../types/diagram';

interface IconProps {
  type: NodeType;
  className?: string;
}

/**
 * Small line icon per node type. Uses `currentColor`, so the colour is set by
 * the surrounding element.
 */
const paths: Partial<Record<NodeType, ReactElement>> = {
  WELL: (
    <>
      <path d="M5 3h14l-2 6H7L5 3Z" />
      <path d="M12 9v9" />
      <path d="M8 21h8" />
    </>
  ),
  GATHERING_NETWORK: (
    <>
      <circle cx="5" cy="6" r="2" />
      <circle cx="5" cy="18" r="2" />
      <circle cx="18" cy="12" r="2.5" />
      <path d="M7 6.8 15.7 11M7 17.2 15.7 13" />
    </>
  ),
  TREATMENT_PLANT: (
    <>
      <path d="M9 3h6M10 3v5l-4 9a2 2 0 0 0 2 3h8a2 2 0 0 0 2-3l-4-9V3" />
      <path d="M7.5 14h9" />
    </>
  ),
  PIPELINE: (
    <>
      <path d="M2 9h14a3 3 0 0 1 3 3v0a3 3 0 0 1-3 3H4" />
      <path d="M16 6v6M4 12v6" />
    </>
  ),
  COMPRESSING_PLANT: (
    <>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 12 15 8" />
      <path d="M12 12h.01" />
    </>
  ),
  GROUND_LIQUEFACTION_PLANT: (
    <>
      <path d="M12 2v20M4 7l16 10M20 7 4 17" />
      <path d="M12 5.5 9.5 4M12 5.5 14.5 4M12 18.5 9.5 20M12 18.5 14.5 20" />
    </>
  ),
  FLNG_UNIT: (
    <>
      <path d="M3 15h18l-2 4H5l-2-4Z" />
      <path d="M7 15V8h7l3 7" />
      <path d="M10 5.5 9 4.5M10 5.5 11 4.5" />
    </>
  ),
  SEAPORT_TERMINAL: (
    <>
      <circle cx="12" cy="5" r="2" />
      <path d="M12 7v12M8 11H5a7 7 0 0 0 14 0h-3M9 11h6" />
    </>
  ),
  LNG_CAMER: (
    <>
      <path d="M3 15h18l-2 4H5l-2-4Z" />
      <path d="M6 15V9h12v6" />
      <path d="M10 9V6h4v3" />
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
