package it.walletinsight.core.movements.domain;

import it.walletinsight.shared.money.Money;

/**
 * Quanto è uscito verso una controparte, in un periodo: «dove spendi di più».
 *
 * La controparte è quella **mostrata** — quella scritta dall'utente se c'è,
 * altrimenti quella della sorgente — perché è il nome che si legge in elenco, e
 * una classifica che raggruppasse per un valore diverso da quello visibile
 * sembrerebbe sbagliata anche essendo giusta.
 *
 * Qui non serve nessun altro modulo: la controparte è un campo del movimento, e
 * infatti è una stringa e non un identificatore. È anche il motivo per cui questa
 * classifica ha senso solo con i dati veri: la controparte è valorizzata su 1082
 * movimenti su 1678, e i restanti finiscono fuori invece di affollare la
 * classifica con un «senza nome» che non dice dove sono andati i soldi.
 */
public record CounterPartySpending(String counterParty, Money total, long count) {
}
