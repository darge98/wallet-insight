import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { Account, ACCOUNT_REPOSITORY, AccountRepository } from '@wallet/accounts-domain';
import { IMPORT_CONNECTION_REPOSITORY, ImportConnection } from '@wallet/ingestion-domain';
import { ANALYTICS_REPOSITORY, asAccountId, money } from '@wallet/shared-domain';
import { UserFacade } from '@wallet/user-data-access';
import { asUserId, USER_PROFILE_REPOSITORY, UserProfile } from '@wallet/user-domain';

import { settingsRoutes } from './settings.routes';

const PROFILO: UserProfile = {
  id: asUserId('0199ab7c-0000-7000-8000-000000000001'),
  firstName: 'Marta',
  lastName: null,
  email: null,
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'current-month',
};

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

const CONNESSIONE: ImportConnection = {
  source: 'budget-bakers',
  enabled: true,
  secretHint: '…a1b2',
  configuredAt: '2026-09-22',
  lastRecordDate: '2026-09-19',
  lastRunAt: '2026-09-22T20:31:26.000Z',
  credentialsRejectedAt: null,
};

/** Quante volte i conti sono stati letti: serve a un test, azzerato prima di ognuno. */
let letture = 0;

const accountRepository: AccountRepository = {
  findAll: async () => {
    letture += 1;
    return [CONTO];
  },
  rename: async () => CONTO,
  recolor: async () => CONTO,
};

/**
 * La sezione montata davvero, con il suo router.
 *
 * Non basta mostrare che i componenti si disegnano: qui si verifica ciò che solo
 * il routing può rompere — che aprire «Impostazioni» porti da qualche parte invece
 * che su una pagina vuota, e che i link del menu laterale, che sono relativi,
 * puntino dove devono anche se un giorno la sezione venisse montata altrove.
 */
describe('Sezione Impostazioni', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    letture = 0;
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: 'impostazioni', children: settingsRoutes }]),
        { provide: ACCOUNT_REPOSITORY, useValue: accountRepository },
        { provide: ANALYTICS_REPOSITORY, useValue: { netWorth: async () => money(0) } },
        {
          provide: IMPORT_CONNECTION_REPOSITORY,
          useValue: { findAll: async () => [CONNESSIONE] },
        },
        {
          provide: USER_PROFILE_REPOSITORY,
          useValue: { findCurrent: async () => PROFILO, save: async () => PROFILO },
        },
      ],
    });

    // Nell'applicazione il profilo lo carica la guardia della shell prima di
    // entrare nella rotta: senza, questa schermata non esisterebbe.
    await TestBed.inject(UserFacade).ensureLoaded();
    harness = await RouterTestingHarness.create();
  });

  const testo = () => harness.routeNativeElement?.textContent ?? '';

  /**
   * Naviga e aspetta che i dati arrivino.
   *
   * `navigateByUrl` torna appena la rotta è attiva: i `resource` della pagina
   * partono in quel momento e si risolvono dopo. Senza l'attesa il test
   * guarderebbe la schermata mezzo secondo troppo presto, quando ci sono ancora
   * gli scheletri di caricamento.
   */
  const vaiA = async (url: string) => {
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
  };

  it('aprendo la sezione si arriva sul profilo', async () => {
    await vaiA('/impostazioni');

    expect(TestBed.inject(Router).url).toBe('/impostazioni/profilo');
    const valori = [...(harness.routeNativeElement?.querySelectorAll('input') ?? [])].map(
      (campo) => campo.value,
    );
    expect(valori).toContain('Marta');
  });

  it('il menu laterale porta alle pagine senza sapere dove è montata la sezione', async () => {
    await vaiA('/impostazioni/profilo');

    const href = [...(harness.routeNativeElement?.querySelectorAll('nav a') ?? [])].map((link) =>
      link.getAttribute('href'),
    );
    expect(href).toEqual([
      '/impostazioni/profilo',
      '/impostazioni/conti',
      '/impostazioni/categorie',
    ]);
  });

  it('il piano è annunciato ma non si apre', async () => {
    await vaiA('/impostazioni/profilo');

    // Nessun link: un `<a>` che non porta da nessuna parte resterebbe raggiungibile
    // da tastiera e annunciato come collegamento.
    expect(testo()).toContain('Piano');
    expect(testo()).toContain('Presto');
    expect(harness.routeNativeElement?.querySelector('a[href*="piano"]')).toBeNull();
  });

  it('la pagina dei conti mostra i conti e le sorgenti', async () => {
    await vaiA('/impostazioni/conti');

    expect(testo()).toContain('Wallet by BudgetBakers');
    // Le due date della sorgente, che qui sono tutto ciò che si può sapere:
    // aggiornare non è più un gesto di questa schermata.
    expect(testo()).toContain('Controllato');
    // Il saldo viene dal backend: iniziale più la somma dei movimenti.
    expect(testo()).toContain('13.352,07');
  });

  it('cambiare scheda non ricarica i conti', async () => {
    await vaiA('/impostazioni/conti');
    await vaiA('/impostazioni/profilo');
    await vaiA('/impostazioni/conti');

    // Il facade vive nel guscio, non nelle pagine: passare da una scheda all'altra
    // non rifà le chiamate.
    expect(letture).toBe(1);
  });
});
