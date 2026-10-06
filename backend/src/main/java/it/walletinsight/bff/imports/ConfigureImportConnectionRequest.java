package it.walletinsight.bff.imports;

import it.walletinsight.core.ingestion.domain.PersonalToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo della configurazione di una sorgente: il solo segreto.
 *
 * La sorgente sta nell'URL, non nel corpo. `enabled` è opzionale e vale `true`
 * quando manca: chi collega una sorgente la vuole attiva, e il campo serve alle
 * impostazioni, dove una sorgente si mette in pausa senza scollegarla.
 */
public record ConfigureImportConnectionRequest(
        @NotBlank @Size(max = PersonalToken.MAX_LENGTH) String token,
        Boolean enabled) {

    public PersonalToken toToken() {
        return new PersonalToken(token);
    }

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
