/**
 * Le regole comuni a tutti i contratti HTTP.
 *
 * Ogni modulo traduce le proprie forme JSON nel proprio `*-contract.ts`, che
 * resta interno al suo `data-access`: un campo rinominato nel backend tocca un
 * file solo. Con `default-property-inclusion: non_null` il server omette i campi
 * nulli, che nelle traduzioni tornano espliciti: nel dominio l'assenza si scrive
 * `null`, mai `undefined`.
 */
export interface PageResponse<T> {
  readonly items: readonly T[];
  readonly total: number;
  readonly index: number;
  readonly size: number;
  readonly pageCount: number;
}

/**
 * Un valore fuori contratto è un errore del server, non un dato da correggere:
 * fallire subito e rumorosamente è meglio che tirare avanti con un ripiego che
 * nasconde il disallineamento fra i due lati.
 */
export function contractError(field: string, value: string): Error {
  return new Error(`Valore non previsto dal contratto per ${field}: ${value}.`);
}
