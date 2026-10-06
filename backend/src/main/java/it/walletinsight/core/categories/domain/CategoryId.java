package it.walletinsight.core.categories.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

/** Identificatore della categoria: un UUIDv7 generato dall'applicazione, come ogni id di Wallet Insights. */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    public static CategoryId newId() {
        return new CategoryId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
