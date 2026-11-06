#!/usr/bin/env node
// Builds the AWS Lightsail container-service deployment for the backend
// (container definition + public endpoint) from the BACKEND_ENV secret, so the
// deploy workflow stays a thin wrapper around the AWS CLI.
//
// Usage (BACKEND_ENV holds the production .env, one KEY=VALUE per line):
//   node lightsail-deployment.mjs validate
//   IMAGE=":enerscope.api.7" CONTAINER_NAME=api node lightsail-deployment.mjs write <outDir>
//
// Tests: node --test .github/scripts/*.test.mjs

import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const CONTAINER_PORT = 8080;
export const HEALTH_CHECK_PATH = '/api/v1/health';

export const REQUIRED_KEYS = [
  'DATASOURCE_URL',
  'DATASOURCE_USERNAME',
  'DATASOURCE_PASSWORD',
  'JWT_SECRET',
  'ADMIN_PASSWORD',
];

// Development defaults from application.properties / .env.example. Deploying
// with any of them would leave production open, so the deploy refuses.
const DEV_DEFAULTS = {
  JWT_SECRET: 'dev-secret-change-in-production-must-be-at-least-256-bits-long!!',
  ADMIN_PASSWORD: 'admin12345',
};

const MIN_JWT_SECRET_LENGTH = 32; // 256 bits

/**
 * Parses dotenv text into a plain object. Blank lines and `#` comments are
 * skipped, an optional `export ` prefix is allowed and one pair of matching
 * surrounding quotes is removed. Only the first `=` splits key from value, so
 * JDBC URLs with query strings survive. Inline comments are not supported.
 */
export function parseDotenv(text) {
  const env = {};
  const lines = String(text ?? '').split(/\r?\n/);
  lines.forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) return;
    const match = /^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/.exec(line);
    if (!match) {
      // Never echo the line itself: it may hold a secret.
      throw new Error(`BACKEND_ENV line ${index + 1} is not in KEY=VALUE form`);
    }
    let value = match[2];
    const quote = value[0];
    if (value.length >= 2 && (quote === '"' || quote === "'") && value.at(-1) === quote) {
      value = value.slice(1, -1);
    }
    env[match[1]] = value;
  });
  return env;
}

/** Returns a list of human-readable problems; empty means the env is deployable. */
export function validateEnv(env) {
  const problems = [];
  for (const key of REQUIRED_KEYS) {
    if (!env[key]) problems.push(`${key} is missing or empty`);
  }
  if (env.DATASOURCE_URL && !env.DATASOURCE_URL.startsWith('jdbc:postgresql://')) {
    problems.push('DATASOURCE_URL must start with jdbc:postgresql:// (a postgres:// URL will not work)');
  }
  if (env.JWT_SECRET && env.JWT_SECRET.length < MIN_JWT_SECRET_LENGTH) {
    problems.push(`JWT_SECRET must be at least ${MIN_JWT_SECRET_LENGTH} characters (256 bits)`);
  }
  for (const [key, devValue] of Object.entries(DEV_DEFAULTS)) {
    if (env[key] === devValue) problems.push(`${key} still has the development default`);
  }
  return problems;
}

/** The two JSON documents `aws lightsail create-container-service-deployment` takes. */
export function buildDeployment({ image, containerName, env }) {
  return {
    containers: {
      [containerName]: {
        image,
        environment: env,
        ports: { [String(CONTAINER_PORT)]: 'HTTP' },
      },
    },
    publicEndpoint: {
      containerName,
      containerPort: CONTAINER_PORT,
      // Generous thresholds: Spring Boot plus Flyway can take a while to boot
      // on the smaller Lightsail powers. Traffic only moves once it is healthy.
      healthCheck: {
        path: HEALTH_CHECK_PATH,
        successCodes: '200-299',
        intervalSeconds: 30,
        timeoutSeconds: 10,
        healthyThreshold: 2,
        unhealthyThreshold: 10,
      },
    },
  };
}

function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

function main([command, outDir]) {
  let env;
  try {
    env = parseDotenv(process.env.BACKEND_ENV);
  } catch (error) {
    fail(error.message);
  }
  const problems = validateEnv(env);
  if (problems.length > 0) {
    problems.forEach((problem) => console.error(`::error::BACKEND_ENV: ${problem}`));
    process.exit(1);
  }

  if (command === 'validate') {
    console.log(`BACKEND_ENV OK — variables: ${Object.keys(env).join(', ')}`);
    return;
  }

  if (command === 'write') {
    const image = process.env.IMAGE;
    const containerName = process.env.CONTAINER_NAME || 'api';
    if (!image) fail('IMAGE is not set');
    if (!outDir) fail('usage: lightsail-deployment.mjs write <outDir>');
    const { containers, publicEndpoint } = buildDeployment({ image, containerName, env });
    const dir = resolve(outDir);
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, 'containers.json'), JSON.stringify(containers, null, 2));
    writeFileSync(join(dir, 'public-endpoint.json'), JSON.stringify(publicEndpoint, null, 2));
    console.log(`Deployment for ${containerName} (${image}) written to ${dir}`);
    return;
  }

  fail('usage: lightsail-deployment.mjs <validate|write> [outDir]');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main(process.argv.slice(2));
}
