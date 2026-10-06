import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { EmptyState, MoneyPipe, Skeleton } from '@wallet/shared-ui';

import { SettingsFacade } from '../data-access/settings-facade';
import { AccountCard } from '../ui/account-card';
import { SourceStatus } from '../ui/source-status';
import { TokenForm } from '../ui/token-form';

/**
 * I conti e le sorgenti da cui arrivano, nella stessa pagina.
 *
 * Non sono due argomenti diversi: un conto esiste perché una sorgente l'ha
 * portato, e la domanda che si fa qui è una sola — «i miei numeri sono giusti e
 * aggiornati?». La risposta è nelle due date di ogni sorgente; l'unico gesto
 * possibile è sostituire un token, che il backend usa subito per importare.
 */
@Component({
  selector: 'app-accounts-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyState, MoneyPipe, Skeleton, AccountCard, SourceStatus, TokenForm],
  templateUrl: './accounts-page.html',
  host: { class: 'block' },
})
export class AccountsPage {
  protected readonly facade = inject(SettingsFacade);

  /** Il totale lo calcola `AccountsOverview`: qui si mostra, non si risomma. */
  protected readonly totalBalance = this.facade.totalBalance;
}
