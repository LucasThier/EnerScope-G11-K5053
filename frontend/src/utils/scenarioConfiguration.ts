import { emptyDraft, fromConfiguration, toConfiguration, validateDraft } from './economicDraft';
import type { ScenarioConfiguration, ScenarioEditor, ScenarioSave } from '../types/scenarioConfiguration';
import type { Scenario } from '../types/economics';
export function initialScenario(state: ScenarioConfiguration): ScenarioSave {
 // Node values belong to the version: they are always the nodes' current ones (a recovered draft's old copy is not resent).
 const operations = Object.fromEntries(state.nodes.map(n => [n.id, n.values]));
 const simple = state.legacyDraft?.draft ?? (state.configuration ? fromConfiguration(state.configuration) : emptyDraft());
 const editor: ScenarioEditor = simple ? { mode: 'simple', value: simple } :
   { mode: 'advanced', value: state.configuration! };
 return state.draft ? { ...state.draft, token: state.token, operations } : {
  token: state.token, sourceToken: state.sourceToken, editor, configuration: state.configuration, operations,
 };
}
export function prepareScenario(state: ScenarioSave, nodes: Scenario['nodes']): ScenarioSave {
 const configuration = state.editor.mode === 'advanced' ? state.editor.value :
   Object.values(validateDraft(state.editor.value, nodes)).every(errors => !errors.length) ? toConfiguration(state.editor.value) : null;
 return { ...state, configuration };
}
export function scenarioConflict(current: ScenarioConfiguration, loaded: ScenarioConfiguration, dirty: boolean) {
 return current.token !== loaded.token && (dirty || !!loaded.draft || !!loaded.legacyDraft);
}
