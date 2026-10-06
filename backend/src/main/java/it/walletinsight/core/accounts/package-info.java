/**
 * I conti su cui i movimenti si appoggiano: il primo pezzo del dominio finanziario.
 *
 * Un conto di Wallet Insights non è il conto della sorgente, ne è la controparte. Porta
 * con sé il riferimento all'originale (`source` + `externalId`) e da quel momento
 * vive di vita propria: l'utente può rinominarlo senza perdere l'aggancio, e un
 * import successivo lo ritrova invece di crearne un secondo.
 *
 * Dipende solo da `core.users::domain`: un conto appartiene a un utente. Sapere
 * *da dove* è arrivato è parte della sua identità, ma il catalogo delle sorgenti
 * vive in `shared/source` — condiviso, perché lo usano sia i conti sia
 * l'ingestione, e tenerlo in uno dei due creerebbe un ciclo fra i moduli.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Accounts",
        allowedDependencies = "core.users::domain")
package it.walletinsight.core.accounts;
