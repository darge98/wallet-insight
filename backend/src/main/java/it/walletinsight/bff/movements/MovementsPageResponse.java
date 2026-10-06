package it.walletinsight.bff.movements;

import it.walletinsight.shared.page.Page;

/**
 * Una pagina di movimenti con i totali dell'intero filtro.
 *
 * Due cose in una risposta sola, ed è il motivo per cui il BFF esiste: la
 * schermata mostra i totali sopra e l'elenco sotto, e sono due interrogazioni
 * diverse sugli stessi criteri. Separarle in due endpoint significherebbe due
 * richieste che possono raccontare due momenti diversi — filtri cambiati nel
 * frattempo, un import finito in mezzo — e un riepilogo che non corrisponde alle
 * righe sotto di sé.
 *
 * `page` resta la `Page<T>` di sempre, annidata invece che allargata: aggiungere
 * i totali *dentro* la pagina avrebbe dato a quel tipo un campo che per ogni
 * altro elenco non significa niente.
 */
public record MovementsPageResponse(Page<MovementResponse> page, MovementTotalsResponse totals) {
}
