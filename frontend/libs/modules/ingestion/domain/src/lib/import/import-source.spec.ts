import { describe, expect, it } from 'vitest';

import { IMPORT_SOURCE_CATALOG, importSourceDescriptor, isImportSource } from './import-source';

describe('catalogo delle sorgenti', () => {
  it('descrive ogni sorgente una volta sola', () => {
    const sources = IMPORT_SOURCE_CATALOG.map((entry) => entry.source);
    expect(new Set(sources).size).toBe(sources.length);
  });

  it('espone BudgetBakers come sorgente a token personale', () => {
    const descriptor = importSourceDescriptor('budget-bakers');

    expect(descriptor.available).toBe(true);
    expect(descriptor.credential).toBe('personal-token');
  });

  it('tiene PSD2 nel catalogo ma non ancora disponibile', () => {
    expect(importSourceDescriptor('psd2').available).toBe(false);
  });

  it('riconosce solo le sorgenti note', () => {
    expect(isImportSource('budget-bakers')).toBe(true);
    expect(isImportSource('revolut')).toBe(false);
  });
});
