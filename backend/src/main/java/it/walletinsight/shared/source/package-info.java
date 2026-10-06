/**
 * Il catalogo delle sorgenti da cui Wallet Insights importa.
 *
 * Sta in `shared` e non dentro `core/ingestion` perché non è più vocabolario di
 * un modulo solo: una connessione dichiara da dove importa, un conto ricorda da
 * dove è arrivato, e il BFF traduce l'identificativo sul filo. Tenerlo
 * nell'ingestion costringerebbe `core.accounts` a dipendere da `core.ingestion`
 * e `core.ingestion` da `core.accounts` — un ciclo che `ModularityTests`
 * rifiuterebbe, e che è il sintomo di un tipo collocato nel posto sbagliato.
 */
package it.walletinsight.shared.source;
