import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { asUserId, UserProfile } from '@wallet/user-domain';

import { ProfileForm } from './profile-form';

const MARTA: UserProfile = {
  id: asUserId('0199ab7c-0000-7000-8000-000000000001'),
  firstName: 'Marta',
  lastName: null,
  email: null,
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'current-month',
};

describe('ProfileForm', () => {
  let fixture: ComponentFixture<ProfileForm>;
  let salvati: UserProfile[];

  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(ProfileForm);
    fixture.componentRef.setInput('profile', MARTA);
    salvati = [];
    fixture.componentInstance.save.subscribe((profilo) => salvati.push(profilo));
    fixture.detectChanges();
  });

  async function scriviEInvia(selettore: string, valore: string): Promise<void> {
    const campo = root().querySelector<HTMLInputElement>(selettore);
    if (!campo) throw new Error(`Campo assente: ${selettore}`);
    campo.value = valore;
    campo.dispatchEvent(new Event('input'));
    root().querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('manda il profilo intero col nome ripulito', async () => {
    await scriviEInvia('#settings-first-name', '  Marta Maria ');

    expect(salvati).toEqual([{ ...MARTA, firstName: 'Marta Maria' }]);
  });

  it('senza nome non salva', async () => {
    await scriviEInvia('#settings-first-name', '');

    expect(salvati).toEqual([]);
  });
});
