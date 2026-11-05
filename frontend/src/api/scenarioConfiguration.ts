import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { ScenarioConfiguration, ScenarioReadiness, ScenarioSave } from '../types/scenarioConfiguration';
const base = (p: string, v: string) => '/projects/' + p + '/versions/' + v + '/scenario-configuration';
function data<T>(r: { data: ApiResponse<T> }): T {
 if (!r.data.success || r.data.data == null) throw new Error(r.data.message || 'Respuesta incompleta.');
 return r.data.data;
}
export const scenarioConfigurationApi = {
 get: (p: string, v: string) => client.get<ApiResponse<ScenarioConfiguration>>(base(p,v)).then(data),
 save: (p: string, v: string, request: ScenarioSave) => client.put<ApiResponse<ScenarioConfiguration>>(base(p,v),request).then(data),
 discard: (p: string,v: string,token: string) => client.delete<ApiResponse<ScenarioConfiguration>>(base(p,v)+'/draft',{ headers: { 'If-Match': token } }).then(data),
 readiness: (p: string) => client.get<ApiResponse<ScenarioReadiness[]>>('/projects/'+p+'/versions/simulation-readiness').then(data),
};
