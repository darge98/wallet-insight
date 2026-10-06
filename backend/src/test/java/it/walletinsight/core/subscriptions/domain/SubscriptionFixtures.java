package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;

import java.time.LocalDate;

/** Un abbonamento senza categoria né conto: per i test che non li guardano. */
public final class SubscriptionFixtures {

    private SubscriptionFixtures() {
    }

    public static Subscription abbonamento(UserId userId, String name, Money amount, Cadence cadence,
                                           LocalDate startDate, LocalDate endDate) {
        return Subscription.create(userId, definizione(name, amount, cadence, startDate, endDate));
    }

    public static SubscriptionDefinition definizione(String name, Money amount, Cadence cadence,
                                                     LocalDate startDate, LocalDate endDate) {
        return new SubscriptionDefinition(name, amount, cadence, startDate, endDate, null, null);
    }
}
