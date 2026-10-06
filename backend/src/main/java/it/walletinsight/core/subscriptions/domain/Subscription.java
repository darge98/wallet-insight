package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Una spesa che si rinnova da sola: dal primo addebito in poi, a ogni
 * {@link Cadence}, fino all'eventuale {@code endDate} compresa.
 *
 * @param endDate    l'ultimo giorno in cui può cadere un addebito; {@code null} se
 *                   non è stato disdetto
 * @param categoryId facoltativa, come {@code accountId}: servono a ritrovare
 *                   l'addebito fra i movimenti, non a definire l'abbonamento
 */
public record Subscription(
        SubscriptionId id,
        UserId userId,
        String name,
        Money amount,
        Cadence cadence,
        LocalDate startDate,
        LocalDate endDate,
        CategoryId categoryId,
        AccountId accountId) {

    public static final int MAX_NAME_LENGTH = 80;

    public Subscription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        name = requireName(name);
        Objects.requireNonNull(amount, "amount");
        if (amount.amount() <= 0) {
            throw new IllegalArgumentException("L'importo di un abbonamento deve essere maggiore di zero.");
        }
        Objects.requireNonNull(cadence, "cadence");
        Objects.requireNonNull(startDate, "startDate");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fine di un abbonamento non può precedere il primo addebito.");
        }
    }

    public static Subscription create(UserId userId, SubscriptionDefinition definition) {
        return of(SubscriptionId.newId(), userId, definition);
    }

    /** Lo stesso abbonamento con la definizione nuova: identità e proprietario restano. */
    public Subscription redefinedAs(SubscriptionDefinition definition) {
        return of(id, userId, definition);
    }

    public SubscriptionDefinition definition() {
        return new SubscriptionDefinition(name, amount, cadence, startDate, endDate, categoryId, accountId);
    }

    /** Lo stesso abbonamento in un'altra categoria: serve quando quella di prima viene unita. */
    public Subscription movedTo(CategoryId newCategoryId) {
        return new Subscription(id, userId, name, amount, cadence, startDate, endDate, newCategoryId, accountId);
    }

    /** Attivo finché non è finito: uno che deve ancora partire lo è già. */
    public boolean isActiveOn(LocalDate today) {
        return endDate == null || !endDate.isBefore(today);
    }

    /** Il primo addebito da {@code today} compreso, vuoto se non ce ne sono più. */
    public Optional<LocalDate> nextChargeOn(LocalDate today) {
        LocalDate prossimo = cadence.occurrence(startDate, cadence.firstIndexOnOrAfter(startDate, today));
        return isWithinEnd(prossimo) ? Optional.of(prossimo) : Optional.empty();
    }

    /** Gli addebiti fra {@code from} e {@code to}, estremi compresi. */
    public List<LocalDate> chargesBetween(LocalDate from, LocalDate to) {
        List<LocalDate> addebiti = new ArrayList<>();
        for (long indice = cadence.firstIndexOnOrAfter(startDate, from); ; indice++) {
            LocalDate addebito = cadence.occurrence(startDate, indice);
            if (addebito.isAfter(to) || !isWithinEnd(addebito)) {
                return addebiti;
            }
            addebiti.add(addebito);
        }
    }

    public Money monthlyCost() {
        return cadence.monthly(amount);
    }

    public Money yearlyCost() {
        return cadence.yearly(amount);
    }

    private static Subscription of(SubscriptionId id, UserId userId, SubscriptionDefinition definition) {
        return new Subscription(id, userId, definition.name(), definition.amount(), definition.cadence(),
                definition.startDate(), definition.endDate(), definition.categoryId(), definition.accountId());
    }

    private boolean isWithinEnd(LocalDate date) {
        return endDate == null || !date.isAfter(endDate);
    }

    private static String requireName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Il nome dell'abbonamento è obbligatorio.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Il nome dell'abbonamento supera i %d caratteri.".formatted(MAX_NAME_LENGTH));
        }
        return trimmed;
    }
}
