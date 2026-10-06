package it.walletinsight.core.accounts.domain;

/**
 * Natura di un conto, normalizzata per Wallet Insights.
 *
 * L'elenco nasce dai tipi che BudgetBakers dichiara davvero — è la prima sorgente
 * reale, e il dominio finanziario va ricostruito da lì — ma i nomi sono nostri e
 * restano validi per le sorgenti che verranno: un conto corrente è un conto
 * corrente comunque lo chiami la banca che lo espone.
 *
 * La traduzione dai valori della sorgente avviene nell'ingestion, non qui: questo
 * modulo non sa che BudgetBakers esiste.
 *
 * {@code UNKNOWN} esiste perché una sorgente può introdurre un tipo nuovo senza
 * avvisare. Un conto con un tipo che non riconosciamo entra comunque — perderlo
 * significherebbe perdere anche tutti i suoi movimenti — e resta visibile come
 * anomalia da guardare.
 */
public enum AccountKind {
    GENERAL,
    CASH,
    CURRENT_ACCOUNT,
    CREDIT_CARD,
    SAVINGS,
    INSURANCE,
    INVESTMENT,
    LOAN,
    MORTGAGE,
    OVERDRAFT,
    BONUS,
    UNKNOWN
}
