import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Pannello pieno sul tono tenue del brand, senza bordo.
 *
 * Riservato a un solo blocco per schermata: è l'unico accento cromatico esteso
 * della pagina e perde forza se ripetuto.
 */
@Component({
  selector: 'app-soft-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col rounded-card bg-soft p-7' },
  template: `
    @if (heading()) {
      <h2 class="type-section text-accent">{{ heading() }}</h2>
    }
    <ng-content />
  `,
})
export class SoftPanel {
  readonly heading = input<string>('');
}
