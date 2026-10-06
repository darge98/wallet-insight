/** Data in formato ISO `yyyy-MM-dd`, senza fuso orario. */
export type IsoDate = string;

/** Intervallo di date con estremi inclusi. */
export interface DateRange {
  readonly from: IsoDate;
  readonly to: IsoDate;
}

/**
 * Tutti i preset offerti dall'applicazione.
 *
 * Il vocabolario vive nel kernel condiviso — non nel dominio Utente — perché è
 * un concetto di calendario usato sia dalla Panoramica sia dai Movimenti: il
 * profilo utente si limita a *registrare* quale preset è il suo predefinito.
 */
export const PERIOD_PRESETS = [
  'today',
  'current-week',
  'current-month',
  'previous-month',
  'last-7-days',
  'last-30-days',
  'last-3-months',
  'last-6-months',
  'current-year',
] as const;

export type PeriodPreset = (typeof PERIOD_PRESETS)[number];

/**
 * Preset selezionabili nella Panoramica.
 *
 * È anche l'insieme tra cui l'onboarding lascia scegliere il periodo predefinito:
 * una preferenza deve sempre restare selezionabile dove viene applicata.
 */
export const DASHBOARD_PERIOD_PRESETS = [
  'today',
  'current-week',
  'current-month',
  'current-year',
  'last-7-days',
  'last-30-days',
] as const;

export type DashboardPeriodPreset = (typeof DASHBOARD_PERIOD_PRESETS)[number];

/**
 * Preset della barra del periodo: tutti, dal giorno all'anno.
 *
 * Sono gli stessi in Panoramica e in Movimenti, ed è una scelta esplicita: la
 * domanda che si fa alle due schermate è la stessa — quale finestra guardo — e
 * due elenchi diversi obbligherebbero a impararla due volte. L'elenco contiene
 * per intero quello delle impostazioni (`DASHBOARD_PERIOD_PRESETS`): un periodo
 * che si può eleggere a predefinito deve restare raggiungibile dove si applica.
 *
 * L'ordine è quello della durata crescente, perché è l'unico per cui scorrere
 * di un segmento significa sempre la stessa cosa: allargare la finestra.
 */
export const PERIOD_FILTER_PRESETS = [
  'today',
  'current-week',
  'last-7-days',
  'current-month',
  'last-30-days',
  'previous-month',
  'last-3-months',
  'last-6-months',
  'current-year',
] as const;

/**
 * Il periodo scelto a mano, accanto ai preset.
 *
 * Sta nello stesso vocabolario perché è la stessa domanda — «quale finestra sto
 * guardando» — e perché le due risposte si escludono: o vale un preset, o valgono
 * le due date. Tenerle in due stati separati vorrebbe dire poterne accendere due
 * insieme, e non saper dire quale comanda.
 */
export const CUSTOM_PERIOD = 'custom';

export type PeriodChoice = PeriodPreset | typeof CUSTOM_PERIOD;

/**
 * Un intervallo scelto a mano vale solo se ha entrambi i capi.
 *
 * Una finestra con un capo solo il server la rifiuta, ed è giusto: meglio un
 * errore di un elenco che mostra un periodo diverso da quello chiesto.
 */
export function toDateRange(from: string, to: string): DateRange | null {
  if (!from || !to || from > to) {
    return null;
  }
  return { from, to };
}

export const PERIOD_PRESET_LABELS: Readonly<Record<PeriodPreset, string>> = {
  today: 'Oggi',
  'current-week': 'Questa settimana',
  'current-month': 'Questo mese',
  'previous-month': 'Mese scorso',
  'last-7-days': 'Ultimi 7 giorni',
  'last-30-days': 'Ultimi 30 giorni',
  'last-3-months': '3 mesi',
  'last-6-months': '6 mesi',
  'current-year': "Quest'anno",
};

/** Timezone del dispositivo, con ripiego su UTC se il browser non la espone. */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/** Valida un identificatore IANA provando a costruire un formatter. */
export function isSupportedTimeZone(timeZone: string): boolean {
  try {
    new Intl.DateTimeFormat('en-CA', { timeZone });
    return true;
  } catch {
    return false;
  }
}

/**
 * Elenco delle timezone IANA note al runtime.
 *
 * `Intl.supportedValuesOf` non è disponibile ovunque: quando manca si ripiega
 * sulla sola timezone del dispositivo, così il campo resta comunque utilizzabile.
 */
