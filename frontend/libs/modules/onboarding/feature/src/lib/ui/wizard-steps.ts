import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Icon } from '@wallet/shared-ui';

export interface WizardStep {
  readonly id: string;
  readonly title: string;
}

interface WizardStepView extends WizardStep {
  readonly position: number;
  readonly done: boolean;
  readonly current: boolean;
}

const MARKER_CLASSES = {
  done: 'border-transparent bg-soft text-accent',
  current: 'border-accent text-accent',
  upcoming: 'border-line text-ink-faint',
} as const;

const TITLE_CLASSES = {
  done: 'text-ink-muted',
  current: 'font-medium text-ink',
  upcoming: 'text-ink-faint',
} as const;

/**
 * Indicatore di avanzamento del wizard.
 *
 * Presentazionale e generico sugli identificativi: riceve i passi già tradotti
 * e sa soltanto quale sia quello corrente. Lo stato non è affidato al solo
 * colore — i passi conclusi portano una spunta, gli altri il proprio numero —
 * perché il colore non deve mai essere l'unico canale.
 */
@Component({
  selector: 'app-wizard-steps',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    <ol class="flex flex-col gap-4" [attr.aria-label]="label()">
      @for (step of items(); track step.id) {
        <li class="flex items-start gap-3" [attr.aria-current]="step.current ? 'step' : null">
          <span
            class="mt-px flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-[0.6875rem] leading-none font-medium"
            [class]="markerClass(step)"
          >
            @if (step.done) {
              <app-icon name="check" [size]="12" [strokeWidth]="2" />
            } @else {
              {{ step.position }}
            }
          </span>
          <span class="text-sm" [class]="titleClass(step)">{{ step.title }}</span>
        </li>
      }
    </ol>
  `,
})
export class WizardSteps {
  readonly steps = input.required<readonly WizardStep[]>();
  readonly current = input.required<string>();
  readonly label = input('Passi');

  protected readonly items = computed<readonly WizardStepView[]>(() => {
    const steps = this.steps();
    // Un identificativo sconosciuto non deve spegnere l'indicatore: si ricade
    // sul primo passo, che è comunque lo stato iniziale del wizard.
    const currentIndex = Math.max(
      0,
      steps.findIndex((step) => step.id === this.current()),
    );

    return steps.map((step, index) => ({
      ...step,
      position: index + 1,
      done: index < currentIndex,
      current: index === currentIndex,
    }));
  });

  protected markerClass(step: WizardStepView): string {
    return MARKER_CLASSES[stateOf(step)];
  }

  protected titleClass(step: WizardStepView): string {
    return TITLE_CLASSES[stateOf(step)];
  }
}

function stateOf(step: WizardStepView): 'done' | 'current' | 'upcoming' {
  if (step.done) return 'done';
  return step.current ? 'current' : 'upcoming';
}
