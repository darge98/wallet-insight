/** Metadati statici dell'applicazione, usati da shell e pagine. */
export const APP_INFO = {
  name: 'Wallet Insights',
  /** Wordmark in minuscolo, come nel concept. */
  wordmark: 'wallet insights.',
  tagline: 'Le tue finanze, con chiarezza.',
  /**
   * Origine dei dati attualmente collegata.
   *
   * Ora arriva tutto dal backend: i budget dimostrativi erano l'ultimo numero
   * inventato a schermo e sono usciti dalla Panoramica insieme al loro pannello.
   * La riga va tenuta onesta in entrambe le direzioni — prometteva dati finti
   * che non ci sono più, ed è un'etichetta sbagliata quanto quella opposta.
   */
  dataSource: 'Dati reali dalle tue sorgenti',
  owner: 'Darge',
  plan: 'Personale / Piano Plus',
  version: '0.3.0',
} as const;
