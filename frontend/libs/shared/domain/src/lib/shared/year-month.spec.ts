import { describe, expect, it } from 'vitest';

import { daysLeftInMonth, monthOf, shiftMonth } from './year-month';

describe('YearMonth', () => {
  it('ricava il mese da una data', () => {
    expect(monthOf('2026-10-04')).toBe('2026-10');
  });

  it('scavalca il cambio di anno in entrambe le direzioni', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
    expect(shiftMonth('2026-10', -13)).toBe('2025-09');
  });

  it('conta i giorni che restano, oggi compreso', () => {
    expect(daysLeftInMonth('2026-10-04')).toBe(28);
    expect(daysLeftInMonth('2026-10-31')).toBe(1);
    expect(daysLeftInMonth('2028-02-01')).toBe(29);
  });
});
