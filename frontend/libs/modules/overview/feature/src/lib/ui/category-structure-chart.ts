import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';

import { Money, toMajorUnits } from '@wallet/shared-domain';
import {
  Chart,
  ChartTheme,
  EmptyState,
  formatMoney,
  formatPercent,
  Skeleton,
} from '@wallet/shared-ui';

/** Una voce della struttura: una categoria, oppure il resto messo insieme. */
export interface CategoryStructureEntry {
  readonly key: string;
  readonly label: string;
  readonly amount: Money;
  /** Quota sul totale delle uscite del periodo, in percentuale (0–100). */
  readonly share: number;
  /**
   * Quanti movimenti compongono la voce, `null` quando non ha senso contarli.
   *
   * È `null` sulla voce di resto: quel numero non ce l'abbiamo — il resto si
   * ricava per differenza, non contando righe — e scriverci "0 movimenti"
   * sarebbe una cifra inventata su una fetta che vale centinaia di euro.
   */
  readonly transactionCount: number | null;
  /**
   * `true` per la voce che raccoglie tutte le categorie fuori classifica.
   * Non è una categoria e non deve sembrarlo: non ha un conteggio proprio da
   * raccontare, e la fetta è grigia.
   */
  readonly residual: boolean;
}

const CHART_HEIGHT = 196;

/**
 * Quanto la categoria più piccola si avvicina al fondo, fra 0 (piena) e 1
 * (invisibile). Oltre 0,6 l'ultima fetta verde si confonde col grigio del resto.
 */
const LIGHTEST_STEP = 0.6;

/**
 * Come si divide la spesa del periodo, per categoria: una torta ad anello con
 * accanto la legenda che porta nomi, importi e quote.
 *
 * I colori non distinguono le categorie, le ordinano: una sola tinta, piena
 * sulla categoria più grande e sempre più chiara scendendo, così l'occhio va
 * prima dove va la maggior parte dei soldi. Chi è chi lo dice la legenda, che
 * segue lo stesso ordine — il colore non è mai l'unico canale. Il resto è
 * grigio: è la parte che non stai guardando.
 *
 * L'ultima voce si chiama "Tutto il resto" e non "Altre categorie": fra le
 * categorie della sorgente ce n'è una che si chiama "Others", e due righe che
 * dicono "le altre" sembrerebbero la stessa cosa contata due volte.
 */
@Component({
  selector: 'app-category-structure-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Chart, EmptyState, Skeleton],
  host: { class: 'block' },
  template: `
    @if (loading() && entries().length === 0) {
      <div class="flex flex-col items-center gap-5">
        <app-skeleton [height]="chartHeight" width="196px" />
        <div class="flex w-full flex-col gap-3">
          @for (placeholder of [1, 2, 3, 4]; track placeholder) {
            <app-skeleton [height]="14" />
          }
        </div>
      </div>
    } @else if (entries().length === 0) {
      <app-empty-state title="Nessuna spesa nel periodo" />
    } @else {
      <!-- Affiancati solo fra sm e lg: sotto non c'è larghezza, sopra la card
           sta nella colonna da 22rem. -->
      <div class="flex flex-col items-center gap-5 sm:flex-row lg:flex-col">
        <div class="relative w-[196px] shrink-0">
          <app-chart
            [options]="options()"
            [height]="chartHeight"
            [description]="accessibleDescription()"
          />
          @if (total(); as total) {
            <div
              class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center"
              aria-hidden="true"
            >
              <span class="text-xs text-ink-muted">Uscite</span>
              <span class="tnum type-figure-sm text-ink">{{
                formatMoney(total, { decimals: false })
              }}</span>
            </div>
          }
        </div>

        <ul class="flex w-full min-w-0 flex-col gap-2.5">
          @for (entry of entries(); track entry.key; let index = $index) {
            <li class="flex items-baseline gap-2.5 text-[0.8125rem]">
              <span
                class="h-2.5 w-2.5 shrink-0 translate-y-px rounded-full"
                [style.background-color]="colors()[index]"
              ></span>
              <span
                class="min-w-0 flex-1 truncate"
                [class]="entry.residual ? 'text-ink-muted' : 'text-ink'"
                [title]="entry.label"
                >{{ entry.label }}</span
              >
              <span class="tnum text-ink">{{
                formatMoney(entry.amount, { decimals: false })
              }}</span>
              <span class="tnum w-12 text-right text-ink-muted">{{
                formatPercent(entry.share)
              }}</span>
            </li>
          }
        </ul>
      </div>
    }
  `,
})
export class CategoryStructureChart {
  private readonly chartTheme = inject(ChartTheme);

