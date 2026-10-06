import { describe, expect, it } from 'vitest';

import { calendarWeeks } from './calendar-weeks';

describe('calendarWeeks', () => {
  it('parte dal lunedì e completa le settimane con celle vuote', () => {
    // Il 1° ottobre 2026 è un giovedì.
    const weeks = calendarWeeks('2026-10');
    expect(weeks).toHaveLength(5);
    expect(weeks[0]).toEqual([
      null,
      null,
      null,
      '2026-10-01',
      '2026-10-02',
      '2026-10-03',
      '2026-10-04',
    ]);
    expect(weeks[4]).toEqual([
      '2026-10-26',
      '2026-10-27',
      '2026-10-28',
      '2026-10-29',
      '2026-10-30',
      '2026-10-31',
      null,
    ]);
  });

  it('un febbraio che inizia di lunedì sta in quattro righe', () => {
    expect(calendarWeeks('2027-02')).toHaveLength(4);
  });
});
