package it.walletinsight.core.categories.domain;

import it.walletinsight.core.users.domain.UserId;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Una categoria di Wallet Insights: una macro se non ha {@code parentId}, altrimenti una
 * sottocategoria. I movimenti si classificano solo nelle sottocategorie; una macro
 * raccoglie le sue.
 *
 * {@code templateKey} dice da quale voce dell'elenco di base è nata, ed è ciò con
 * cui l'import ritrova la categoria anche dopo che l'utente l'ha rinominata.
 * Vale {@code null} per quelle create a mano.
 */
public record Category(
        CategoryId id,
        UserId userId,
        CategoryId parentId,
        String name,
        String color,
        String templateKey) {

    public static final int MAX_NAME_LENGTH = 80;
    /** `#rrggbb`: la forma che un selettore di colore produce e che il CSS legge com'è. */
    public static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        name = requireName(name);
        color = normalizeColor(color);
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("Una categoria non può stare dentro se stessa.");
        }
    }

    public static Category create(UserId userId, CategoryId parentId, String name) {
        return new Category(CategoryId.newId(), userId, parentId, name, null, null);
    }

    public boolean isMacro() {
        return parentId == null;
    }

    public Category renamedTo(String newName) {
        return new Category(id, userId, parentId, newName, color, templateKey);
    }

    /** `null` toglie il colore. */
    public Category coloredWith(String newColor) {
        return new Category(id, userId, parentId, name, newColor, templateKey);
    }

    public Category movedUnder(CategoryId newParent) {
        return new Category(id, userId, newParent, name, color, templateKey);
    }

    private static String requireName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Il nome della categoria è obbligatorio.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Il nome della categoria supera i %d caratteri.".formatted(MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    /** Minuscolo: `#FF8800` e `#ff8800` sono lo stesso colore. */
    static String normalizeColor(String value) {
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
}
