/**
 * Un JWT compatto: tre segmenti base64url separati da un punto.
 *
 * Non si verifica la firma — non abbiamo la chiave e non è compito del client —
 * ma la forma sì: intercetta subito l'errore più frequente, cioè l'incollatura
 * parziale o il valore copiato dal posto sbagliato. È la stessa regola che
 * applica `PersonalToken` nel backend: qui serve solo a dirlo prima, senza un
 * giro di rete.
 */
const COMPACT_JWT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

export function isCompactJwt(value: string): boolean {
  return COMPACT_JWT.test(value.trim());
}
