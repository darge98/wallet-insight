package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;

import java.util.List;
import java.util.Optional;

/** Porta di persistenza degli abbonamenti. */
public interface SubscriptionRepository {

    /** Gli abbonamenti di un utente in ordine di nome. */
    List<Subscription> findByUser(UserId userId);

    Optional<Subscription> findById(UserId userId, SubscriptionId id);

    void insert(Subscription subscription);

    void update(Subscription subscription);

    void delete(UserId userId, SubscriptionId id);

    /** Sposta in {@code into} gli abbonamenti che stavano in {@code from}. */
    void reassignCategory(UserId userId, CategoryId from, CategoryId into);
}
