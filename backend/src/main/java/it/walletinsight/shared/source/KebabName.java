package it.walletinsight.shared.source;

import java.util.Locale;

/**
 * Il nome pubblico di una sorgente, quello che il frontend conosce
 * (`budget-bakers`): serve ai messaggi d'errore del dominio, che citano la
 * sorgente con lo stesso identificativo usato nelle richieste.
 *
 * Duplica di proposito la sola direzione enum → kebab di `platform/web/KebabCase`:
 * il dominio non deve conoscere il layer web.
 */
public final class KebabName {

    private KebabName() {
    }

    public static String of(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
