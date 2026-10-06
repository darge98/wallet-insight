import { describe, expect, it } from 'vitest';

import { asUserId, displayName, UserProfile } from './user-profile';

const BASE: UserProfile = {
  id: asUserId('user-1'),
  firstName: 'Marta',
  lastName: 'Rossi',
  email: null,
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'current-month',
};

describe('displayName', () => {
  it('unisce nome e cognome', () => {
    expect(displayName(BASE)).toBe('Marta Rossi');
  });

  it('mostra il solo nome quando il cognome manca', () => {
    expect(displayName({ ...BASE, lastName: null })).toBe('Marta');
  });
});
