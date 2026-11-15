import type { KeyboardEvent, ReactNode } from 'react';

export interface TabItem {
  id: string;
  label: string;
}

interface TabsProps {
  label: string;
  idPrefix: string;
  tabs: TabItem[];
  active: string;
  onChange: (id: string) => void;
}

const tabDomId = (prefix: string, id: string) => `${prefix}-tab-${id}`;
const panelDomId = (prefix: string, id: string) => `${prefix}-panel-${id}`;

export function Tabs({ label, idPrefix, tabs, active, onChange }: TabsProps) {
  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') {
      return;
    }
    event.preventDefault();
    const step = event.key === 'ArrowRight' ? 1 : -1;
    const next = tabs[(index + step + tabs.length) % tabs.length];
    onChange(next.id);
    document.getElementById(tabDomId(idPrefix, next.id))?.focus();
  }

  return (
    <div role="tablist" aria-label={label} className="flex gap-1 border-b border-ink-100">
      {tabs.map((tab, index) => {
        const selected = tab.id === active;
        return (
          <button
            key={tab.id}
            id={tabDomId(idPrefix, tab.id)}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={panelDomId(idPrefix, tab.id)}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={
              '-mb-px border-b-2 px-4 py-2 text-sm font-semibold transition-colors ' +
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
              (selected
                ? 'border-brand-800 text-brand-800'
                : 'border-transparent text-ink-500 hover:text-ink-800')
            }
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

interface TabPanelProps {
  idPrefix: string;
  id: string;
  active: string;
  children: ReactNode;
}

export function TabPanel({ idPrefix, id, active, children }: TabPanelProps) {
  if (id !== active) {
    return null;
  }
  return (
    <div
      role="tabpanel"
      id={panelDomId(idPrefix, id)}
      aria-labelledby={tabDomId(idPrefix, id)}
      className="pt-6"
    >
      {children}
    </div>
  );
}
