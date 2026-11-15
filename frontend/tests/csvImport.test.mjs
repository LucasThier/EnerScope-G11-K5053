import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const csv = createLoader()(resolve(root, 'utils/csvImport.ts'));

test('the puesto column is read under any of its header names', () => {
  for (const header of ['jobTitle', 'puesto', 'Cargo', 'job title']) {
    const parsed = csv.parseImportCsv(`mail,firstName,lastName,${header}\na@b.co,Ana,Pérez, Analista \n`);
    assert.equal(parsed.rows[0].jobTitle, 'Analista', header);
    assert.equal(parsed.rows[0].error, null, header);
  }
});

test('a file without a puesto column still imports with an empty job title', () => {
  const parsed = csv.parseImportCsv('mail,firstName,lastName\na@b.co,Ana,Pérez\n');
  assert.equal(parsed.rows[0].jobTitle, '');
  assert.equal(parsed.validCount, 1);
});

test('a job title above 120 characters is reported on its row', () => {
  const parsed = csv.parseImportCsv(`mail,firstName,lastName,puesto\na@b.co,Ana,Pérez,${'x'.repeat(121)}\n`);
  assert.equal(parsed.rows[0].error, 'El puesto no puede superar los 120 caracteres.');
  assert.equal(parsed.invalidCount, 1);
});

test('the normalized csv sent to the server carries the job title', () => {
  const parsed = csv.parseImportCsv('mail,firstName,lastName,puesto\na@b.co,Ana,Pérez,"Jefa, de área"\n');
  assert.equal(
    csv.buildNormalizedCsv(parsed.rows),
    'mail,firstName,lastName,role,jobTitle\r\na@b.co,Ana,Pérez,,"Jefa, de área"\r\n',
  );
});

test('the template shows the jobTitle column and parses back cleanly', () => {
  const template = csv.buildTemplateCsv();
  assert.ok(template.includes('mail,firstName,lastName,role,jobTitle'));
  const parsed = csv.parseImportCsv(template);
  assert.equal(parsed.invalidCount, 0);
  assert.deepEqual(parsed.rows.map((row) => row.jobTitle), ['Analista', 'Gerente de proyectos']);
});
