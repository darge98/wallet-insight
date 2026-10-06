package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

public record SubscriptionId(UUID value) {

    public SubscriptionId {
        Objects.requireNonNull(value, "value");
    }

    public static SubscriptionId of(UUID value) {
        return new SubscriptionId(value);
    }

    public static SubscriptionId newId() {
        return new SubscriptionId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
