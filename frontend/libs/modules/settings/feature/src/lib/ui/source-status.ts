import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ImportConnection, importSourceDescriptor } from '@wallet/ingestion-domain';
import { formatLongDate, Icon } from '@wallet/shared-ui';

/**
 * Lo stato di una sorgente collegata.
 *
 * Mostra **due** date, perché rispondono a domande diverse. «Dati fino al» dice
 * fino a quando si possiede lo storico; «controllato» dice quando si è guardato
 * l'ultima volta. Un aggiornamento che non trova nulla muove solo la seconda, ed
 * è proprio quella differenza a distinguere «sono aggiornato, non c'era niente di
 * nuovo» da «è fermo da giorni e non so perché».
 *
 * Del token esce solo la traccia (`…a1b2`): serve a riconoscere *quale*
 * credenziale è collegata, e nient'altro deve mai uscire dal backend.
 */
@Component({
  selector: 'app-source-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
      <p class="text-sm font-medium text-ink">{{ sourceLabel() }}</p>
      @if (!connection().enabled) {
        <span class="text-xs text-ink-faint">In pausa</span>
      }
    </div>

    @if (rejectedAt(); as rejectedAt) {
      <p class="mt-2 flex items-start gap-2 text-xs text-warning" role="status">
        <app-icon name="alert" [size]="14" class="mt-px shrink-0" />
        <span> Token rifiutato il {{ rejectedAt }}. Aggiornamenti sospesi. </span>
      </p>
    }

    <dl class="mt-2 grid gap-1 text-xs text-ink-muted">
      <div class="flex items-baseline justify-between gap-4">
        <dt>Dati fino al</dt>
        <dd class="tnum text-ink">{{ dataThrough() }}</dd>
      </div>
      <div class="flex items-baseline justify-between gap-4">
        <dt class="flex items-center gap-1.5">
          <app-icon name="clock" [size]="12" />
          Controllato
        </dt>
        <dd class="tnum text-ink">{{ checkedAt() }}</dd>
      </div>
      @if (connection().secretHint) {
        <div class="flex items-baseline justify-between gap-4">
          <dt>Token</dt>
          <dd class="tnum text-ink-faint">{{ connection().secretHint }}</dd>
        </div>
      }
    </dl>
  `,
})
export class SourceStatus {
  readonly connection = input.required<ImportConnection>();

  protected readonly sourceLabel = computed(
    () => importSourceDescriptor(this.connection().source).name,
  );

  protected readonly dataThrough = computed(() => {
    const date = this.connection().lastRecordDate;
    // "Mai" e non un trattino: l'assenza qui ha un significato preciso — la
    // sorgente è collegata ma non è mai stata letta — e va detta.
    return date ? formatLongDate(date) : 'Mai importata';
  });

  protected readonly rejectedAt = computed(() => {
    const instant = this.connection().credentialsRejectedAt;
    return instant ? RUN_FORMATTER.format(new Date(instant)) : null;
  });

  protected readonly checkedAt = computed(() => {
    const instant = this.connection().lastRunAt;
    return instant ? RUN_FORMATTER.format(new Date(instant)) : 'Mai';
  });
}

/** Data e ora insieme: un aggiornamento di stamattina e uno di ieri sera vanno distinti. */
const RUN_FORMATTER = new Intl.DateTimeFormat('it-IT', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
