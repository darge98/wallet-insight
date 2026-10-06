import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { asUserId } from '@wallet/user-domain';
import { API_BASE_URL } from '@wallet/shared-data-access';

import { HttpUserProfileRepository } from './http-user-profile.repository';

const USERS = '/api/users?page=0&size=1';

const RAW_USER = {
  id: '0199ab7c-0000-7000-8000-000000000001',
  firstName: 'Marta',
  lastName: 'Rossi',
  email: 'marta@example.com',
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'last-7-days',
};

function emptyPage(items: readonly unknown[] = []) {
  return { items, total: items.length, index: 0, size: 1, pageCount: items.length };
}

describe('HttpUserProfileRepository', () => {
  let repository: HttpUserProfileRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpUserProfileRepository,
      ],
    });
    repository = TestBed.inject(HttpUserProfileRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('non trova un profilo quando la lista è vuota', async () => {
    const found = repository.findCurrent();
    http.expectOne(USERS).flush(emptyPage());

    await expect(found).resolves.toBeNull();
  });

  it('traduce il primo utente nel profilo di dominio', async () => {
    const found = repository.findCurrent();
    http.expectOne(USERS).flush(emptyPage([RAW_USER]));

    await expect(found).resolves.toEqual({
      id: RAW_USER.id,
      firstName: 'Marta',
      lastName: 'Rossi',
      email: 'marta@example.com',
      timeZone: 'Europe/Rome',
      language: 'it',
      defaultDashboardPeriod: 'last-7-days',
    });
  });

  it('riporta a null i campi che il server omette', async () => {
    const found = repository.findCurrent();
    http
      .expectOne(USERS)
      .flush(emptyPage([{ ...RAW_USER, lastName: undefined, email: undefined }]));

    const profile = await found;
    expect(profile?.lastName).toBeNull();
    expect(profile?.email).toBeNull();
  });

  it('rifiuta un valore fuori contratto invece di ripiegare in silenzio', async () => {
    const found = repository.findCurrent();
    http.expectOne(USERS).flush(emptyPage([{ ...RAW_USER, language: 'de' }]));

    await expect(found).rejects.toThrow(/language/);
  });

  it('annulla la richiesta quando il segnale viene abortito', async () => {
    const controller = new AbortController();
    const found = repository.findCurrent(controller.signal);
    const request = http.expectOne(USERS);

    controller.abort(new Error('annullata'));

    await expect(found).rejects.toThrow('annullata');
    expect(request.cancelled).toBe(true);
  });

  it('salva il profilo per intero con una sola richiesta', async () => {
    const salvato = repository.save({
      id: asUserId(RAW_USER.id),
      firstName: 'Marta',
      lastName: null,
      email: null,
      timeZone: 'Europe/Rome',
      language: 'it',
      defaultDashboardPeriod: 'current-month',
    });

    const request = http.expectOne(`/api/users/${RAW_USER.id}`);
    expect(request.request.method).toBe('PUT');
    // Sostituzione completa: i campi vuoti viaggiano come `null`, non spariscono.
    // Ometterli farebbe credere al server che non vadano cambiati.
    expect(request.request.body).toEqual({
      firstName: 'Marta',
      lastName: null,
      email: null,
      timeZone: 'Europe/Rome',
      language: 'it',
      defaultDashboardPeriod: 'current-month',
    });
    request.flush({ ...RAW_USER, defaultDashboardPeriod: 'current-month' });

    await expect(salvato).resolves.toMatchObject({ firstName: 'Marta' });
  });

  it('restituisce il profilo come lo rimanda il server', async () => {
    const salvato = repository.save({
      id: asUserId(RAW_USER.id),
      firstName: 'Marta',
      lastName: 'Rossi',
      email: 'marta@example.com',
      timeZone: 'Europe/Rome',
      language: 'it',
      defaultDashboardPeriod: 'current-month',
    });

    // Il server ha normalizzato il periodo: in schermata deve comparire il suo
    // valore, non quello mandato, altrimenti si darebbe per salvato altro.
    http.expectOne(`/api/users/${RAW_USER.id}`).flush(RAW_USER);

    await expect(salvato).resolves.toMatchObject({ defaultDashboardPeriod: 'last-7-days' });
  });
});
