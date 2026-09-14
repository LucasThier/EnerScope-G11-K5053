const NODES = {
  n0: [40, 300], n1: [150, 250], n2: [250, 310], n3: [360, 210], n4: [470, 260],
  b0: [110, 130], b1: [230, 90], b2: [350, 40], b3: [440, 130], b4: [60, 180],
} as const;

type NodeKey = keyof typeof NODES;

const EDGES: [NodeKey, NodeKey][] = [
  ['n0', 'n1'], ['n1', 'n2'], ['n2', 'n3'], ['n3', 'n4'],
  ['n1', 'b0'], ['b0', 'b1'], ['b1', 'b2'], ['b2', 'b3'], ['b3', 'n4'],
  ['n0', 'b4'], ['b4', 'b0'], ['n2', 'b1'], ['n3', 'b2'],
];

const ACCENT: NodeKey[] = ['n0', 'n2', 'n4'];

interface NodeGraphProps {
  className?: string;
  lineClass?: string;
  nodeClass?: string;
  accentClass?: string;
  strokeWidth?: number;
}

/**
 * The value-chain network as an illustration: a spine of nodes with branches,
 * drawn at whatever size the caller gives it. Colour comes from the three
 * `*Class` props so the same graph works on the light page and on the ink-900
 * sign-in panel.
 */
export function NodeGraph({
  className = '',
  lineClass = 'text-ink-200',
  nodeClass = 'text-ink-300',
  accentClass = 'text-brand-500',
  strokeWidth = 2,
}: NodeGraphProps) {
  return (
    <svg viewBox="0 0 520 420" aria-hidden="true" className={className} fill="none">
      <g className={lineClass} stroke="currentColor" strokeWidth={strokeWidth}>
        {EDGES.map(([a, b]) => (
          <line key={`${a}${b}`} x1={NODES[a][0]} y1={NODES[a][1]} x2={NODES[b][0]} y2={NODES[b][1]} />
        ))}
      </g>
      <g className={nodeClass} fill="currentColor">
        {(Object.keys(NODES) as NodeKey[])
          .filter((key) => !ACCENT.includes(key))
          .map((key) => (
            <circle key={key} cx={NODES[key][0]} cy={NODES[key][1]} r="6" />
          ))}
      </g>
      <g className={accentClass} fill="currentColor">
        {ACCENT.map((key) => (
          <circle key={key} cx={NODES[key][0]} cy={NODES[key][1]} r="9" />
        ))}
      </g>
    </svg>
  );
}
