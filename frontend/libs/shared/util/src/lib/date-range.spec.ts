import { describe, expect, it } from 'vitest';

import {
  isWithinRange,
  toDateRange,
  monthsInRange,
  resolvePeriod,
  startOfWeek,
  todayInTimeZone,
} from './date-range';

/** 13 settembre 2026, domenica. */
const TODAY = '2026-09-13';

describe('resolvePeriod', () => {
  it('risolve oggi come singolo giorno', () => {
    expect(resolvePeriod('today', TODAY)).toEqual({ from: '2026-09-13', to: '2026-09-13' });
  });

  it('risolve la settimana corrente da lunedì a oggi', () => {
    expect(resolvePeriod('current-week', TODAY)).toEqual({
      from: '2026-09-07',
      to: '2026-09-13',
    });
  });

  it('gestisce una settimana a cavallo dell’anno', () => {
    expect(resolvePeriod('current-week', '2026-01-01')).toEqual({
      from: '2025-12-29',
      to: '2026-01-01',
    });
  });

  it('risolve il mese corrente da inizio mese a oggi', () => {
    expect(resolvePeriod('current-month', TODAY)).toEqual({
      from: '2026-09-01',
      to: '2026-09-13',
    });
  });

  it('risolve il mese precedente da estremo a estremo', () => {
    expect(resolvePeriod('previous-month', TODAY)).toEqual({
      from: '2026-08-01',
      to: '2026-08-31',
    });
  });

  it('risolve gli ultimi 7 giorni, oggi incluso', () => {
    expect(resolvePeriod('last-7-days', TODAY)).toEqual({
      from: '2026-09-07',
      to: '2026-09-13',
    });
  });

  it('risolve gli ultimi 30 giorni, oggi incluso', () => {
    expect(resolvePeriod('last-30-days', TODAY)).toEqual({
      from: '2026-08-15',
      to: '2026-09-13',
    });
  });

  it('copre tre mesi interi, fino a oggi', () => {
    expect(resolvePeriod('last-3-months', TODAY)).toEqual({
      from: '2026-07-01',
      to: '2026-09-13',
    });
  });

  it('copre sei mesi interi, fino a oggi', () => {
    expect(resolvePeriod('last-6-months', TODAY)).toEqual({
      from: '2026-04-01',
      to: '2026-09-13',
    });
  });

  it('a fine mese non perde il mese più lontano', () => {
    expect(resolvePeriod('last-3-months', '2026-04-30').from).toBe('2026-02-01');
    expect(resolvePeriod('last-6-months', '2026-07-31').from).toBe('2026-02-01');
  });

  it("risolve l'anno solare fino a oggi", () => {
    expect(resolvePeriod('current-year', TODAY)).toEqual({
      from: '2026-01-01',
      to: '2026-09-13',
    });
  });

  it('gestisce il 29 febbraio in un anno bisestile', () => {
    expect(resolvePeriod('current-month', '2024-02-29')).toEqual({
      from: '2024-02-01',
      to: '2024-02-29',
    });
  });
});

describe('startOfWeek', () => {
  it('individua il lunedì della settimana', () => {
    // Domenica 13 settembre 2026 → lunedì 7 settembre.
    expect(startOfWeek(new Date(2026, 8, 13)).getDate()).toBe(7);
  });
});

describe('monthsInRange', () => {
  it('elenca i mesi coperti in ordine crescente', () => {
    expect(monthsInRange({ from: '2026-07-15', to: '2026-09-02' })).toEqual([
      '2026-07',
      '2026-08',
      '2026-09',
    ]);
  });
});

describe('isWithinRange', () => {
  it('include gli estremi', () => {
    const range = { from: '2026-09-01', to: '2026-09-30' };

    expect(isWithinRange('2026-09-01', range)).toBe(true);
    expect(isWithinRange('2026-09-30', range)).toBe(true);
    expect(isWithinRange('2026-10-01', range)).toBe(false);
  });
});

describe('todayInTimeZone', () => {
  it('produce una data ISO valida', () => {
    expect(todayInTimeZone('Europe/Rome')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('ripiega sulla data locale per una timezone non valida', () => {
    expect(todayInTimeZone('Non/Valida')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('toDateRange', () => {
  it('accetta un intervallo con entrambi i capi', () => {
    expect(toDateRange('2026-09-01', '2026-09-30')).toEqual({
      from: '2026-09-01',
      to: '2026-09-30',
    });
  });

  it('accetta un intervallo di un giorno solo', () => {
    expect(toDateRange('2026-09-19', '2026-09-19')).toEqual({
      from: '2026-09-19',
      to: '2026-09-19',
    });
  });

  it('rifiuta una finestra con un capo solo', () => {
    // Mezza finestra il server la rifiuta con un 400: meglio non chiederla che
    // mostrare un periodo diverso da quello che l'utente sta scrivendo.
    expect(toDateRange('2026-09-01', '')).toBeNull();
    expect(toDateRange('', '2026-09-30')).toBeNull();
  });

  it('rifiuta una finestra rovesciata', () => {
    expect(toDateRange('2026-09-30', '2026-09-01')).toBeNull();
  });
});
