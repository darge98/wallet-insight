package it.walletinsight.shared.source;

/**
 * Come una sorgente autentica le proprie chiamate.
 *
 * `PERSONAL_TOKEN` è un segreto che l'utente incolla a mano (BudgetBakers emette
 * un JWT personale dalle impostazioni dell'account): lo custodiamo noi, cifrato.
 * `OAUTH` è un consenso delegato che nasce da un giro di redirect e non si
 * configura con un campo di testo, quindi non passa da questa API.
 */
public enum ImportCredentialKind {
    PERSONAL_TOKEN, OAUTH
}
