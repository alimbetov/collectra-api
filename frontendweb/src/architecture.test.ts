import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { describe, expect, it } from 'vitest';

const sourceRoot = resolve(import.meta.dirname);
const layerRank = new Map([
  ['shared', 0],
  ['entities', 1],
  ['features', 2],
  ['pages', 3],
  ['app', 4],
]);

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const absolute = resolve(directory, entry);
    if (statSync(absolute).isDirectory()) return sourceFiles(absolute);
    return /\.(ts|tsx)$/.test(entry) && !entry.endsWith('.test.ts') && !entry.endsWith('.test.tsx')
      ? [absolute]
      : [];
  });
}

function parts(file: string): string[] {
  return relative(sourceRoot, file).split(sep);
}

describe('frontend dependency boundaries', () => {
  it('keeps dependencies flowing from app to shared and prevents cross-feature imports', () => {
    const violations: string[] = [];
    for (const file of sourceFiles(sourceRoot)) {
      const [sourceLayer, sourceSlice] = parts(file);
      const sourceRank = layerRank.get(sourceLayer);
      if (sourceRank === undefined) continue;

      const imports = readFileSync(file, 'utf8').matchAll(/from\s+['"](\.[^'"]+)['"]/g);
      for (const match of imports) {
        const target = resolve(dirname(file), match[1]);
        const [targetLayer, targetSlice] = parts(target);
        const targetRank = layerRank.get(targetLayer);
        if (targetRank === undefined) continue;

        const label = `${relative(sourceRoot, file)} -> ${match[1]}`;
        if (targetRank > sourceRank) violations.push(`${label} reverses the layer direction`);
        if (sourceLayer === 'pages' && targetLayer === 'pages') {
          violations.push(`${label} imports another page`);
        }
        if (
          sourceLayer === 'features' &&
          targetLayer === 'features' &&
          sourceSlice !== targetSlice
        ) {
          violations.push(`${label} crosses feature slices`);
        }
      }
    }

    expect(violations).toEqual([]);
  });
});
