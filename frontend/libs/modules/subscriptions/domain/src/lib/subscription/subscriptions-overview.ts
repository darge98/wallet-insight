import { IsoDate, Money, YearMonth } from '@wallet/shared-domain';

import { SubscriptionId } from './subscription';

/** Un addebito previsto. Un abbonamento settimanale ne ha più d'uno nella finestra. */
export interface UpcomingCharge {
  readonly subscriptionId: SubscriptionId;
  readonly name: string;
  readonly date: IsoDate;
  readonly amount: Money;
}

/**
 * Gli abbonamenti visti da `today`: quanto costano quelli attivi e cosa verrà
 * addebitato fino a `until` compreso. I totali li fa il server.
 */
export interface SubscriptionsOverview {
  readonly today: IsoDate;
  readonly until: IsoDate;
  readonly activeCount: number;
  readonly monthlyCost: Money;
  readonly yearlyCost: Money;
  readonly upcomingTotal: Money;
  readonly upcoming: readonly UpcomingCharge[];
}

/** Gli orizzonti fra cui si sceglie, in giorni da oggi compreso. */
export const UPCOMING_HORIZONS = [7, 30, 90] as const;

export type UpcomingHorizon = (typeof UPCOMING_HORIZONS)[number];

/** Gli addebiti di un mese di calendario, disdetti compresi fino alla loro fine. */
export interface MonthCharges {
  readonly month: YearMonth;
  readonly total: Money;
  readonly charges: readonly UpcomingCharge[];
}
