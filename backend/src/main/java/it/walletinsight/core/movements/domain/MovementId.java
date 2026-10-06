package it.walletinsight.core.movements.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificatore del movimento: un UUIDv7 generato dall'applicazione.
 *
 * Sequenziale nel tempo, quindi gli inserimenti di un import cadono in coda
 * all'indice invece di sparpagliarsi: su decine di migliaia di righe è la
 * differenza fra un B-tree compatto e uno pieno di buchi.
 */
public record MovementId(UUID value) {

    public MovementId {
        Objects.requireNonNull(value, "value");
    }

    public static MovementId of(UUID value) {
        return new MovementId(value);
    }

    public static MovementId newId() {
        return new MovementId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
