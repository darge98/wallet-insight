import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';

import {
  CUSTOM_PERIOD,
  DateRange,
  PERIOD_FILTER_PRESETS,
  PERIOD_PRESET_LABELS,
  PeriodChoice,
  PeriodPreset,
} from '@wallet/shared-domain';

import { SegmentedControl, SegmentedOption } from './segmented-control';

const PERIOD_OPTIONS: readonly SegmentedOption<PeriodChoice>[] = [
  ...PERIOD_FILTER_PRESETS.map((preset) => ({
    value: preset satisfies PeriodPreset as PeriodChoice,
    label: PERIOD_PRESET_LABELS[preset],
  })),
  { value: CUSTOM_PERIOD, label: 'Date' },
];

/**
 * Barra del periodo: nove preset più due date libere.
 *
 * Vive in `shared` perché è la stessa in Panoramica e in Movimenti, e due
 * schermate non possono nominarsi a vicenda. Averne una sola non è solo
 * economia di codice: il periodo è la domanda che si fa per prima a entrambe, e
 * due barre diverse la farebbero sembrare due domande diverse.
 *
 * Componente di presentazione: riceve lo stato corrente e notifica le
 * intenzioni dell'utente, senza sapere da dove arrivano i dati.
 */
@Component({
  selector: 'app-period-filter-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SegmentedControl],
  host: { class: 'block' },
  template: `
    <!--
      «--segments-wrap:nowrap» tiene i segmenti su una riga sola: qui non devono
      andare a capo ma scorrere, perché dieci periodi su più file mangerebbero
      la barra — su un telefono sarebbero quattro file, un quinto dello schermo.
      Il selettore va a capo di suo: è questa riga a chiedergli di non farlo, ed
      è per questo che accanto c'è uno scroller che tiene in vista quello acceso.

      «-mx-1 px-1» perché il contorno di messa a fuoco del primo e dell'ultimo
      segmento non venga tagliato dal contenitore che scorre.
    -->
    <div #scroller class="app-scroll -mx-1 overflow-x-auto px-1 pb-1 [--segments-wrap:nowrap]">
      <app-segmented-control
        [label]="label()"
        [options]="periodOptions"
        [value]="period()"
        (valueChange)="onPeriod($event)"
      />
    </div>

    @if (custom()) {
      <!--
        Due campi nativi e nessun calendario nostro: il selettore del sistema
        operativo sa già la lingua, il primo giorno della settimana e come si
        tocca su un telefono. «min» e «max» incrociati dicono al browser quale
        metà del calendario ha senso, prima ancora che si scelga.
      -->
      <div class="fade-in mt-3 flex flex-wrap items-center gap-x-5 gap-y-2">
        <label class="flex items-center gap-2 text-sm text-ink-muted">
          Dal
          <input
            type="date"
            class="field w-auto cursor-pointer px-2.5"
            [value]="range().from"
            [max]="range().to"
            (change)="onFrom($event)"
          />
        </label>

        <label class="flex items-center gap-2 text-sm text-ink-muted">
          al
          <input
            type="date"
            class="field w-auto cursor-pointer px-2.5"
            [value]="range().to"
            [min]="range().from"
            (change)="onTo($event)"
          />
        </label>
      </div>
    }
  `,
})
export class PeriodFilterBar {
  readonly period = input.required<PeriodChoice>();
  /** La finestra effettiva: è ciò da cui partono le due date quando si passa a mano. */
  readonly range = input.required<DateRange>();
  /** Il nome del gruppo per chi naviga a voce: cambia col posto in cui sta la barra. */
  readonly label = input('Periodo');

  readonly periodChange = output<PeriodPreset>();
  readonly customRangeChange = output<DateRange>();

  protected readonly periodOptions = PERIOD_OPTIONS;

  protected readonly custom = computed(() => this.period() === CUSTOM_PERIOD);

  private readonly scroller = viewChild.required<ElementRef<HTMLElement>>('scroller');

  constructor() {
    // Undici periodi non stanno in una riga sotto una certa larghezza, quindi la
    // riga scorre. Il segmento acceso però deve restare visibile: sceglierne uno
    // che finisce fuori campo vuol dire non poter più leggere quale finestra si
    // sta guardando, che è esattamente il motivo per cui il periodo sta qui.
    afterRenderEffect(() => {
      this.period();
      this.scroller()
        .nativeElement.querySelector<HTMLElement>('[aria-checked="true"]')
        ?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
    });
  }

  protected onPeriod(choice: PeriodChoice): void {
    // Passare alle date a mano non deve cambiare ciò che si sta guardando: si
    // parte dalla finestra corrente, e da lì la si sposta.
    if (choice === CUSTOM_PERIOD) {
      this.customRangeChange.emit(this.range());
      return;
    }
    this.periodChange.emit(choice);
  }

  /**
   * Spostare un capo oltre l'altro trascina l'altro con sé, invece di rifiutare
   * la modifica: una finestra rovesciata non esiste, e un campo che non reagisce
   * sembra rotto.
   */
  protected onFrom(event: Event): void {
    const from = (event.target as HTMLInputElement).value;
    if (!from) return;

    const { to } = this.range();
    this.customRangeChange.emit({ from, to: to < from ? from : to });
  }

  protected onTo(event: Event): void {
    const to = (event.target as HTMLInputElement).value;
    if (!to) return;

    const { from } = this.range();
    this.customRangeChange.emit({ from: from > to ? to : from, to });
  }
}
