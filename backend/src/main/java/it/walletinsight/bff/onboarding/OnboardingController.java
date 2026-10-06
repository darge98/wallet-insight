package it.walletinsight.bff.onboarding;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.bff.onboarding.OnboardingService.OnboardingResult;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.platform.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Il primo accesso in una chiamata sola.
 *
 * La risorsa rispecchia la schermata e non un dominio: è ciò che distingue questo
 * endpoint da `POST /api/users` più `PUT .../import-connections/{source}`, che
 * restano disponibili e fanno le stesse due cose separatamente. Qui l'ordine — il
 * profilo prima, la sorgente dopo, perché una connessione ha bisogno di un utente
 * — lo decide il backend, dove è verificabile, invece del browser.
 */
@RestController
@RequestMapping(ApiPaths.API + "/onboarding")
@Tag(name = "Onboarding")
class OnboardingController {

    private final OnboardingService service;

    OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<OnboardingResponse> completeOnboarding(
            @Valid @RequestBody OnboardingRequest request) {
        OnboardingImportRequest choice = request.importConnection();
        IngestionSource source = choice == null ? null : choice.toSource();
        PersonalToken token = choice == null ? null : choice.toToken();

        OnboardingResult result = service.complete(
                request.profile().firstName(),
                request.profile().lastName(),
                request.profile().email(),
                request.profile().toSettings(),
                source,
                token);

        // La risorsa creata è l'utente: l'onboarding è il gesto, non una cosa che esiste.
        URI location = URI.create(ApiPaths.API + "/users/" + result.user().id());
        return ResponseEntity.created(location)
                .body(OnboardingResponse.from(result.user(), result.connections()));
    }
}
