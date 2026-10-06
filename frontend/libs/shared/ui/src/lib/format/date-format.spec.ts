import { describe, expect, it } from 'vitest';

import { formatMonth } from './date-format';

describe('formatMonth', () => {
  it('scrive il mese per esteso', () => {
    expect(formatMonth('2026-10')).toBe('ottobre 2026');
  });
});
