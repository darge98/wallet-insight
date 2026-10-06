/**
 * I colori fra cui si sceglie.
 *
 * Una tavolozza chiusa e non un selettore libero: otto tinte scelte per restare
 * distinguibili fra loro e leggibili su entrambi i temi. Un `<input type="color">`
 * lascerebbe scegliere il grigio del testo o il bianco dello sfondo, e il pallino
 * sparirebbe — un modo garantito di ottenere un'interfaccia peggiore per essere
 * stati più permissivi.
 *
 * Sono valori fissi e non token del design system di proposito: il token cambia
 * col tema, mentre questo colore è una scelta dell'utente salvata nel database, e
 * deve restare quella che ha scelto.
 */
export interface AccountColorOption {
  readonly value: string;
  readonly label: string;
}

export const ACCOUNT_COLORS: readonly AccountColorOption[] = [
  { value: '#e11d48', label: 'Rosso' },
  { value: '#f97316', label: 'Arancione' },
  { value: '#eab308', label: 'Giallo' },
  { value: '#16a34a', label: 'Verde' },
  { value: '#0d9488', label: 'Verde acqua' },
  { value: '#0ea5e9', label: 'Azzurro' },
  { value: '#6366f1', label: 'Indaco' },
  { value: '#a855f7', label: 'Viola' },
];
