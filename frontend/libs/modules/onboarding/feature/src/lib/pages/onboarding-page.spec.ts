import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  CompleteOnboardingCommand,
  ONBOARDING_REPOSITORY,
  OnboardingFailed,
  OnboardingResult,
} from '@wallet/onboarding-domain';
import { USER_PROFILE_REPOSITORY, UserProfile } from '@wallet/user-domain';

import { OnboardingPage } from './onboarding-page';

const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl';

const PROFILE: UserProfile = {
  id: 'user-1' as UserProfile['id'],
  firstName: 'Marta',
  lastName: null,
  email: null,
  timeZone: 'Europe/Rome',
  language: 'it',
  defaultDashboardPeriod: 'current-month',
};

const complete = vi.fn<(command: CompleteOnboardingCommand) => Promise<OnboardingResult>>();

/** L'indice delle schede segue il catalogo: BudgetBakers, PSD2, "decido più tardi". */
const LATER = 2;
const BUDGET_BAKERS = 0;

function setup(): ComponentFixture<OnboardingPage> {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'panoramica', children: [] }]),
      { provide: ONBOARDING_REPOSITORY, useValue: { complete } },
      { provide: USER_PROFILE_REPOSITORY, useValue: { findCurrent: vi.fn() } },
    ],
  });

  const fixture = TestBed.createComponent(OnboardingPage);
  fixture.detectChanges();
  return fixture;
}

function page(fixture: ComponentFixture<OnboardingPage>): HTMLElement {
  return fixture.nativeElement as HTMLElement;
}

function type(fixture: ComponentFixture<OnboardingPage>, selector: string, value: string): void {
  const field = page(fixture).querySelector<HTMLInputElement>(selector);
  if (!field) throw new Error(`Campo assente: ${selector}`);

  field.value = value;
  field.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

/**
 * Invia il form come fa il browser.
 *
 * È il gesto che conta: se il `<form>` non è governato da una direttiva di
 * Angular, l'evento non arriva a nessuno — l'utente vedrebbe la pagina
 * ricaricarsi e l'onboarding ripartire da capo.
 */
async function submitForm(fixture: ComponentFixture<OnboardingPage>): Promise<void> {
  const form = page(fixture).querySelector('form');
  if (!form) throw new Error('Form assente.');

  form.dispatchEvent(new Event('submit'));
  await fixture.whenStable();
  fixture.detectChanges();
}

function select(fixture: ComponentFixture<OnboardingPage>, selector: string, value: string): void {
  const field = page(fixture).querySelector<HTMLSelectElement>(selector);
  if (!field) throw new Error(`Campo assente: ${selector}`);

  field.value = value;
  field.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

function check(fixture: ComponentFixture<OnboardingPage>, selector: string): void {
  const field = page(fixture).querySelector<HTMLInputElement>(selector);
  if (!field) throw new Error(`Campo assente: ${selector}`);

  field.checked = true;
  field.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

function chooseSource(fixture: ComponentFixture<OnboardingPage>, index: number): void {
  const cards = page(fixture).querySelectorAll<HTMLButtonElement>(
    'app-import-source-option button',
  );
  cards[index]?.click();
  fixture.detectChanges();
}

/** Il passo raggiunto si legge dall'intestazione del form, l'unica che cambia. */
function headingId(fixture: ComponentFixture<OnboardingPage>): string | undefined {
  return page(fixture).querySelector('form section')?.getAttribute('aria-labelledby') ?? undefined;
}

async function reachImportStep(): Promise<ComponentFixture<OnboardingPage>> {
  const fixture = setup();
  type(fixture, '#firstName', 'Marta');
  await submitForm(fixture);
  return fixture;
}

describe('OnboardingPage', () => {
  beforeAll(() => {
    // `ThemeStore` interroga il tema di sistema, che jsdom non conosce.
    window.matchMedia ??= ((query: string) =>
      ({
        matches: false,
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList) as typeof window.matchMedia;
  });

  beforeEach(() => {
    complete.mockReset();
    complete.mockResolvedValue({ profile: PROFILE, connections: [] });
    localStorage.clear();
  });

  it('resta sul primo passo finché manca il nome', async () => {
    const fixture = setup();

    await submitForm(fixture);

    expect(headingId(fixture)).toBe('onboarding-identity');
  });

  it('passa al secondo passo con un profilo valido', async () => {
    const fixture = await reachImportStep();

    expect(headingId(fixture)).toBe('onboarding-import');
  });

  it('consegna profilo e scelta di rimandare in una chiamata sola', async () => {
    const fixture = await reachImportStep();
    chooseSource(fixture, LATER);

    await submitForm(fixture);

    expect(complete).toHaveBeenCalledTimes(1);
    expect(complete).toHaveBeenCalledWith(
      expect.objectContaining({
        profile: expect.objectContaining({ firstName: 'Marta', lastName: null, email: null }),
        importConnection: null,
      }),
    );
  });

  it('raccoglie anche i campi che non sono caselle di testo', async () => {
    const fixture = setup();
    type(fixture, '#firstName', 'Marta');
    select(fixture, '#timeZone', 'Europe/Lisbon');
    check(fixture, 'input[type="radio"][value="last-7-days"]');
    await submitForm(fixture);
    chooseSource(fixture, LATER);

    await submitForm(fixture);

    expect(complete).toHaveBeenCalledWith(
      expect.objectContaining({
        profile: expect.objectContaining({
          timeZone: 'Europe/Lisbon',
          defaultDashboardPeriod: 'last-7-days',
        }),
      }),
    );
  });

  it('consegna il token insieme al profilo quando la sorgente è collegata', async () => {
    const fixture = await reachImportStep();
    chooseSource(fixture, BUDGET_BAKERS);
    type(fixture, '#token', TOKEN);

    await submitForm(fixture);

    expect(complete).toHaveBeenCalledWith(
      expect.objectContaining({
        importConnection: { source: 'budget-bakers', token: TOKEN },
      }),
    );
  });

  it('non chiama l’API senza una scelta', async () => {
    const fixture = await reachImportStep();

    await submitForm(fixture);

    expect(complete).not.toHaveBeenCalled();
  });

  it('non chiama l’API con un token che non è un JWT', async () => {
    const fixture = await reachImportStep();
    chooseSource(fixture, BUDGET_BAKERS);
    type(fixture, '#token', 'non-un-jwt');

    await submitForm(fixture);

    expect(complete).not.toHaveBeenCalled();
  });

  it('mostra un rimedio quando l’email è già registrata', async () => {
    complete.mockRejectedValue(new OnboardingFailed('conflict'));

    const fixture = await reachImportStep();
    chooseSource(fixture, LATER);
    await submitForm(fixture);

    expect(page(fixture).querySelector('[role="alert"]')?.textContent).toMatch(/email/i);
  });
});
