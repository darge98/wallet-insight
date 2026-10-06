package it.walletinsight.platform.web;

import java.util.Locale;

/**
 * Traduzione fra le costanti Java e i valori kebab-case del contratto HTTP.
 *
 * Il frontend usa `credit-card`, `direct-debit`, `digital-wallet`; Java usa
 * `CREDIT_CARD`. La conversione sta qui e i mapper dei DTO la richiamano, invece
 * di essere un comportamento globale di Jackson: così vale solo per i nostri
 * tipi e si verifica con un test unitario.
 */
public final class KebabCase {

    private KebabCase() {
    }

    public static String from(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static <E extends Enum<E>> E to(Class<E> type, String raw) {
        String constant = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, constant);
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException(
                    "Valore non ammesso per %s: %s.".formatted(type.getSimpleName(), raw), cause);
        }
    }
}
