package it.walletinsight.bff.imports;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.ingestion.application.ImportConnectionService;
import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import it.walletinsight.platform.web.KebabCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Le sorgenti di importazione collegate da un utente.
 *
 * Il dominio è di `core/ingestion` — credenziali, cifratura e sorgenti vivono lì —
 * ma l'URL è una scelta di questo layer: l'identificativo dice *di chi* sono le
 * connessioni che stai chiedendo, e sparirà dal percorso quando ad assegnarlo sarà
 * l'autenticazione invece del chiamante. Che sia annidato sotto `users` non dice
 * nulla su chi possiede il dato, dice come il frontend lo interroga.
 *
 * `PUT` e non `POST`: la sorgente è l'ultimo segmento dell'URL, quindi ripetere
 * la stessa richiesta non crea un secondo collegamento ma sostituisce le
 * credenziali di quello esistente. La risposta è sempre 200 — collegare e
 * ricollegare hanno lo stesso corpo — e riprovare un onboarding interrotto è sicuro.
 *
 * Con un token nuovo la sorgente si importa subito, come all'onboarding: la
 * risposta dice già se il token funziona, e i dati persi mentre era scaduto arrivano.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/import-connections")
@Tag(name = "Import connections")
class ImportConnectionsController {

    private final ImportConnectionService service;
    private final ImportService imports;

    ImportConnectionsController(ImportConnectionService service, ImportService imports) {
        this.service = service;
        this.imports = imports;
    }

    @GetMapping
    List<ImportConnectionResponse> listConnections(@PathVariable UUID userId) {
        return service.listConnections(UserId.of(userId)).stream()
                .map(ImportConnectionResponse::from)
                .toList();
    }

    @PutMapping("/{source}")
    ImportConnectionResponse configure(
            @PathVariable UUID userId,
            @PathVariable String source,
            @Valid @RequestBody ConfigureImportConnectionRequest request) {
        UserId utente = UserId.of(userId);
        IngestionSource sorgente = toSource(source);
        service.configure(utente, sorgente, request.toToken(), request.enabledOrDefault());
        imports.importFor(utente, sorgente);

        // Riletta dopo l'import, che ha spostato segnaposto, esecuzione ed eventuale rifiuto.
        ImportConnection connection = service.listConnections(utente).stream()
                .filter(c -> c.source() == sorgente)
                .findFirst()
                .orElseThrow();
        return ImportConnectionResponse.from(connection);
    }

    @DeleteMapping("/{source}")
    ResponseEntity<Void> disconnect(@PathVariable UUID userId, @PathVariable String source) {
        service.disconnect(UserId.of(userId), toSource(source));
        return ResponseEntity.noContent().build();
    }

    /** Una sorgente sconosciuta è una richiesta malformata: `KebabCase` la segnala con un 400. */
    private static IngestionSource toSource(String source) {
        return KebabCase.to(IngestionSource.class, source);
    }
}
