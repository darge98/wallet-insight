import { DateRange, endOfMonth, fromIsoDate, toIsoDate } from '@wallet/shared-domain';

const MONTH_FORMATTER = new Intl.DateTimeFormat('it-IT', { month: 'long' });

/** Mesi che richiedono la "d" eufonica: "rispetto ad agosto". */
const VOWEL_INITIALS = new Set(['a', 'e', 'i', 'o', 'u']);

/**
 * Come nominare, dopo «rispetto», il periodo con cui il server ha confrontato i KPI.
 *
 * Un mese intero ha un nome proprio («ad agosto»), un tratto di mese dal giorno 1 è
 * «lo stesso periodo» di quel mese; ogni altro periodo resta generico.
 */
export function comparisonLabel(previous: DateRange): string {
  const from = fromIsoDate(previous.from);
  if (from.getDate() !== 1 || previous.to.slice(0, 7) !== previous.from.slice(0, 7)) {
    return 'al periodo precedente';
  }
  const month = MONTH_FORMATTER.format(from);
  if (previous.to === toIsoDate(endOfMonth(from))) {
    return `${VOWEL_INITIALS.has(month.charAt(0)) ? 'ad' : 'a'} ${month}`;
  }
  return `allo stesso periodo di ${month}`;
}
