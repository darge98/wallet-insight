import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'accent';

const TONE_CLASSES: Readonly<Record<BadgeTone, string>> = {
  neutral: 'border-line text-ink-muted',
  accent: 'border-transparent bg-soft text-accent',
};

@Component({
  selector: 'app-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'inline-flex items-center rounded-full border px-2 py-px text-[0.6875rem] leading-5 font-medium',
    '[class]': 'toneClass()',
  },
  template: '<ng-content />',
})
export class Badge {
  readonly tone = input<BadgeTone>('neutral');
  protected readonly toneClass = computed(() => TONE_CLASSES[this.tone()]);
}
