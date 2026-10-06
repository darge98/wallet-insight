import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { USER_PROFILE_REPOSITORY, UserProfile } from '@wallet/user-domain';

import { UserFacade } from './user.facade';

const PROFILE: UserProfile = {
  id: 'user-1' as UserProfile['id'],
  firstName: 'Marta',
  lastName: 'Rossi',
  email: 'marta@example.com',
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'last-7-days',
};

class FakeRepository {
  readonly findCurrent = vi.fn<() => Promise<UserProfile | null>>();
}

function setup(repository: FakeRepository): UserFacade {
  TestBed.configureTestingModule({
    providers: [{ provide: USER_PROFILE_REPOSITORY, useValue: repository }],
  });
  return TestBed.inject(UserFacade);
}

describe('UserFacade', () => {
  let repository: FakeRepository;

  beforeEach(() => {
    repository = new FakeRepository();
  });

  it('parte da uno stato sconosciuto', () => {
    expect(setup(repository).status()).toBe('unknown');
  });

  it('passa ad “assente” quando il repository non trova un profilo', async () => {
    repository.findCurrent.mockResolvedValue(null);
    const facade = setup(repository);

    await facade.ensureLoaded();

    expect(facade.status()).toBe('absent');
    expect(facade.profile()).toBeNull();
  });

  it('passa a “pronto” quando il profilo esiste', async () => {
    repository.findCurrent.mockResolvedValue(PROFILE);
    const facade = setup(repository);

    await facade.ensureLoaded();

    expect(facade.status()).toBe('ready');
    expect(facade.profile()).toEqual(PROFILE);
  });

  it('distingue un errore dall’assenza di profilo', async () => {
    repository.findCurrent.mockRejectedValue(new Error('storage bloccato'));
    const facade = setup(repository);

    await facade.ensureLoaded();

    expect(facade.status()).toBe('error');
  });

  it('esegue una sola lettura anche con richieste concorrenti', async () => {
    let resolve!: (profile: UserProfile | null) => void;
    repository.findCurrent.mockReturnValue(
      new Promise((settle) => {
        resolve = settle;
      }),
    );
    const facade = setup(repository);

    const first = facade.ensureLoaded();
    const second = facade.ensureLoaded();
    resolve(PROFILE);
    await Promise.all([first, second]);

    expect(repository.findCurrent).toHaveBeenCalledTimes(1);
  });

  it('recupera dopo un errore con retry', async () => {
    repository.findCurrent.mockRejectedValueOnce(new Error('rete'));
    const facade = setup(repository);

    await facade.ensureLoaded();
    expect(facade.status()).toBe('error');

    repository.findCurrent.mockResolvedValue(PROFILE);
    await facade.retry();

    expect(facade.status()).toBe('ready');
  });

  it('adotta senza rileggere il profilo creato dall’onboarding', () => {
    const facade = setup(repository);

    facade.adopt(PROFILE);

    expect(facade.status()).toBe('ready');
    expect(facade.profile()).toEqual(PROFILE);
    expect(repository.findCurrent).not.toHaveBeenCalled();
  });
});
