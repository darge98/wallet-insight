/** «1.500», «1.250,50»: il punto separa le migliaia, la virgola i decimali. */
const GROUPED = /^\d{1,3}(\.\d{3})*(,\d{1,2})?$/;

/** «12.5», «300,50»: un solo separatore, ed è quello dei decimali. */
const PLAIN = /^\d+([.,]\d{1,2})?$/;

/**
 * Un importo scritto da una persona, in centesimi: «300», «300,50», «1.500», «1.250,5».
 * `null` se non è un importo positivo.
 */
export function parseAmount(raw: string): number | null {
  const text = raw.trim().replaceAll(/\s|€/g, '');
  let normalized: string;
  if (GROUPED.test(text)) {
    normalized = text.replaceAll('.', '').replace(',', '.');
  } else if (PLAIN.test(text)) {
    normalized = text.replace(',', '.');
  } else {
    return null;
  }
  const cents = Math.round(Number(normalized) * 100);
  return cents > 0 ? cents : null;
}

/** L'importo in centesimi come lo si riscrive nel campo: «300» o «300,50». */
export function amountInput(cents: number): string {
  const euros = Math.floor(cents / 100);
  const rest = cents % 100;
  return rest === 0 ? String(euros) : `${euros},${String(rest).padStart(2, '0')}`;
}
