import { describe, expect, it } from 'vitest';
import { FILE_CATEGORIES } from './file.types';

describe('FILE_CATEGORIES', () => {
  it('matches the backend FileCategory contract', () => {
    expect(FILE_CATEGORIES).toEqual(['IMPORT_SOURCE', 'REPORT', 'EXPORT', 'ASSET', 'TEMP']);
  });
});
