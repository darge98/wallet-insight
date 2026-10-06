import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { Account, ACCOUNT_REPOSITORY, AccountRepository } from '@wallet/accounts-domain';
import { ANALYTICS_REPOSITORY, asAccountId, money } from '@wallet/shared-domain';
import { USER_PROFILE_REPOSITORY } from '@wallet/user-domain';

import { Sidebar } from './sidebar';

const CONTO: Account = {
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

const accountRepository: AccountRepository = {
  findAll: async () => [CONTO],
  rename: async () => CONTO,
  recolor: async () => CONTO,
};

/**
 * L'elenco dei conti si apre e si chiude.
 *
 * Non è una preferenza estetica da lasciare non verificata: chiusa la sezione,
 * il saldo complessivo deve restare a schermo, altrimenti chiudere vuol dire
 * perdere la risposta a «quanto ho» invece che guadagnare spazio.
 */
describe('Sidebar', () => {
  let fixture: ComponentFixture<Sidebar>;

  const intestazione = (): HTMLButtonElement =>
    fixture.nativeElement.querySelector('#sidebar-accounts-label button') as HTMLButtonElement;

  const linkDeiConti = (): NodeListOf<HTMLAnchorElement> =>
    fixture.nativeElement.querySelectorAll('#sidebar-accounts a');

  const conti = (): number => linkDeiConti().length;

  const testo = (): string => fixture.nativeElement.textContent ?? '';

  const premi = async () => {
    intestazione().click();
    await fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ACCOUNT_REPOSITORY, useValue: accountRepository },
        { provide: ANALYTICS_REPOSITORY, useValue: { netWorth: async () => money(1_335_207) } },
        { provide: USER_PROFILE_REPOSITORY, useValue: { findCurrent: async () => null } },
      ],
    });

    fixture = TestBed.createComponent(Sidebar);
    await fixture.whenStable();
  });

  it('parte aperta, con i conti in elenco', () => {
    expect(intestazione().getAttribute('aria-expanded')).toBe('true');
    expect(conti()).toBe(1);
  });

  it('chiudendola restano il titolo e il totale', async () => {
    await premi();

    expect(intestazione().getAttribute('aria-expanded')).toBe('false');
    expect(conti()).toBe(0);
    expect(testo()).toContain('I tuoi conti');
    expect(testo()).toContain('13.352,07');
  });

  it("si riapre com'era", async () => {
    await premi();
    await premi();

    expect(intestazione().getAttribute('aria-expanded')).toBe('true');
    expect(conti()).toBe(1);
  });

  it('ogni conto apre i propri movimenti', () => {
    const [link] = linkDeiConti();

    expect(link?.getAttribute('href')).toBe(`/movimenti?conto=${CONTO.id}`);
    expect(link?.getAttribute('aria-label')).toBe('Movimenti di Credem');
  });
});
