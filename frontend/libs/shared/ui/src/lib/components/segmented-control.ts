import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface SegmentedOption<TValue extends string> {
  readonly value: TValue;
  readonly label: string;
}

/**
 * Selettore a segmenti, single-select.
 *
 * Generico sul tipo del valore: il chiamante non perde il tipo letterale.
 * Nessuna classe di display sull'host: è chi lo usa a decidere se mostrarlo
 * (es. `hidden sm:inline-flex`), senza conflitti di specificità.
 *
 * **I segmenti vanno a capo per impostazione predefinita.** È `inline-flex`,
 * quindi la larghezza è quella del contenuto finché c'è posto e quella del
 * contenitore quando non ce n'è: su uno schermo largo resta una riga sola senza
 * che nessuno lo chieda, su un telefono si dispone su più file. Non serve né un
 * input né un secondo esemplare nascosto dietro un breakpoint.
 *
 * Chi ha bisogno della riga unica lo dice dal proprio template con la variabile
 * `--segments-wrap`, ad esempio `[--segments-wrap:nowrap]` — lo fa la barra dei
 * Movimenti, che di periodi ne ha undici e li fa scorrere in orizzontale tenendo
 * in vista quello acceso. È una proprietà personalizzata e non un input perché
 * così si compone con le varianti di Tailwind (`md:[--segments-wrap:nowrap]`)
 * senza passare dal TypeScript: la riga si spezza dove lo decide il CSS.
 */
@Component({
  selector: 'app-segmented-control',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="inline-flex items-center gap-1 rounded-control border border-line p-1 [flex-wrap:var(--segments-wrap,wrap)]"
      role="radiogroup"
      [attr.aria-label]="label()"
    >
      @for (option of options(); track option.value) {
        <button
          type="button"
          role="radio"
          [attr.aria-checked]="option.value === value()"
          class="min-h-10 cursor-pointer rounded-md px-3 text-xs whitespace-nowrap transition-colors sm:min-h-0 sm:px-2.5 sm:py-1"
          [class]="
            option.value === value()
              ? 'bg-soft font-medium text-accent'
              : 'text-ink-muted hover:text-ink'
          "
          (click)="value.set(option.value)"
        >
          {{ option.label }}
        </button>
      }
    </div>
  `,
})
export class SegmentedControl<TValue extends string> {
  readonly options = input.required<readonly SegmentedOption<TValue>[]>();
  readonly value = model.required<TValue>();
  readonly label = input('Selezione');
}
