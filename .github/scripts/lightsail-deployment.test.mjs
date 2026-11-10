import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  buildDeployment,
  parseDotenv,
  validateEnv,
} from './lightsail-deployment.mjs';

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), 'lightsail-deployment.mjs');

const PRODUCTION_ENV = {
  DATASOURCE_URL: 'jdbc:postgresql://db.example.com:5432/enerscope?sslmode=require',
  DATASOURCE_USERNAME: 'enerscope',
  DATASOURCE_PASSWORD: 's3cret',
  JWT_SECRET: 'a-production-secret-that-is-long-enough-123',
  ADMIN_PASSWORD: 'a-strong-admin-password',
};

const toDotenv = (env) => Object.entries(env).map(([k, v]) => `${k}=${v}`).join('\n');

test('parseDotenv reads KEY=VALUE lines and skips comments and blank lines', () => {
  const env = parseDotenv('# comment\n\nLOG_LEVEL_APP=INFO\r\n  export ADMIN_MAIL = admin@enerscope.org  \n');
  assert.deepEqual(env, { LOG_LEVEL_APP: 'INFO', ADMIN_MAIL: 'admin@enerscope.org' });
});

test('parseDotenv keeps "=" inside values and strips one pair of matching quotes', () => {
  const env = parseDotenv([
    'DATASOURCE_URL=jdbc:postgresql://h:5432/db?sslmode=require&a=b',
    'JWT_SECRET="quoted value"',
    "ADMIN_PASSWORD='single'",
    'MISMATCHED="half\'',
  ].join('\n'));
  assert.equal(env.DATASOURCE_URL, 'jdbc:postgresql://h:5432/db?sslmode=require&a=b');
  assert.equal(env.JWT_SECRET, 'quoted value');
  assert.equal(env.ADMIN_PASSWORD, 'single');
  assert.equal(env.MISMATCHED, '"half\'');
});

test('parseDotenv rejects a malformed line without echoing its content', () => {
  assert.throws(
    () => parseDotenv('JWT_SECRET=ok\nthis-is-a-leaked-password'),
    (error) => error.message.includes('line 2') && !error.message.includes('leaked-password'),
  );
});

test('validateEnv accepts a complete production env', () => {
  assert.deepEqual(validateEnv(PRODUCTION_ENV), []);
});

test('validateEnv reports every missing required key', () => {
  const problems = validateEnv({ DATASOURCE_URL: PRODUCTION_ENV.DATASOURCE_URL });
  for (const key of ['DATASOURCE_USERNAME', 'DATASOURCE_PASSWORD', 'JWT_SECRET', 'ADMIN_PASSWORD']) {
    assert.ok(problems.some((p) => p.startsWith(key)), `expected a problem for ${key}`);
  }
});

test('validateEnv rejects development defaults, a short JWT secret and a non-JDBC URL', () => {
  const devDefaults = validateEnv({
    ...PRODUCTION_ENV,
    JWT_SECRET: 'dev-secret-change-in-production-must-be-at-least-256-bits-long!!',
    ADMIN_PASSWORD: 'admin12345',
  });
  assert.ok(devDefaults.some((p) => p.startsWith('JWT_SECRET still has')));
  assert.ok(devDefaults.some((p) => p.startsWith('ADMIN_PASSWORD still has')));

  assert.ok(validateEnv({ ...PRODUCTION_ENV, JWT_SECRET: 'short' })
    .some((p) => p.includes('at least 32 characters')));
  assert.ok(validateEnv({ ...PRODUCTION_ENV, DATASOURCE_URL: 'postgres://u:p@h/db' })
    .some((p) => p.startsWith('DATASOURCE_URL must start with jdbc:postgresql://')));
});

test('buildDeployment wires image, env, port and health check to the named container', () => {
  const { containers, publicEndpoint } = buildDeployment({
    image: ':enerscope.api.7',
    containerName: 'api',
    env: PRODUCTION_ENV,
  });
  assert.deepEqual(containers, {
    api: { image: ':enerscope.api.7', environment: PRODUCTION_ENV, ports: { 8080: 'HTTP' } },
  });
  assert.equal(publicEndpoint.containerName, 'api');
  assert.equal(publicEndpoint.containerPort, 8080);
  assert.equal(publicEndpoint.healthCheck.path, '/api/v1/health');
  assert.equal(publicEndpoint.healthCheck.successCodes, '200-299');
});

test('CLI write produces the two deployment files from BACKEND_ENV', () => {
  const outDir = mkdtempSync(join(tmpdir(), 'lightsail-'));
  try {
    const run = spawnSync(process.execPath, [SCRIPT, 'write', outDir], {
      env: { ...process.env, BACKEND_ENV: toDotenv(PRODUCTION_ENV), IMAGE: ':svc.api.3', CONTAINER_NAME: 'api' },
      encoding: 'utf8',
    });
    assert.equal(run.status, 0, run.stderr);
    const containers = JSON.parse(readFileSync(join(outDir, 'containers.json'), 'utf8'));
    const endpoint = JSON.parse(readFileSync(join(outDir, 'public-endpoint.json'), 'utf8'));
    assert.equal(containers.api.image, ':svc.api.3');
    assert.equal(containers.api.environment.DATASOURCE_PASSWORD, 's3cret');
    assert.equal(endpoint.containerName, 'api');
  } finally {
    rmSync(outDir, { recursive: true, force: true });
  }
});

test('CLI validate fails without printing secret values', () => {
  const run = spawnSync(process.execPath, [SCRIPT, 'validate'], {
    env: { ...process.env, BACKEND_ENV: toDotenv({ ...PRODUCTION_ENV, JWT_SECRET: 'tiny-secret' }) },
    encoding: 'utf8',
  });
  assert.equal(run.status, 1);
  assert.match(run.stderr, /JWT_SECRET must be at least 32 characters/);
  assert.doesNotMatch(run.stderr + run.stdout, /tiny-secret|s3cret/);
});
