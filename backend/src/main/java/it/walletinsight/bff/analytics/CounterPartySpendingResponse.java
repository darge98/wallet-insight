package it.walletinsight.bff.analytics;

/**
 * Quanto è uscito verso una controparte — una persona, un negozio — nel periodo.
 *
 * Qui non c'è niente da comporre: la controparte è un campo del movimento, non
 * un'anagrafica, e infatti è una stringa. È il motivo per cui questa classifica
 * ha senso solo sui dati veri: nei 1678 movimenti misurati la controparte è
 * valorizzata su 1082, con 429 valori distinti, e sono nomi che una persona
 * riconosce — Amazon, Conad, Piadineria.
 *
 * @param counterParty il nome mostrato: quello scritto dall'utente se c'è
 * @param totalCents   quanto è uscito, positivo, in centesimi interi
 * @param count        quanti movimenti
 */
public record CounterPartySpendingResponse(String counterParty, long totalCents, long count) {
}
