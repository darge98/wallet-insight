import { DOCUMENT, Injectable, computed, inject } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';

import { ThemeStore } from '../theme/theme-store';

/** Token di colore letti dalle CSS custom properties del tema attivo. */
export interface ChartTokens {
  readonly line: string;
  readonly fillFrom: string;
  readonly fillTo: string;
  readonly grid: string;
  readonly axis: string;
  /** Fondo di una barra: dice quanto manca al massimo della classifica. */
  readonly track: string;
  readonly ink: string;
  readonly inkMuted: string;
  readonly surface: string;
  readonly border: string;
}

/**
 * Adatta la palette del design system a ECharts.
 *
 * Il concept usa **un solo accento**: i grafici non hanno una palette
 * categoriale, la grandezza è codificata da lunghezza e posizione. I colori si
 * leggono dalle CSS custom properties, quindi seguono il tema senza duplicare
 * valori nel TypeScript.
 */
@Injectable({ providedIn: 'root' })
export class ChartTheme {
  private readonly document = inject(DOCUMENT);
  private readonly themeStore = inject(ThemeStore);

  readonly tokens = computed<ChartTokens>(() => {
    // Dipendenza esplicita dal tema risolto: rilegge i token a ogni cambio.
    this.themeStore.resolved();
    return this.readTokens();
  });

  /** Opzioni comuni a tutti i grafici: niente cornici, tooltip discreto. */
  readonly baseOption = computed<EChartsCoreOption>(() => {
    const tokens = this.tokens();

    return {
      textStyle: {
        fontFamily: "'DM Sans', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",
        color: tokens.inkMuted,
      },
      animationDuration: 420,
      animationEasing: 'cubicOut',
      tooltip: {
        backgroundColor: tokens.surface,
        borderColor: tokens.border,
        borderWidth: 1,
        padding: [8, 11],
        textStyle: { color: tokens.ink, fontSize: 12 },
        extraCssText: 'border-radius:10px;box-shadow:0 8px 24px -14px rgba(9,26,20,.35);',
      },
    };
  });

  private readTokens(): ChartTokens {
    const view = this.document.defaultView;
    const styles = view?.getComputedStyle(this.document.documentElement);

    // La sonda serve solo a far normalizzare i colori dal browser, e vive il
    // tempo di una lettura: nessun nodo residuo nel documento.
    const probe = this.document.createElement('span');
    probe.style.display = 'none';
    this.document.body.appendChild(probe);

    const read = (name: string, fallback: string): string =>
      this.toLegacyColor(styles?.getPropertyValue(name).trim() || fallback, probe, view);

    try {
      return {
        line: read('--chart-line', '#22634c'),
        fillFrom: read('--chart-fill-from', 'rgba(34,99,76,.14)'),
        fillTo: read('--chart-fill-to', 'rgba(34,99,76,0)'),
        grid: read('--chart-grid', '#eaf0ec'),
        axis: read('--chart-axis', '#8a968f'),
        track: read('--chart-track', '#e9efeb'),
        ink: read('--app-text', '#192d28'),
        inkMuted: read('--app-text-muted', '#62716a'),
        surface: read('--app-surface', '#ffffff'),
        border: read('--app-border', '#dce3df'),
      };
    } finally {
      probe.remove();
    }
  }

  /**
   * Riscrive un colore nella forma `rgb()/rgba()` con le virgole.
   *
   * Il design system usa la sintassi CSS moderna (`rgb(34 99 76 / 14%)`), che
   * il parser di ECharts non riconosce: restituisce `undefined`, e le fermate
   * del gradiente dell'area finiscono senza colore. Quando zrender prova ad
   * animarle va in eccezione dentro il proprio loop, che da quel momento non
   * ridisegna piu' il canvas: il grafico resta congelato e la linea del cursore
   * non segue più il mouse.
   *
   * La serializzazione di `getComputedStyle().color` è normata dal CSSOM ed è
   * sempre nella forma con le virgole, quindi il giro dalla sonda copre
   * qualunque sintassi il browser sappia leggere, anche quelle future.
   */
  private toLegacyColor(value: string, probe: HTMLElement, view: Window | null | undefined): string {
    if (!view) return value;

    probe.style.color = '';
    probe.style.color = value;
    // Un valore che il browser non riconosce lascia la proprietà vuota.
    if (!probe.style.color) return value;

    return view.getComputedStyle(probe).color || value;
  }
}
