import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { Badge } from '@wallet/shared-ui';

/**
 * Una sorgente dati fra cui scegliere, presentata come scheda selezionabile.
 *
 * È un radio a tutti gli effetti — ruolo, stato e navigazione da tastiera
 * compresi — solo con un'area di click grande quanto la scheda. Una sorgente
 * non ancora disponibile resta visibile ma disabilitata: l'utente vede che cosa
 * arriverà, e il perché è scritto nella descrizione, non affidato al colore.
 */
@Component({
  selector: 'app-import-source-option',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge],
  host: { class: 'block' },
  template: `
    <button
      type="button"
      role="radio"
      [attr.aria-checked]="selected()"
      [disabled]="disabled()"
      [class]="buttonClass()"
      (click)="choose.emit()"
    >
      <span
        class="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border"
        [class]="selected() ? 'border-accent' : 'border-line'"
      >
        @if (selected()) {
          <span class="h-2 w-2 rounded-full bg-accent"></span>
        }
      </span>

      <span class="flex flex-col gap-1">
        <span class="flex flex-wrap items-center gap-2">
          <span class="text-sm font-medium text-ink">{{ name() }}</span>
          @if (tag(); as label) {
            <app-badge [tone]="selected() ? 'accent' : 'neutral'">{{ label }}</app-badge>
          }
        </span>
        <span class="text-xs text-ink-muted">{{ description() }}</span>
      </span>
    </button>
  `,
})
export class ImportSourceOption {
  readonly name = input.required<string>();
  readonly description = input.required<string>();
  readonly selected = input(false);
  readonly disabled = input(false);
  readonly tag = input<string | null>(null);

  readonly choose = output<void>();

  protected readonly buttonClass = computed(() => {
    const base =
      'flex w-full items-start gap-3 rounded-control border p-4 text-left transition-colors';
    if (this.disabled()) {
      return `${base} cursor-not-allowed border-line bg-bg opacity-60`;
    }
    return this.selected()
      ? `${base} cursor-pointer border-accent bg-soft`
      : `${base} cursor-pointer border-line bg-bg hover:border-ink-faint`;
  });
}
