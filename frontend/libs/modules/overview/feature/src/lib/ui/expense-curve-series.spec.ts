import { describe, expect, it } from 'vitest';

import { money } from '@wallet/shared-domain';

import { dayStart, expenseCurveSeries } from './expense-curve-series';

const OTTOBRE = { from: '2026-10-01', to: '2026-10-31' };

describe('expenseCurveSeries', () => {
  it('mette ogni giorno al suo posto nel calendario, i giorni senza spese compresi', () => {
    const serie = expenseCurveSeries(
      [
        { date: '2026-10-02', total: money(5_000) },
        { date: '2026-10-25', total: money(45_000) },
      ],
      OTTOBRE,
    );

    expect(serie?.data).toEqual([
      [dayStart('2026-10-01'), 0],
      [dayStart('2026-10-02'), 50],
      [dayStart('2026-10-25'), 450],
      [dayStart('2026-10-31'), 450],
    ]);
    expect(serie?.last).toEqual([dayStart('2026-10-25'), 450]);
  });

  it('non aggiunge niente quando la spesa copre già i due estremi', () => {
    const serie = expenseCurveSeries(
      [
        { date: '2026-10-01', total: money(100) },
        { date: '2026-10-31', total: money(200) },
      ],
      OTTOBRE,
    );

    expect(serie?.data).toHaveLength(2);
  });

  it('senza spese non c’è curva', () => {
    expect(expenseCurveSeries([], OTTOBRE)).toBeNull();
  });
});