  readonly entries = input.required<readonly CategoryStructureEntry[]>();
  /** Le uscite del periodo, scritte al centro dell'anello; assenti finché non arrivano. */
  readonly total = input<Money | undefined>();
  readonly loading = input(false);

  protected readonly chartHeight = CHART_HEIGHT;
  protected readonly formatMoney = formatMoney;
  protected readonly formatPercent = formatPercent;

  /** Un colore per voce, nello stesso ordine: la torta e la legenda li condividono. */
  protected readonly colors = computed(() => {
    const tokens = this.chartTheme.tokens();
    const entries = this.entries();
    const categorie = entries.filter((entry) => !entry.residual).length;

    let posizione = 0;
    return entries.map((entry) => {
      if (entry.residual) return mixColors(tokens.axis, tokens.surface, 0.5);
      const passo = categorie > 1 ? (posizione++ / (categorie - 1)) * LIGHTEST_STEP : 0;
      return mixColors(tokens.line, tokens.surface, passo);
    });
  });

  /**
   * Il grafico per chi non lo vede: le prime tre voci, dove sta la risposta.
   * Gli importi completi restano nella legenda, che è testo.
   */
  protected readonly accessibleDescription = computed(() => {
    const entries = this.entries();
    if (entries.length === 0) return 'Struttura delle spese per categoria';

    const prime = entries
      .slice(0, 3)
      .map((entry) => `${entry.label} ${formatMoney(entry.amount)} (${formatPercent(entry.share)})`)
      .join(', ');
    return `Struttura delle spese per categoria: ${prime}. In tutto ${entries.length} voci.`;
  });

  protected readonly options = computed<EChartsCoreOption>(() => {
    const tokens = this.chartTheme.tokens();
    const entries = this.entries();
    const colors = this.colors();

    return {
      ...this.chartTheme.baseOption(),
      tooltip: {
        ...(this.chartTheme.baseOption()['tooltip'] as object),
        trigger: 'item',
        formatter: (params: unknown) => {
          const index = (params as { dataIndex?: number }).dataIndex ?? 0;
          const entry = entries[index];
          if (!entry) return '';

          const movimenti =
            entry.transactionCount === 1 ? '1 movimento' : `${entry.transactionCount} movimenti`;
          const nota = entry.residual
            ? `Le categorie oltre le prime ${entries.length - 1}`
            : movimenti;
          return `<div style="color:${tokens.inkMuted};font-size:11px">${entry.label}</div>
            <div style="margin-top:2px;font-weight:500;font-variant-numeric:tabular-nums">${formatMoney(
              entry.amount,
            )} · ${formatPercent(entry.share)}</div>
            <div style="color:${tokens.inkMuted};font-size:11px">${nota}</div>`;
        },
      },
      series: [
        {
          name: 'Uscite per categoria',
          type: 'pie',
          radius: ['62%', '96%'],
          // Si parte da mezzogiorno in senso orario, nell'ordine della legenda:
          // la fetta più grande è la prima che si incontra.
          startAngle: 90,
          clockwise: true,
          label: { show: false },
          labelLine: { show: false },
          // Il bordo del colore del fondo stacca le fette fra loro anche quando
          // due tinte vicine si somigliano.
          itemStyle: { borderColor: tokens.surface, borderWidth: 2, borderRadius: 4 },
          emphasis: { scale: true, scaleSize: 4, label: { show: false } },
          data: entries.map((entry, index) => ({
            name: entry.label,
            value: toMajorUnits(entry.amount),
            itemStyle: { color: colors[index] },
          })),
        },
      ],
    };
  });
}

/**
 * Mescola due colori `rgb()/rgba()` — la forma in cui li consegna `ChartTheme` —
 * spostandosi da `from` verso `to` della frazione `weight`.
 */
function mixColors(from: string, to: string, weight: number): string {
  const a = parseRgb(from);
  const b = parseRgb(to);
  if (!a || !b) return from;

  const [r, g, bl] = a.map((channel, index) =>
    Math.round(channel + ((b[index] ?? channel) - channel) * weight),
  );
  return `rgb(${r}, ${g}, ${bl})`;
}

function parseRgb(color: string): number[] | null {
  const match = /rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)/.exec(color);
  return match ? match.slice(1, 4).map(Number) : null;
}
