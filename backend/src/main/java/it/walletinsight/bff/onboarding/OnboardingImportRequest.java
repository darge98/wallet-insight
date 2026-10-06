package it.walletinsight.bff.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.platform.web.KebabCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La sorgente scelta al secondo passo dell'onboarding.
 *
 * Qui la sorgente sta nel corpo e non nell'URL — a differenza di
 * `/api/users/{id}/import-connections/{source}` — perché l'URL di questa chiamata
 * descrive la schermata, non la risorsa: è la sola differenza fra i due percorsi.
 */
public record OnboardingImportRequest(
        @NotBlank @Schema(allowableValues = {"budget-bakers", "psd2"}) String source,
        @NotBlank @Size(max = PersonalToken.MAX_LENGTH) String token) {

    public IngestionSource toSource() {
        return KebabCase.to(IngestionSource.class, source);
    }

    public PersonalToken toToken() {
        return new PersonalToken(token);
    }
}