export function supportedTimeZones(): readonly string[] {
  const supported = (Intl as unknown as { supportedValuesOf?: (key: string) => readonly string[] })
    .supportedValuesOf;

  try {
    const zones = supported?.('timeZone');
    if (zones && zones.length > 0 && zones.includes(browserTimeZone())) {
      return zones;
    }
    if (zones && zones.length > 0) {
      return [browserTimeZone(), ...zones];
    }
  } catch {
    /* Runtime senza `supportedValuesOf`: si prosegue col solo fallback. */
  }

  return [browserTimeZone()];
}

/**
 * Data di oggi nella timezone indicata.
 *
 * Il profilo è la fonte autorevole del fuso: un utente in viaggio, o con il
 * browser configurato altrove, deve vedere gli stessi intervalli del proprio
 * profilo. Se la timezone non è valida si ripiega sull'ora del dispositivo.
 */
export function todayInTimeZone(timeZone: string): IsoDate {
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());

    const year = parts.find((part) => part.type === 'year')?.value;
    const month = parts.find((part) => part.type === 'month')?.value;
    const day = parts.find((part) => part.type === 'day')?.value;

    if (year && month && day) {
      return `${year}-${month}-${day}`;
    }
  } catch {
    /* Timezone non valida: si usa la data locale. */
  }

  return toIsoDate(new Date());
}

export function toIsoDate(date: Date): IsoDate {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function fromIsoDate(value: IsoDate): Date {
  const [year = 1970, month = 1, day = 1] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function endOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

/** Lunedì della settimana che contiene `date` (ISO 8601: lunedì–domenica). */
export function startOfWeek(date: Date): Date {
  const offsetFromMonday = (date.getDay() + 6) % 7;
  return addDays(date, -offsetFromMonday);
}

/** Mantiene il giorno: dal 31 o dal 30 trabocca nel mese dopo, quindi si parte dal giorno 1. */
export function addMonths(date: Date, months: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + months, date.getDate());
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/**
 * Traduce un preset in un intervallo concreto, relativo a `today`.
 *
 * `today` è una data ISO già risolta nella timezone del profilo (vedi
 * `todayInTimeZone`): qui si fa solo aritmetica di calendario, indipendente dal
 * fuso del dispositivo. I periodi correnti — settimana, mese, anno — si fermano
 * a oggi: proiettarli nel futuro mostrerebbe giorni che non sono ancora accaduti.
 */
export function resolvePeriod(
  preset: PeriodPreset,
  today: IsoDate = toIsoDate(new Date()),
): DateRange {
  const date = fromIsoDate(today);

  switch (preset) {
    case 'today':
      return { from: today, to: today };
    case 'current-week':
      return { from: toIsoDate(startOfWeek(date)), to: today };
    case 'current-month':
      return { from: toIsoDate(startOfMonth(date)), to: today };
    case 'previous-month': {
      const previous = addMonths(startOfMonth(date), -1);
      return { from: toIsoDate(startOfMonth(previous)), to: toIsoDate(endOfMonth(previous)) };
    }
    case 'last-7-days':
      return { from: toIsoDate(addDays(date, -6)), to: today };
    case 'last-30-days':
      return { from: toIsoDate(addDays(date, -29)), to: today };
    case 'last-3-months':
      return { from: toIsoDate(addMonths(startOfMonth(date), -2)), to: today };
    case 'last-6-months':
      return { from: toIsoDate(addMonths(startOfMonth(date), -5)), to: today };
    case 'current-year':
      return { from: toIsoDate(new Date(date.getFullYear(), 0, 1)), to: today };
  }
}

export function isWithinRange(date: IsoDate, range: DateRange): boolean {
  return date >= range.from && date <= range.to;
}

/** Elenco dei mesi (`yyyy-MM`) coperti dall'intervallo, in ordine crescente. */
export function monthsInRange(range: DateRange): readonly string[] {
  const months: string[] = [];
  const last = startOfMonth(fromIsoDate(range.to));
  let cursor = startOfMonth(fromIsoDate(range.from));

  while (cursor <= last && months.length < 120) {
    months.push(toIsoDate(cursor).slice(0, 7));
    cursor = addMonths(cursor, 1);
  }

  return months;
}
