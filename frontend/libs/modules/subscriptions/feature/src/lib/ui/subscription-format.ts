import { daysBetween } from '@wallet/subscriptions-domain';
import { IsoDate } from '@wallet/shared-domain';

/** «oggi», «domani», «tra 5 giorni»: quanto manca, che è ciò che si guarda. */
export function formatDaysUntil(today: IsoDate, date: IsoDate): string {
  const days = daysBetween(today, date);
  if (days <= 0) return 'oggi';
  if (days === 1) return 'domani';
  return `tra ${days} giorni`;
}
