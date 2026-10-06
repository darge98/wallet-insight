import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ThemeStore } from './theme-store';

const radice = () => document.documentElement.dataset['theme'];

describe('ThemeStore', () => {
  let sistemaScuro: boolean;
  let cambiaSistema: (scuro: boolean) => void;

  beforeEach(() => {
    localStorage.clear();
    sistemaScuro = false;
    // jsdom non ha matchMedia: lo si simula, con la possibilità di cambiarlo.
    vi.stubGlobal('matchMedia', () => ({
      get matches() {
        return sistemaScuro;
      },
      addEventListener: (_: string, ascolta: (evento: { matches: boolean }) => void) => {
        cambiaSistema = (scuro) => {
          sistemaScuro = scuro;
          ascolta({ matches: scuro });
        };
      },
    }));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('parte chiaro e lo scrive sulla radice', () => {
    const tema = TestBed.inject(ThemeStore);

    expect(tema.resolved()).toBe('light');
    expect(radice()).toBe('light');
  });

  it('alterna il tema e ricorda la scelta', () => {
    const tema = TestBed.inject(ThemeStore);

    tema.toggle();

    expect(tema.isDark()).toBe(true);
    expect(radice()).toBe('dark');
    expect(localStorage.getItem('wallet-insights.theme')).toBe('dark');
  });

  it('riparte dalla preferenza salvata', () => {
    localStorage.setItem('wallet-insights.theme', 'dark');

    expect(TestBed.inject(ThemeStore).preference()).toBe('dark');
  });

  it('con «sistema» segue il sistema anche quando cambia', () => {
    const tema = TestBed.inject(ThemeStore);
    tema.setPreference('system');
    expect(tema.resolved()).toBe('light');

    cambiaSistema(true);

    expect(tema.resolved()).toBe('dark');
    expect(radice()).toBe('dark');
  });
});
