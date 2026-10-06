import { InjectionToken } from '@angular/core';
import { CategoryBreakdownSlice } from '../analytics/category-breakdown';
import { SpendingTarget } from '../analytics/spending-target';
import { DateRange } from '../shared/date-range';
import { AccountId } from '../shared/identifier';

/**
 * Su quali movimenti si fa la classifica: un periodo e, se ci sono, dei conti.
 *
 * Nessun conto vuol dire tutti, come nei filtri dei movimenti: è la classifica
 * che sta accanto a quell'elenco, e quando l'elenco è ristretto a un conto deve
 * parlare delle stesse righe.
 */
export interface SpendingScope {
  readonly range: DateRange;
  readonly accountIds?: readonly AccountId[];
}

/**
 * Le classifiche di periodo: dove sono andati i soldi.
 *
 * Sono aggregazioni e le fa il server: il client non scarica lo storico per
 * sommarlo: su 1678 movimenti sarebbe già una cattiva idea, e lo storico cresce.
 *
 * Stanno in una porta loro e non insieme ai KPI, che pure sono aggregati, e la
 * ragione è che una porta è una capacità: queste due domande il backend sa
 * rispondergliele, i KPI di periodo e la curva cumulativa no. Tenerle insieme
 * avrebbe voluto dire un adapter HTTP con metà dei metodi non implementati — e
 * una porta che si può soddisfare solo a metà non è una porta.
 */
export interface SpendingBreakdownRepository {
  expensesByCategory(
    scope: SpendingScope,
    limit: number,
    signal?: AbortSignal,
  ): Promise<readonly CategoryBreakdownSlice[]>;
  topSpendingTargets(
    scope: SpendingScope,
    limit: number,
    signal?: AbortSignal,
  ): Promise<readonly SpendingTarget[]>;
}

export const SPENDING_BREAKDOWN_REPOSITORY = new InjectionToken<SpendingBreakdownRepository>(
  'SpendingBreakdownRepository',
);
