import type { EconomicConfiguration } from './economics';
import type { EconomicDraft } from '../utils/economicDraft';
export type OperationalValue = string | number | null | { type: string; a: string | number; b?: string | number };
export interface OperationalNode {
 id: string; identity: string; name: string; type: string;
 fields: { name: string; type: string }[];
 values: Record<string, OperationalValue>;
}
export type ScenarioEditor = { mode: 'simple'; value: EconomicDraft } | { mode: 'advanced'; value: EconomicConfiguration };
export interface ScenarioSave {
 token: string; sourceToken: string; editor: ScenarioEditor; configuration: EconomicConfiguration | null;
 operations: Record<string, Record<string, OperationalValue>>;
}
export interface ScenarioConfiguration {
 token: string; sourceToken: string; configuration: EconomicConfiguration | null;
 nodes: OperationalNode[]; draft: ScenarioSave | null;
 legacyDraft: { schemaVersion: number; draft: EconomicDraft } | null;
 errors: string[];
}
export interface ScenarioReadiness { versionId: string; ready: boolean; errors: string[] }
