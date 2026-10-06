import { addDays, fromIsoDate, toIsoDate } from '@wallet/shared-domain';

/**
 * La riga della topbar sull'ultimo aggiornamento dei dati.
 *
 * L'ora è quella in cui il backend ha importato davvero, non quella in cui il browser
 * ha scaricato i conti: un import notturno fallito non deve leggersi «aggiornati
 * adesso». Giorno e ora sono nel fuso del profilo.
 */
export function lastRunLabel(
  lastRunAt: string | null,
  timeZone: string,
  now: Date = new Date(),
): string {
  if (lastRunAt === null) {
    return 'Dati non ancora importati';
  }
  const istante = new Date(lastRunAt);
  const ora = new Intl.DateTimeFormat('it-IT', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone,
  }).format(istante);
  const giorno = dayIn(istante, timeZone);
  const oggi = dayIn(now, timeZone);

  if (giorno === oggi) {
    return `Dati aggiornati oggi alle ${ora}`;
  }
  if (giorno === toIsoDate(addDays(fromIsoDate(oggi), -1))) {
    return `Dati aggiornati ieri alle ${ora}`;
  }
  const data = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long', timeZone }).format(
    istante,
  );
  return `Dati aggiornati il ${data} alle ${ora}`;
}

/** Il giorno `yyyy-MM-dd` di un istante nel fuso indicato. */
function dayIn(istante: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone }).format(istante);
}
