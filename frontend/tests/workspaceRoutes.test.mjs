import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const code=ts.transpileModule(readFileSync(new URL('../src/utils/workspaceRoutes.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext}}).outputText;
const {CONFIGURATION_PATTERN,SIMULATION_PATTERN,VERSION_MANAGEMENT_PATTERN,projectDestination}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
test('configuration and results routes never activate master version management',()=>{
 for(const suffix of ['scenario-configurations','versions/v1/economics','versions/v1/economics/configuration']){
 const path='/projects/p1/'+suffix;assert.ok(CONFIGURATION_PATTERN.test(path));assert.equal(SIMULATION_PATTERN.test(path),false);assert.equal(VERSION_MANAGEMENT_PATTERN.test(path),false);
 }
 for(const suffix of ['simulations','versions/v1/economics/history','versions/v1/economics/results','versions/v1/economics/results/e1']){
 const path='/projects/p1/'+suffix;assert.ok(SIMULATION_PATTERN.test(path));assert.equal(CONFIGURATION_PATTERN.test(path),false);assert.equal(VERSION_MANAGEMENT_PATTERN.test(path),false);
 }
 assert.ok(VERSION_MANAGEMENT_PATTERN.test('/projects/p1/versions'));
});
test('switching projects preserves the module and drops foreign version and evaluation identifiers',()=>{
 for(const suffix of ['scenario-configurations','versions/v1/economics','versions/v1/economics/configuration'])assert.equal(projectDestination('p2',suffix),'/projects/p2/scenario-configurations');
 for(const suffix of ['simulations','versions/v1/economics/history','versions/v1/economics/results/e1'])assert.equal(projectDestination('p2',suffix),'/projects/p2/simulations');
 assert.equal(projectDestination('p2','versions'),'/projects/p2/versions');
 assert.equal(projectDestination('p2','team'),'/projects/p2/team');
});
