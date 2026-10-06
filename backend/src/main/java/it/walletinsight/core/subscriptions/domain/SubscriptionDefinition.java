package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.money.Money;

import java.time.LocalDate;

/**
 * Ciò che l'utente scrive di un abbonamento, tutto insieme: si crea e si riscrive
 * così. Le regole le verifica {@link Subscription} quando lo riceve.
 *
 * @param endDate    l'ultimo giorno in cui può cadere un addebito; {@code null} se non è disdetto
 * @param categoryId la sottocategoria in cui l'addebito cadrà; {@code null} se non scelta
 * @param accountId  il conto da cui esce; {@code null} se non scelto
 */
public record SubscriptionDefinition(
        String name,
        Money amount,
        Cadence cadence,
        LocalDate startDate,
        LocalDate endDate,
        CategoryId categoryId,
        AccountId accountId) {
}
