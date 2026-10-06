package it.walletinsight.core.ingestion.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificatore della connessione: un UUIDv7 generato dall'applicazione, come
 * per ogni entità (vedi {@link Uuids}).
 *
 * La chiave naturale sarebbe la coppia (utente, sorgente) — ed è comunque unica
 * nel database — ma un identificatore proprio resta stabile se un domani la
 * stessa sorgente potrà essere collegata più volte con account diversi.
 */
public record ImportConnectionId(UUID value) {

    public ImportConnectionId {
        Objects.requireNonNull(value, "value");
    }

    public static ImportConnectionId of(UUID value) {
        return new ImportConnectionId(value);
    }

    public static ImportConnectionId newId() {
        return new ImportConnectionId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
