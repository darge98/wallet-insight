import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ImportConnections } from '@wallet/ingestion-data-access';
import { browserTimeZone } from '@wallet/shared-domain';
import { Icon, ThemeStore } from '@wallet/shared-ui';
import { UserFacade } from '@wallet/user-data-access';

import { lastRunLabel } from './last-run-label';

@Component({
  selector: 'app-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, RouterLink],
  templateUrl: './topbar.html',
  host: { class: 'flex h-8 items-center gap-3' },
})
export class Topbar {
  readonly section = input('');
  readonly menuOpened = output<void>();

  protected readonly theme = inject(ThemeStore);
  protected readonly importConnections = inject(ImportConnections);
  private readonly user = inject(UserFacade);

  protected readonly lastRun = computed(() =>
    this.importConnections.connections.hasValue()
      ? lastRunLabel(
          this.importConnections.lastRunAt(),
          this.user.profile()?.timeZone ?? browserTimeZone(),
        )
      : '',
  );
}
