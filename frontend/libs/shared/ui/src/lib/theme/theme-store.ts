import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

export const THEME_PREFERENCES = ['system', 'light', 'dark'] as const;

export type ThemePreference = (typeof THEME_PREFERENCES)[number];

export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'wallet-insights.theme';

/**
 * Sorgente di verità per il tema.
 *
 * L'attributo `data-theme` sull'elemento radice viene aggiornato **prima** che
 * i signal cambino: così ogni consumatore che legge le CSS custom properties
 * (in primis i grafici) vede sempre valori coerenti con il tema corrente.
 */
@Injectable({ providedIn: 'root' })
export class ThemeStore {
  private readonly document = inject(DOCUMENT);
  private readonly darkMediaQuery = this.document.defaultView?.matchMedia(
    '(prefers-color-scheme: dark)',
  );

  private readonly preferenceState = signal<ThemePreference>(this.readStoredPreference());
  private readonly systemPrefersDark = signal<boolean>(this.darkMediaQuery?.matches ?? true);

  readonly preference = this.preferenceState.asReadonly();

  readonly resolved = computed<ResolvedTheme>(() =>
    this.resolve(this.preferenceState(), this.systemPrefersDark()),
  );

  readonly isDark = computed(() => this.resolved() === 'dark');

  constructor() {
    this.applyToDom(this.resolve(this.preferenceState(), this.systemPrefersDark()));

    this.darkMediaQuery?.addEventListener('change', (event) => {
      this.applyToDom(this.resolve(this.preferenceState(), event.matches));
      this.systemPrefersDark.set(event.matches);
    });
  }

  setPreference(preference: ThemePreference): void {
    this.applyToDom(this.resolve(preference, this.systemPrefersDark()));
    this.preferenceState.set(preference);
    this.persist(preference);
  }

  /** Alterna chiaro/scuro partendo dal tema effettivamente applicato. */
  toggle(): void {
    this.setPreference(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  private resolve(preference: ThemePreference, systemPrefersDark: boolean): ResolvedTheme {
    if (preference === 'system') {
      return systemPrefersDark ? 'dark' : 'light';
    }
    return preference;
  }

  private applyToDom(theme: ResolvedTheme): void {
    this.document.documentElement.dataset['theme'] = theme;
  }

  private readStoredPreference(): ThemePreference {
    const stored = safeStorageRead(this.document.defaultView, STORAGE_KEY);
    return isThemePreference(stored) ? stored : 'light';
  }

  private persist(preference: ThemePreference): void {
    safeStorageWrite(this.document.defaultView, STORAGE_KEY, preference);
  }
}

function isThemePreference(value: string | null): value is ThemePreference {
  return value !== null && (THEME_PREFERENCES as readonly string[]).includes(value);
}

/* Lo storage può lanciare (finestra privata, cookie bloccati): mai far fallire il boot. */
function safeStorageRead(view: Window | null | undefined, key: string): string | null {
  try {
    return view?.localStorage.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function safeStorageWrite(view: Window | null | undefined, key: string, value: string): void {
  try {
    view?.localStorage.setItem(key, value);
  } catch {
    /* Preferenza non persistita: comportamento accettabile, si torna al default. */
  }
}
