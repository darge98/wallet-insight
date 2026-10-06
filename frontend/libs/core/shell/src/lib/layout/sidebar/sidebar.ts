import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { IsActiveMatchOptions, RouterLink, RouterLinkActive } from '@angular/router';

import { AccountsOverview } from '@wallet/accounts-data-access';
import { UserFacade } from '@wallet/user-data-access';
import { displayName } from '@wallet/user-domain';
import { APP_INFO } from '../../app-info';
import { ACCOUNT_NAVIGATION, NAVIGATION_ITEMS } from '../../navigation';
import { Icon, MoneyPipe, Skeleton } from '@wallet/shared-ui';

@Component({
  selector: 'app-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icon, MoneyPipe, Skeleton],
  templateUrl: './sidebar.html',
  host: { class: 'app-scroll flex h-full w-60 flex-col overflow-y-auto bg-sidebar px-6 py-8' },
})
export class Sidebar {
  /** Emesso quando si sceglie una voce: la shell chiude il drawer mobile. */
  readonly navigated = output<void>();

  protected readonly accounts = inject(AccountsOverview);
  private readonly user = inject(UserFacade);
  protected readonly items = NAVIGATION_ITEMS;
  protected readonly accountItem = ACCOUNT_NAVIGATION;
  protected readonly appInfo = APP_INFO;

  /** Un conto è attivo solo sui propri movimenti, non su «Movimenti» di tutti i conti. */
  protected readonly exactQuery: IsActiveMatchOptions = {
    paths: 'exact',
    queryParams: 'exact',
    matrixParams: 'ignored',
    fragment: 'ignored',
  };

  /**
   * Se l'elenco dei conti è aperto.
   *
   * Aperto di partenza: è il motivo per cui la sidebar è larga così, e una
   * sezione che si apre chiusa nasconde il dato a chi non sa che c'è. Chi ha
   * molti conti la chiude e si tiene la navigazione a portata di occhio — con
   * l'elenco chiuso resta il totale, perché la domanda a cui questa sezione
   * risponde («quanto ho») non deve richiedere di riaprirla.
   *
   * Vive nel componente e non in uno store: la sidebar è montata dalla shell e
   * sopravvive ai cambi di rotta, quindi la scelta dura quanto la visita. Non
   * sopravvive a un ricaricamento, e finché non lo chiede nessuno va bene così:
   * persisterla vorrebbe dire un'altra chiave in `localStorage`.
   */
  protected readonly accountsOpen = signal(true);

  /** Nome del profilo configurato in onboarding. */
  protected readonly ownerName = computed(() => {
    const profile = this.user.profile();
    return profile ? displayName(profile) : APP_INFO.owner;
  });

  protected toggleAccounts(): void {
    this.accountsOpen.update((open) => !open);
  }
}
