import { ApplicationRef, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { AccountsOverview } from '@wallet/accounts-data-access';
import { Account, ACCOUNT_REPOSITORY, AccountRepository } from '@wallet/accounts-domain';
import { IMPORT_CONNECTION_REPOSITORY } from '@wallet/ingestion-domain';
import { ANALYTICS_REPOSITORY, asAccountId, money } from '@wallet/shared-domain';
import { USER_PROFILE_REPOSITORY } from '@wallet/user-domain';

import { SettingsFacade } from './settings-facade';

const CREDEM: Account = {
  id: asAccountId('0199ab7c-0000-7000-8000-0000000000aa'),
  source: 'budget-bakers',
  name: 'Credem',
  sourceName: 'Credem',
  kind: 'current-account',
  initialBalance: { amount: 892_001, currency: 'EUR' },
  balance: { amount: 1_335_207, currency: 'EUR' },
  color: null,
  numberLast4: '2861',
  archived: false,
  excludedFromStats: false,
};

const VERDE = '#1f9d55';

const accountRepository: AccountRepository = {
  findAll: async () => [CREDEM],
  rename: async (id, name) => ({ ...CREDEM, id, name }),
  // Il server risponde col conto nuovo: è quello che deve finire in elenco.
  recolor: async (id, color) => ({ ...CREDEM, id, color }),
};

/**
 * Le Impostazioni scrivono su una lista che non è loro.
 *
 * È il test di una cosa che si era rotta davvero: finché questo facade teneva una
 * copia privata dei conti, ricolorare Credem lo aggiornava nella schermata delle
 * Impostazioni e lasciava i movimenti e la sidebar col colore di prima, fino al
 * ricaricamento della pagina. Due liste degli stessi conti, una sola aggiornata.
 */
describe('SettingsFacade', () => {
  let facade: SettingsFacade;
  let overview: AccountsOverview;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        SettingsFacade,
        { provide: ACCOUNT_REPOSITORY, useValue: accountRepository },
        { provide: ANALYTICS_REPOSITORY, useValue: { netWorth: async () => money(0) } },
        { provide: IMPORT_CONNECTION_REPOSITORY, useValue: { findAll: async () => [] } },
        {
          provide: USER_PROFILE_REPOSITORY,
          useValue: { findCurrent: async () => null, save: async () => null },
        },
      ],
    });

    facade = TestBed.inject(SettingsFacade);
    overview = TestBed.inject(AccountsOverview);
    // I conti devono essere arrivati: si ricolora ciò che è già in elenco.
    await TestBed.inject(ApplicationRef).whenStable();
  });

  it('il colore nuovo arriva a chi legge i conti condivisi', async () => {
    expect(overview.visible()[0]?.color).toBeNull();

    await facade.recolor(CREDEM.id, VERDE);

    // Non basta che lo veda questa schermata: `AccountsOverview` è la lista che
    // leggono anche i movimenti e la sidebar.
    expect(overview.visible()[0]?.color).toBe(VERDE);
    expect(facade.accounts.value()[0]?.color).toBe(VERDE);
  });

  it('il nome nuovo segue la stessa strada', async () => {
    await facade.rename(CREDEM.id, 'Conto stipendio');

    expect(overview.visible()[0]?.name).toBe('Conto stipendio');
  });

  it('il totale è quello di AccountsOverview, non una seconda somma', () => {
    expect(facade.totalBalance()).toEqual(overview.total());
  });
});
