package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.users.CreateUserRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo del primo accesso: i due passi della schermata, in una richiesta sola.
 *
 * `profile` è esattamente il corpo di `POST /api/users` — l'onboarding non
 * inventa una seconda forma per lo stesso dato — e `importConnection` è
 * facoltativo: rimandare la scelta della sorgente è una decisione legittima, non
 * un errore, e si esprime omettendo il campo.
 */
public record OnboardingRequest(
        @NotNull @Valid CreateUserRequest profile,
        @Valid OnboardingImportRequest importConnection) {
}
