import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const batches = load(resolve(root, 'utils/csvImportBatches.ts'));
const csv = load(resolve(root, 'utils/csvImport.ts'));

function rowsFile(count, invalidAt = []) {
  const lines = ['mail,firstName,lastName'];
  for (let i = 0; i < count; i += 1) {
    lines.push(invalidAt.includes(i) ? `bad${i},Ana,Pérez` : `u${i}@e.co,Ana,Pérez`);
  }
  return csv.parseImportCsv(lines.join('\n') + '\n').rows;
}

function okResponse(csvText) {
  const mails = csvText.split('\r\n').slice(1).filter(Boolean).map((l) => l.split(',')[0]);
  return {
    total: mails.length,
    created: mails.length,
    failed: 0,
    credentialsCsv: 'mail,password\r\n' + mails.map((m) => `${m},pw\r\n`).join(''),
    failures: [],
  };
}

const never = new AbortController().signal;

test('limits are 500 rows and batches of 50', () => {
  assert.equal(csv.MAX_ROWS, 500);
  assert.equal(batches.BATCH_SIZE, 50);
});

test('500 rows are split into 10 batches of 50, and the remainder goes in a last one', () => {
  const sizes = (n) => batches.splitIntoBatches(Array.from({ length: n }, (_, i) => i)).map((b) => b.length);
  assert.deepEqual(sizes(500), Array(10).fill(50));
  assert.deepEqual(sizes(120), [50, 50, 20]);
  assert.deepEqual(sizes(0), []);
});

test('results are summed and credentials keep a single header', () => {
  const merged = batches.mergeResults([
    { total: 2, created: 1, failed: 1, credentialsCsv: 'mail,password\r\na@e.co,p1\r\n', failures: [{ line: 3, mail: 'x', reason: 'r' }] },
    { total: 1, created: 1, failed: 0, credentialsCsv: 'mail,password\r\nb@e.co,p2\r\n', failures: [] },
  ]);
  assert.equal(merged.total, 3);
  assert.equal(merged.created, 2);
  assert.equal(merged.failed, 1);
  assert.equal(merged.credentialsCsv, 'mail,password\r\na@e.co,p1\r\nb@e.co,p2\r\n');
  assert.equal(merged.failures.length, 1);
});

test('failure lines point to the original file line, skipping invalid rows', async () => {
  const rows = rowsFile(60, [0, 5]);
  const sent = [];
  const run = await batches.runImportBatches(rows, {
    signal: never,
    isUncertain: () => true,
    send: async (text) => {
      sent.push(text);
      const res = okResponse(text);
      if (sent.length === 2) {
        return { ...res, created: res.created - 1, failed: 1, failures: [{ line: 2, mail: 'u7@e.co', reason: 'dup' }] };
      }
      return res;
    },
  });
  assert.equal(run.status, 'completed');
  assert.equal(sent.length, 2);
  const secondBatchFirstRow = rows.filter((r) => r.error === null)[50];
  assert.equal(run.result.failures[0].line, secondBatchFirstRow.line);
  assert.equal(run.result.total, 58);
});

test('progress is reported after each batch', async () => {
  const seen = [];
  await batches.runImportBatches(rowsFile(120), {
    signal: never,
    isUncertain: () => true,
    send: async (text) => okResponse(text),
    onProgress: (done, total) => seen.push([done, total]),
  });
  assert.deepEqual(seen, [[0, 120], [50, 120], [100, 120], [120, 120]]);
});

test('a failing batch keeps earlier credentials and flags it as uncertain', async () => {
  let calls = 0;
  const run = await batches.runImportBatches(rowsFile(120), {
    signal: never,
    isUncertain: () => true,
    send: async (text) => {
      calls += 1;
      if (calls === 2) throw new Error('boom');
      return okResponse(text);
    },
  });
  assert.equal(run.status, 'failed');
  assert.equal(calls, 2);
  assert.equal(run.result.created, 50);
  assert.equal(run.uncertainRows.length, 50);
  assert.equal(run.notSentRows.length, 20);
});

test('a rejected batch (not uncertain) is reported as not sent', async () => {
  let calls = 0;
  const run = await batches.runImportBatches(rowsFile(60), {
    signal: never,
    isUncertain: () => false,
    send: async (text) => {
      calls += 1;
      if (calls === 2) throw new Error('400');
      return okResponse(text);
    },
  });
  assert.equal(run.uncertainRows.length, 0);
  assert.equal(run.notSentRows.length, 10);
});

test('cancelling mid-request stops the loop and keeps completed batches', async () => {
  const controller = new AbortController();
  let calls = 0;
  const run = await batches.runImportBatches(rowsFile(150), {
    signal: controller.signal,
    isUncertain: () => true,
    send: async (text) => {
      calls += 1;
      if (calls === 2) {
        controller.abort();
        throw new Error('canceled');
      }
      return okResponse(text);
    },
  });
  assert.equal(run.status, 'cancelled');
  assert.equal(calls, 2);
  assert.equal(run.result.created, 50);
  assert.equal(run.uncertainRows.length, 50);
  assert.equal(run.notSentRows.length, 50);
});

test('cancelling between batches sends nothing more and leaves nothing uncertain', async () => {
  const controller = new AbortController();
  let calls = 0;
  const run = await batches.runImportBatches(rowsFile(150), {
    signal: controller.signal,
    isUncertain: () => true,
    send: async (text) => {
      calls += 1;
      controller.abort();
      return okResponse(text);
    },
  });
  assert.equal(run.status, 'cancelled');
  assert.equal(calls, 1);
  assert.equal(run.uncertainRows.length, 0);
  assert.equal(run.notSentRows.length, 100);
  assert.equal(run.result.created, 50);
});

test('cancelling before the first batch yields no result', async () => {
  const controller = new AbortController();
  controller.abort();
  const run = await batches.runImportBatches(rowsFile(10), {
    signal: controller.signal,
    isUncertain: () => true,
    send: async () => {
      throw new Error('should not send');
    },
  });
  assert.equal(run.status, 'cancelled');
  assert.equal(run.result, null);
  assert.equal(run.notSentRows.length, 10);
});
