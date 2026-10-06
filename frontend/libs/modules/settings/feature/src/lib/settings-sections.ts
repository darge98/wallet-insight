export interface SettingsSection {
  readonly label: string;
  /** Percorso relativo alla rotta della sezione: chi la monta decide dove sta. */
  readonly path: string;
  readonly description: string;
  /** Annunciata ma non ancora disponibile: compare in elenco e non si apre. */
  readonly upcoming?: boolean;
}

/**
 * Le pagine delle impostazioni.
 *
 * Un elenco dichiarato e non tre link scritti a mano nel template: il menu
 * laterale, il titolo della pagina e le rotte leggono tutti da qui, e aggiungerne
 * una quarta non richiede di ricordarsi tre posti.
 *
 * `Piano` compare pur non essendo disponibile. Nasconderlo finché non esiste
 * sembrerebbe più pulito, ma un'area a pagamento che appare dal nulla il giorno
 * del rilascio è una sorpresa; dichiararla adesso dice che esisterà, e il non
 * essere cliccabile dice che oggi non c'è niente da vedere.
 */
export const SETTINGS_SECTIONS: readonly SettingsSection[] = [
  {
    label: 'Profilo',
    path: 'profilo',
    description: 'Come ti chiami, dove sei, come vuoi vedere i periodi.',
  },
  {
    label: 'Conti',
    path: 'conti',
    description: 'I conti importati, i loro nomi e le sorgenti da cui arrivano.',
  },
  {
    label: 'Categorie',
    path: 'categorie',
    description: 'Le tue categorie e da quali di BudgetBakers arrivano i movimenti.',
  },
  {
    label: 'Piano',
    path: 'piano',
    description: 'Più funzioni, più spazio, più sorgenti collegate.',
    upcoming: true,
  },
];
