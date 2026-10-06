import {
  asSubscriptionId,
  CADENCE_UNITS,
  CadenceUnit,
  MonthCharges,
  Subscription,
  SubscriptionDraft,
  SubscriptionsOverview,
  UpcomingCharge,
} from '@wallet/subscriptions-domain';
import { contractError } from '@wallet/shared-data-access';
import {
  asAccountId,
  asCategoryId,
  CurrencyCode,
  isCurrencyCode,
  money,
} from '@wallet/shared-domain';

export interface SubscriptionResponse {
  readonly id: string;
  readonly name: string;
  readonly amountCents: number;
  readonly currencyCode: string;
  readonly every: number;
  readonly unit: string;
  readonly startDate: string;
  readonly endDate?: string | null;
  readonly active: boolean;
  readonly nextChargeDate?: string | null;
  readonly monthlyCents: number;
  readonly yearlyCents: number;
  readonly categoryId?: string | null;
  readonly accountId?: string | null;
}

interface UpcomingChargeResponse {
  readonly subscriptionId: string;
  readonly name: string;
  readonly date: string;
  readonly amountCents: number;
}

export interface SubscriptionsOverviewResponse {
  readonly today: string;
  readonly until: string;
  readonly currencyCode: string;
  readonly activeCount: number;
  readonly monthlyCents: number;
  readonly yearlyCents: number;
  readonly upcomingCents: number;
  readonly upcoming: readonly UpcomingChargeResponse[];
}

export interface MonthChargesResponse {
  readonly month: string;
  readonly currencyCode: string;
  readonly totalCents: number;
  readonly charges: readonly UpcomingChargeResponse[];
}

export interface SubscriptionRequest {
  readonly name: string;
  readonly amountCents: number;
  readonly every: number;
  readonly unit: CadenceUnit;
  readonly startDate: string;
  readonly endDate?: string;
  readonly categoryId?: string;
  readonly accountId?: string;
}

function currencyOf(raw: string): CurrencyCode {
  if (!isCurrencyCode(raw)) {
    throw contractError('currencyCode', raw);
  }
  return raw;
}

function unitOf(raw: string): CadenceUnit {
  const unit = CADENCE_UNITS.find((candidate) => candidate === raw);
  if (!unit) {
    throw contractError('unit', raw);
  }
  return unit;
}

export function toSubscription(raw: SubscriptionResponse): Subscription {
  const currency = currencyOf(raw.currencyCode);
  return {
    id: asSubscriptionId(raw.id),
    name: raw.name,
    amount: money(raw.amountCents, currency),
    cadence: { every: raw.every, unit: unitOf(raw.unit) },
    startDate: raw.startDate,
    endDate: raw.endDate ?? null,
    active: raw.active,
    nextChargeDate: raw.nextChargeDate ?? null,
    monthlyCost: money(raw.monthlyCents, currency),
    yearlyCost: money(raw.yearlyCents, currency),
    categoryId: raw.categoryId ? asCategoryId(raw.categoryId) : null,
    accountId: raw.accountId ? asAccountId(raw.accountId) : null,
  };
}

export function toOverview(raw: SubscriptionsOverviewResponse): SubscriptionsOverview {
  const currency = currencyOf(raw.currencyCode);
  return {
    today: raw.today,
    until: raw.until,
    activeCount: raw.activeCount,
    monthlyCost: money(raw.monthlyCents, currency),
    yearlyCost: money(raw.yearlyCents, currency),
    upcomingTotal: money(raw.upcomingCents, currency),
    upcoming: raw.upcoming.map((charge) => toCharge(charge, currency)),
  };
}

export function toMonthCharges(raw: MonthChargesResponse): MonthCharges {
  const currency = currencyOf(raw.currencyCode);
  return {
    month: raw.month,
    total: money(raw.totalCents, currency),
    charges: raw.charges.map((charge) => toCharge(charge, currency)),
  };
}

function toCharge(charge: UpcomingChargeResponse, currency: CurrencyCode): UpcomingCharge {
  return {
    subscriptionId: asSubscriptionId(charge.subscriptionId),
    name: charge.name,
    date: charge.date,
    amount: money(charge.amountCents, currency),
  };
}

/** Un campo assente vuol dire «nessuno», non «non toccare»: il PUT riscrive tutto. */
export function toRequest(draft: SubscriptionDraft): SubscriptionRequest {
  return {
    name: draft.name,
    amountCents: draft.amount.amount,
    every: draft.cadence.every,
    unit: draft.cadence.unit,
    startDate: draft.startDate,
    ...(draft.endDate ? { endDate: draft.endDate } : {}),
    ...(draft.categoryId ? { categoryId: draft.categoryId } : {}),
    ...(draft.accountId ? { accountId: draft.accountId } : {}),
  };
}
