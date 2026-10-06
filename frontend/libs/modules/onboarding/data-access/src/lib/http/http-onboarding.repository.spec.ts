import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CompleteOnboardingCommand, OnboardingFailed } from '@wallet/onboarding-domain';
import { API_BASE_URL } from '@wallet/shared-data-access';

import { HttpOnboardingRepository } from './http-onboarding.repository';

const ONBOARDING = '/api/onboarding';
const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl';

const PROFILE: CompleteOnboardingCommand['profile'] = {
  firstName: 'Marta',
  lastName: null,
  email: null,
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'current-month',
};

const RESPONSE = {
  user: {
    id: '0199ab7c-0000-7000-8000-000000000001',
    firstName: 'Marta',
    timeZone: 'Europe/Rome',
    language: 'it',
    defaultDashboardPeriod: 'current-month',
  },
  connections: [
    {
      source: 'budget-bakers',
      enabled: true,
      secretHint: '…tcmU',
      configuredAt: '2026-09-21',
    },
  ],
};

describe('HttpOnboardingRepository', () => {
  let repository: HttpOnboardingRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpOnboardingRepository,
      ],
    });
    repository = TestBed.inject(HttpOnboardingRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('manda profilo e sorgente in una sola richiesta', async () => {
    const completed = repository.complete({
      profile: PROFILE,
      importConnection: { source: 'budget-bakers', token: TOKEN },
    });

    const request = http.expectOne(ONBOARDING);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      profile: PROFILE,
      importConnection: { source: 'budget-bakers', token: TOKEN },
    });
    request.flush(RESPONSE, { status: 201, statusText: 'Created' });

    const result = await completed;
    expect(result.profile.firstName).toBe('Marta');
    expect(result.connections).toEqual([
      {
        source: 'budget-bakers',
        enabled: true,
        secretHint: '…tcmU',
        configuredAt: '2026-09-21',
        // Appena collegata: non è mai stata letta e non è mai girato un import.
        lastRecordDate: null,
        lastRunAt: null,
        credentialsRejectedAt: null,
      },
    ]);
  });

  it('omette la sorgente quando l’utente rimanda la scelta', async () => {
    const completed = repository.complete({ profile: PROFILE, importConnection: null });

    const request = http.expectOne(ONBOARDING);
    expect(request.request.body).toEqual({ profile: PROFILE });
    request.flush({ ...RESPONSE, connections: [] }, { status: 201, statusText: 'Created' });

    await expect(completed).resolves.toMatchObject({ connections: [] });
  });

  it('riconosce l’email già registrata', async () => {
    const completed = repository.complete({ profile: PROFILE, importConnection: null });
    http.expectOne(ONBOARDING).flush(null, { status: 409, statusText: 'Conflict' });

    await expect(completed).rejects.toMatchObject({ kind: 'conflict' });
  });

  it('distingue una richiesta rifiutata da un servizio non raggiungibile', async () => {
    const rejected = repository.complete({ profile: PROFILE, importConnection: null });
    http.expectOne(ONBOARDING).flush(null, { status: 400, statusText: 'Bad Request' });
    await expect(rejected).rejects.toMatchObject({ kind: 'invalid' });

    const unreachable = repository.complete({ profile: PROFILE, importConnection: null });
    http.expectOne(ONBOARDING).error(new ProgressEvent('error'));
    await expect(unreachable).rejects.toMatchObject({ kind: 'unavailable' });
  });

  it('presenta ogni fallimento come errore di dominio', async () => {
    const completed = repository.complete({ profile: PROFILE, importConnection: null });
    http.expectOne(ONBOARDING).flush(null, { status: 500, statusText: 'Server Error' });

    await expect(completed).rejects.toBeInstanceOf(OnboardingFailed);
  });
});
