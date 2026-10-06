import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { SettingsSection } from '../settings-sections';

/**
 * Il menu laterale delle impostazioni.
 *
 * Le voci non disponibili sono `<span>` e non link disabilitati: un `<a>` che non
 * porta da nessuna parte resta comunque raggiungibile da tastiera e annunciato
 * come collegamento, e chi naviga così ci finisce sopra per scoprire che non
 * succede niente. Un testo con la sua etichetta dice la stessa cosa senza
 * promettere un'azione.
 *
 * Su viewport strette diventa una riga che scorre in orizzontale invece di una
 * colonna: rubare mezza schermata a un menu di tre voci lascerebbe il contenuto
 * in un angolo.
 */
@Component({
  selector: 'app-settings-nav',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  host: { class: 'block' },
  template: `
    <nav
      class="app-scroll -mx-6 flex gap-1.5 overflow-x-auto px-6 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0"
      aria-label="Sezioni delle impostazioni"
    >
      @for (section of sections(); track section.path) {
        @if (section.upcoming) {
          <span
            class="rounded-control px-3 py-2.5 text-sm whitespace-nowrap text-ink-faint lg:whitespace-normal"
            [title]="section.description"
          >
            {{ section.label }}
            <span class="ml-1.5 text-[0.6875rem] text-ink-faint">Presto</span>
          </span>
        } @else {
          <a
            [routerLink]="section.path"
            routerLinkActive="bg-soft font-medium text-accent"
            #link="routerLinkActive"
            [attr.aria-current]="link.isActive ? 'page' : null"
            class="rounded-control px-3 py-2.5 text-sm whitespace-nowrap text-ink-muted transition-colors hover:text-ink lg:whitespace-normal"
          >
            {{ section.label }}
          </a>
        }
      }
    </nav>
  `,
})
export class SettingsNav {
  readonly sections = input.required<readonly SettingsSection[]>();
}
