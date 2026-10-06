import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { currentUserId } from './current-user';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const PAGINA = { items: [{ id: UTENTE }], total: 1, index: 0, size: 1, pageCount: 1 };
const PAGINA_VUOTA = { items: [], total: 0, index: 0, size: 1, pageCount: 0 };

describe('currentUserId', () => {
  let http: HttpClient;
  let server: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    server = TestBed.inject(HttpTestingController);
  });

  afterEach(() => server.verify());

  it("chiede l'utente una volta sola, anche a più chiamate insieme", async () => {
    const prima = currentUserId(http, '/api');
    const seconda = currentUserId(http, '/api');
    server.expectOne(UTENTI).flush(PAGINA);

    expect(await prima).toBe(UTENTE);
    expect(await seconda).toBe(UTENTE);
    expect(await currentUserId(http, '/api')).toBe(UTENTE);
  });

  it("un errore non si ricorda: la volta dopo l'utente si richiede", async () => {
    const senzaUtente = currentUserId(http, '/api');
    server.expectOne(UTENTI).flush(PAGINA_VUOTA);
    await expect(senzaUtente).rejects.toThrow('Nessun utente configurato.');

    const dopo = currentUserId(http, '/api');
    server.expectOne(UTENTI).flush(PAGINA);
    expect(await dopo).toBe(UTENTE);
  });
});
