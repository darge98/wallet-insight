package it.walletinsight.core.categories.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

public record SourceCategoryId(UUID value) {

    public SourceCategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static SourceCategoryId of(UUID value) {
        return new SourceCategoryId(value);
    }

    public static SourceCategoryId newId() {
        return new SourceCategoryId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
