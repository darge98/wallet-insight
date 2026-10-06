import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type ProgressTone = 'accent' | 'warning' | 'negative';

/** Superficie su cui poggia la barra: cambia il colore della traccia. */
export type ProgressSurface = 'default' | 'soft';

const TONE_CLASSES: Readonly<Record<ProgressTone, string>> = {
  accent: 'bg-accent',
  warning: 'bg-warning',
  negative: 'bg-negative',
};

@Component({
  selector: 'app-progress-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div
      class="h-1.5 w-full overflow-hidden rounded-full"
      [class]="trackClass()"
      role="progressbar"
      [attr.aria-valuenow]="clamped()"
      aria-valuemin="0"
      aria-valuemax="100"
      [attr.aria-label]="label()"
    >
      <div
        class="h-full rounded-full transition-[width] duration-500 ease-out"
        [class]="toneClass()"
        [style.width.%]="clamped()"
      ></div>
    </div>
  `,
})
export class ProgressBar {
  readonly value = input.required<number>();
  readonly tone = input<ProgressTone>('accent');
  readonly surface = input<ProgressSurface>('default');
  readonly label = input('Avanzamento');

  protected readonly clamped = computed(() => Math.min(100, Math.max(0, this.value())));
  protected readonly toneClass = computed(() => TONE_CLASSES[this.tone()]);
  protected readonly trackClass = computed(() =>
    this.surface() === 'soft' ? 'bg-accent/15' : 'bg-track',
  );
}
