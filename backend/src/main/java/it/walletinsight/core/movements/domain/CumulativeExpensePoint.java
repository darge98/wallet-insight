package it.walletinsight.core.movements.domain;

import it.walletinsight.shared.money.Money;

import java.time.LocalDate;

/**
 * Spesa cumulata a un certo giorno del periodo.
 *
 * Risponde a «a che ritmo sto consumando il mese»: la curva sale sempre, e la
 * sua pendenza è il ritmo di spesa. Il valore è quindi progressivo, non la spesa
 * del singolo giorno — e a farlo progredire è PostgreSQL con una finestra, non
 * un ciclo che somma nel client.
 *
 * Ci sono solo i giorni in cui qualcosa è uscito: i giorni vuoti non aggiungono
 * informazione a una curva cumulativa, che fra due punti resta piatta da sé.
 */
public record CumulativeExpensePoint(LocalDate date, Money cumulative) {
}
