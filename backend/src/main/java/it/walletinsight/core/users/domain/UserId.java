package it.walletinsight.core.users.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificatore dell'utente: un UUIDv7 generato dall'applicazione.
 *
 * Avvolge {@link UUID} e non {@link String} perché è un identificatore nostro,
 * non il codice opaco di una sorgente esterna: il tipo garantisce che non possa
 * entrarci un valore arbitrario. È sequenziale nel tempo (vedi {@link Uuids}).
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId newId() {
        return new UserId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
