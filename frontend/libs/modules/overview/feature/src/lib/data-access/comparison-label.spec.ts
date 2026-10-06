import { describe, expect, it } from 'vitest';

import { comparisonLabel } from './comparison-label';

describe('comparisonLabel', () => {
  it('chiama per nome un mese intero', () => {
    expect(comparisonLabel({ from: '2026-08-01', to: '2026-08-31' })).toBe('ad agosto');
    expect(comparisonLabel({ from: '2026-02-01', to: '2026-02-28' })).toBe('a febbraio');
  });

  it("chiama «stesso periodo» un tratto di mese dall'inizio", () => {
    expect(comparisonLabel({ from: '2026-09-01', to: '2026-09-04' })).toBe(
      'allo stesso periodo di settembre',
    );
  });

  it('resta generico su ogni altro periodo', () => {
    expect(comparisonLabel({ from: '2026-09-25', to: '2026-10-01' })).toBe('al periodo precedente');
    expect(comparisonLabel({ from: '2026-09-27', to: '2026-09-30' })).toBe('al periodo precedente');
  });
});
