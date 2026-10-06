package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.imports.ImportConnectionResponse;
import it.walletinsight.bff.users.UserResponse;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.users.domain.User;

import java.util.List;

/**
 * Cosa esiste dopo il primo accesso: il profilo e le sorgenti collegate.
 *
 * Restituire entrambi risparmia al frontend le due GET che farebbe subito dopo, e
 * riusa le stesse forme JSON del resto dell'API: l'onboarding è una scorciatoia,
 * non un contratto parallelo.
 */
public record OnboardingResponse(UserResponse user, List<ImportConnectionResponse> connections) {

    public static OnboardingResponse from(User user, List<ImportConnection> connections) {
        return new OnboardingResponse(
                UserResponse.from(user),
                connections.stream().map(ImportConnectionResponse::from).toList());
    }
}
