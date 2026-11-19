import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import ts from 'typescript';

const nodeRequire = createRequire(import.meta.url);

export function createLoader(stubFor = () => undefined) {
  const cache = new Map();
  function load(path) {
    if (cache.has(path)) return cache.get(path).exports;
    const module = { exports: {} };
    cache.set(path, module);
    const code = ts.transpileModule(readFileSync(path, 'utf8'), {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        jsx: ts.JsxEmit.ReactJSX,
        target: ts.ScriptTarget.ES2022,
      },
    }).outputText;
    const localRequire = (name) => {
      const stub = stubFor(name);
      if (stub !== undefined) return stub;
      if (!name.startsWith('.')) return nodeRequire(name);
      const base = resolve(dirname(path), name);
      return load([base + '.ts', base + '.tsx', base + '/index.tsx'].find(existsSync));
    };
    new Function('require', 'module', 'exports', code)(localRequire, module, module.exports);
    return module.exports;
  }
  return load;
}
