import { describe, expect, it } from 'vitest';

import { appRoutes } from './app.routes';

const shellRoute = appRoutes.find((route) => route.component !== undefined);
const onboardingRoute = appRoutes.find((route) => route.path === 'onboarding');
const shellChildren = shellRoute?.children ?? [];

function childWithPath(path: string) {
  return shellChildren.find((route) => route.path === path);
}

describe('appRoutes', () => {
  it('monta ogni sezione sotto la shell', () => {
    expect(shellRoute?.component).toBeDefined();
    expect(childWithPath('panoramica')?.loadChildren).toBeTypeOf('function');
    expect(childWithPath('movimenti')?.loadChildren).toBeTypeOf('function');
  });

  it('apre sulla panoramica', () => {
    expect(childWithPath('')?.redirectTo).toBe('panoramica');
  });

  it('conserva i percorsi della versione precedente', () => {
    expect(childWithPath('dashboard')?.redirectTo).toBe('panoramica');
    expect(childWithPath('records')?.redirectTo).toBe('movimenti');
  });

  it('riporta alla radice i percorsi sconosciuti', () => {
    expect(appRoutes.at(-1)).toMatchObject({ path: '**', redirectTo: '' });
  });

  it('monta l’onboarding fuori dalla shell', () => {
    expect(onboardingRoute?.loadChildren).toBeTypeOf('function');
    expect(onboardingRoute?.component).toBeUndefined();
    expect(onboardingRoute?.children).toBeUndefined();
  });

  it('protegge sia la shell sia l’onboarding con una guardia', () => {
    expect(shellRoute?.canMatch?.length).toBeGreaterThan(0);
    expect(onboardingRoute?.canMatch?.length).toBeGreaterThan(0);
  });
});
