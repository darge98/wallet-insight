export interface NavigationItem {
  readonly label: string;
  readonly path: string;
  /** Sezione annunciata ma non ancora disponibile. */
  readonly upcoming?: boolean;
}

/**
 * Voci del menu principale.
 *
 * Senza icone, come nel concept: l'etichetta basta e la colonna resta quieta.
 *
 * L'Assistente AI è fuori dal menu finché non serve: la sua rotta annunciata
 * resta in `upcomingRoutes`, raggiungibile per URL.
 */
export const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  { label: 'Panoramica', path: '/panoramica' },
  { label: 'Movimenti', path: '/movimenti' },
  { label: 'Budget', path: '/budget' },
  { label: 'Abbonamenti', path: '/abbonamenti' },
];

/**
 * Le impostazioni, che **non** stanno nel menu principale.
 *
 * Quel menu elenca i posti dove si guardano i propri soldi; le impostazioni sono
 * un'altra cosa — riguardano chi sei e come l'applicazione è configurata — e
 * mescolarle alle sezioni le farebbe sembrare un'area di consultazione come le
 * altre. Vivono in fondo alla sidebar, sul blocco che porta già il tuo nome e il
 * tuo piano: è lì che si va a cercare «le mie cose».
 */
export const ACCOUNT_NAVIGATION: NavigationItem = {
  label: 'Impostazioni',
  path: '/impostazioni',
};
