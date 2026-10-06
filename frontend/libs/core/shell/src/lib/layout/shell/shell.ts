import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { ACCOUNT_NAVIGATION, NAVIGATION_ITEMS } from '../../navigation';

import { Sidebar } from '../sidebar/sidebar';
import { Topbar } from '../topbar/topbar';

/**
 * Guscio applicativo: navigazione persistente + area di contenuto instradata.
 *
 * Su viewport strette la sidebar diventa un drawer sovrapposto.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Sidebar, Topbar],
  templateUrl: './shell.html',
  host: { class: 'flex min-h-screen bg-bg' },
})
export class Shell {
  private readonly router = inject(Router);

  protected readonly drawerOpen = signal(false);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly sectionLabel = computed(() => {
    const url = this.currentUrl();
    // Le impostazioni non sono nel menu principale ma restano una sezione: senza
    // questa riga il percorso in alto resterebbe vuoto proprio lì.
    const items = [...NAVIGATION_ITEMS, ACCOUNT_NAVIGATION];
    return items.find((item) => url.startsWith(item.path))?.label ?? '';
  });

  protected openDrawer(): void {
    this.drawerOpen.set(true);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }
}
