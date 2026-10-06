import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Stato vuoto in sola tipografia: nessuna cornice, nessuna illustrazione. */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-1 flex-col justify-center py-10' },
  template: `
    <p class="text-sm font-medium text-ink">{{ title() }}</p>
    @if (description()) {
      <p class="mt-1 max-w-sm text-sm text-ink-muted">{{ description() }}</p>
    }
    <ng-content />
  `,
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
