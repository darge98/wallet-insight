import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Badge, Icon } from '@wallet/shared-ui';

import { UPCOMING_SECTIONS, UpcomingSectionKey } from '../upcoming-section';

/**
 * Pagina delle sezioni annunciate.
 *
 * La chiave della sezione arriva dai `data` della rotta: una sola pagina serve
 * l'Assistente AI.
 */
@Component({
  selector: 'app-upcoming-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Badge, Icon],
  templateUrl: './upcoming-page.html',
  host: { class: 'block' },
})
export class UpcomingPage {
  readonly section = input.required<UpcomingSectionKey>();

  protected readonly content = computed(() => UPCOMING_SECTIONS[this.section()]);
}
