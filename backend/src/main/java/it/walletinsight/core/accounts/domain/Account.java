package it.walletinsight.core.accounts.domain;

import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Un conto dell'utente, nato da una sorgente di importazione.
 *
 * Porta due nomi di proprietà diversa. {@code sourceName} è come il conto si
 * chiama nella sorgente e viene riscritto a ogni import; {@code name} è come si
 * chiama in Wallet Insights ed è dell'utente. Finché coincidono l'utente non è
 * intervenuto e il nome segue la sorgente; appena divergono, un import non tocca
 * più {@code name} — vedi {@link #refreshedFrom}. La regola si deduce dai due
 * valori invece che da un flag, così non esiste uno stato "rinominato" che possa
 * restare disallineato.
 *
 * {@code color} è l'altra cosa che appartiene a chi guarda, e segue la stessa
 * regola del nome: nessun import lo tocca. È {@code null} finché l'utente non ne
 * sceglie uno — un colore assegnato d'ufficio sarebbe indistinguibile da una
 * scelta vera, e non ci sarebbe modo di sapere se qualcuno l'ha deciso davvero.
 *
 * {@code initialBalance} è l'unico saldo memorizzato. Il saldo corrente non è un
 * attributo del conto ma il risultato di una somma sui movimenti, e va chiesto a
 * PostgreSQL quando serve: una colonna con l'ultimo saldo noto invecchierebbe fra
 * un import e l'altro, lasciando due verità in disaccordo.
 */
public record Account(
        AccountId id,
        UserId userId,
        IngestionSource source,
        String externalId,
        String name,
        String sourceName,
        AccountKind kind,
        CurrencyCode currency,
        Money initialBalance,
        String iban,
        String color,
        boolean archived,
        boolean excludedFromStats) {

    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_EXTERNAL_ID_LENGTH = 120;
    /** Il limite dichiarato dalla specifica di BudgetBakers per `bankAccountNumber`. */
    public static final int MAX_IBAN_LENGTH = 80;
    /** `#rrggbb`: la forma che un selettore di colore produce e che il CSS legge com'è. */
    public static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(initialBalance, "initialBalance");
        externalId = requireText(externalId, "externalId", MAX_EXTERNAL_ID_LENGTH);
        name = requireText(name, "name", MAX_NAME_LENGTH);
        sourceName = requireText(sourceName, "sourceName", MAX_NAME_LENGTH);
        iban = optionalText(iban, "iban", MAX_IBAN_LENGTH);
        color = normalizeColor(color);
        if (initialBalance.currency() != currency) {
            throw new IllegalArgumentException(
                    "Il saldo iniziale è in %s ma il conto è in %s."
                            .formatted(initialBalance.currency(), currency));
        }
    }

    /**
     * Un conto appena importato: identificatore nuovo, e il nome della sorgente
     * vale anche come nome in Wallet Insights finché l'utente non decide altrimenti.
     */
    public static Account imported(
            UserId userId,
            IngestionSource source,
            String externalId,
            String sourceName,
            AccountKind kind,
            CurrencyCode currency,
            Money initialBalance,
            String iban,
            boolean archived,
            boolean excludedFromStats) {
        // Nessun colore: la sorgente non ne dichiara, e sceglierlo è dell'utente.
        return new Account(AccountId.newId(), userId, source, externalId,
                sourceName, sourceName, kind, currency, initialBalance, iban,
                null, archived, excludedFromStats);
    }

    /**
     * Riallinea il conto a ciò che la sorgente dice oggi, conservando identità e
     * scelte dell'utente.
     *
     * Il nome è l'unico campo conteso: se l'utente l'ha cambiato — cioè se
     * {@code name} e {@code sourceName} sono già diversi — la sorgente aggiorna
     * solo il proprio, e la rinomina sopravvive a tutti gli import successivi.
     * Se invece non l'ha mai toccato, seguire la sorgente è ciò che si aspetta:
     * ha rinominato il conto là, e se lo ritrova rinominato qui.
     *
     * Tutto il resto (tipo, valuta, saldo iniziale, IBAN, archiviazione) è un
     * fatto della sorgente e viene sempre riscritto. Il colore no: è dell'utente
     * come il nome, e la sorgente non ne conosce nemmeno l'esistenza.
     */
    public Account refreshedFrom(Account fromSource) {
        boolean rinominatoDallUtente = !name.equals(sourceName);
        return new Account(
                id,
                userId,
                source,
                externalId,
                rinominatoDallUtente ? name : fromSource.sourceName,
                fromSource.sourceName,
                fromSource.kind,
                fromSource.currency,
                fromSource.initialBalance,
                fromSource.iban,
                color,
                fromSource.archived,
                fromSource.excludedFromStats);
    }

    /** Copia con il solo nome di Wallet Insights sostituito: è il gesto dell'utente che rinomina. */
    public Account renamedTo(String newName) {
        return new Account(id, userId, source, externalId, newName, sourceName,
                kind, currency, initialBalance, iban, color, archived, excludedFromStats);
    }

    /** Copia con un altro colore: l'altro gesto che l'utente può fare su un conto. */
    public Account coloredWith(String newColor) {
        return new Account(id, userId, source, externalId, name, sourceName,
                kind, currency, initialBalance, iban, newColor, archived, excludedFromStats);
    }

    /** `true` se il nome mostrato non è più quello della sorgente. */
    public boolean renamedByUser() {
        return !name.equals(sourceName);
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Il campo %s è obbligatorio.".formatted(field));
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Il campo %s supera i %d caratteri.".formatted(field, maxLength));
        }
        return trimmed;
    }

    /** Un testo opzionale: bianco o vuoto diventa `null`, mai una stringa vuota persistita. */
    /**
     * Normalizza il colore a `#rrggbb` minuscolo, o a `null` se non ce n'è uno.
     *
     * Minuscolo perché `#FF8800` e `#ff8800` sono lo stesso colore: tenerli
     * distinti farebbe risultare "cambiato" un conto che nessuno ha toccato, e un
     * import a vuoto riscriverebbe righe per una differenza che non esiste.
     */
    private static String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!COLOR_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Il colore '%s' non è nel formato #rrggbb.".formatted(trimmed));
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Il campo %s supera i %d caratteri.".formatted(field, maxLength));
        }
        return trimmed;
    }
}
