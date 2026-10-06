import { describe, expect, it } from 'vitest';

import { lastRunLabel } from './last-run-label';

const ROMA = 'Europe/Rome';
const ADESSO = new Date('2026-10-05T12:00:00Z');

describe('lastRunLabel', () => {
  it("dice l'ora dell'import, nel fuso del profilo", () => {
    expect(lastRunLabel('2026-10-05T03:00:05Z', ROMA, ADESSO)).toBe(
      'Dati aggiornati oggi alle 05:00',
    );
  });

  it('dice «ieri» quando il giro di oggi non è passato', () => {
    expect(lastRunLabel('2026-10-04T03:00:05Z', ROMA, ADESSO)).toBe(
      'Dati aggiornati ieri alle 05:00',
    );
  });

  it('dà la data quando è più vecchio', () => {
    expect(lastRunLabel('2026-10-01T03:00:05Z', ROMA, ADESSO)).toBe(
      'Dati aggiornati il 1 ottobre alle 05:00',
    );
  });

  it('decide il giorno nel fuso del profilo, non in UTC', () => {
    // Le 23:30 UTC del 4 sono l'1:30 del 5 a Roma.
    expect(lastRunLabel('2026-10-04T23:30:00Z', ROMA, ADESSO)).toBe(
      'Dati aggiornati oggi alle 01:30',
    );
  });

  it('senza un import riuscito lo dice', () => {
    expect(lastRunLabel(null, ROMA, ADESSO)).toBe('Dati non ancora importati');
  });
});
