package it.walletinsight.core.accounts.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificatore del conto: un UUIDv7 generato dall'applicazione.
 *
 * Nostro e non della sorgente: l'identificativo di BudgetBakers vive in
 * {@link Account#externalId()}, dove resta un dato opaco che non interpretiamo.
 * Tenerli separati è ciò che permette allo stesso conto reale di arrivare un
 * giorno da due sorgenti diverse senza doverne cambiare la chiave.
 */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId newId() {
        return new AccountId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
