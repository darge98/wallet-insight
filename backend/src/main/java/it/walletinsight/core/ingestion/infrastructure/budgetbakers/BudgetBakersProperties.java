package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Coordinate di accesso all'API Wallet di BudgetBakers.
 *
 * Il token non sta qui: e' personale, cambia da utente a utente e arriva dalla
 * connessione che il backend restituisce, cifrato a riposo. Un token in
 * configurazione varrebbe per tutti, e non ha senso in un import che gira su
 * piu' utenti.
 *
 * Nemmeno il periodo sta qui, ed e' una cosa che era stata messa e poi tolta:
 * quando l'import parte non lo decide chi configura l'ambiente. Dal secondo giro
 * in poi lo dice il segnaposto della connessione, e il primo giro prende tutto
 * (vedi {@code BudgetBakersSource.DALL_ORIGINE}). Una data in configurazione
 * sembrava una manopola e non lo era: l'unico valore che non rompe i saldi e'
 * "da sempre", quindi non c'era niente da scegliere.
 *
 * @param baseUrl  radice dell'API
 * @param pageSize quanti movimenti chiedere per pagina, massimo 200
 */
@ConfigurationProperties("margine.budgetbakers")
record BudgetBakersProperties(
        @DefaultValue("https://rest.budgetbakers.com/wallet") String baseUrl,
        @DefaultValue("200") int pageSize) {
}
