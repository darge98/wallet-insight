import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Set di icone interno.
 *
 * Tracciati a 24×24, stroke uniforme: nessuna dipendenza esterna e peso
 * trascurabile nel bundle. Il set è volutamente minimo — nel design le icone
 * compaiono solo sui controlli, mai come decorazione — quindi si aggiunge una
 * voce solo quando serve davvero.
 */
export const ICON_PATHS: Readonly<Record<string, string>> = {
  menu: 'M4 6.5h16M4 12h16M4 17.5h16',
  close: 'M6 6l12 12M18 6L6 18',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4.2-4.2',
  filter: 'M3.5 5h17l-6.6 7.7V19l-3.8 2v-8.3z',
  refresh: 'M20.5 12a8.5 8.5 0 1 1-2.8-6.3M20.5 3.5v6h-6',
  'chevron-down': 'M6 9.5l6 6 6-6',
  'chevron-left': 'M15 5.5l-6 6.5 6 6.5',
  'chevron-right': 'M9 5.5l6 6.5-6 6.5',
  'arrow-right': 'M4 12h14.5M12.5 6l6 6-6 6',
  check: 'M4.5 12.5l5 5L19.5 7',
  clock: 'M12 3.2a8.8 8.8 0 1 0 0 17.6 8.8 8.8 0 0 0 0-17.6zM12 7.2v5l3.2 1.9',
  alert: 'M12 3.5l9 16.5H3zM12 10v4M12 17.2h.01',
  plus: 'M12 5v14M5 12h14',
  sun: 'M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9zM12 1.5v2M12 20.5v2M4.5 4.5l1.4 1.4M18.1 18.1l1.4 1.4M1.5 12h2M20.5 12h2M4.5 19.5l1.4-1.4M18.1 5.9l1.4-1.4',
  moon: 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z',
};

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex shrink-0', '[attr.aria-hidden]': 'true' },
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth()"
      stroke-linecap="round"
      stroke-linejoin="round"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(18);
  readonly strokeWidth = input(1.6);

  protected readonly path = computed(() => ICON_PATHS[this.name()] ?? '');
}
