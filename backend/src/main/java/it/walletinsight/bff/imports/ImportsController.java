package it.walletinsight.bff.imports;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * L'import chiesto a mano: una sola chiamata che aggiorna tutte le sorgenti di
 * un utente.
 *
 * Non è più il pulsante di una schermata. L'interfaccia non ha più un "aggiorna
 * ora": il primo import parte dall'onboarding, quelli dopo li fanno le
 * schedulazioni delle sorgenti, e questo endpoint resta il modo di farne partire uno
 * adesso — per un'assistenza, per una misura, per il giro schedulato che non è
 * andato. La logica è comunque una sola, in `ImportService`: chi chiama non
 * cambia cosa succede.
 *
 * `POST` e non `PUT`: non si sta mandando lo stato di una risorsa, si sta
 * chiedendo di *eseguire* qualcosa. Ripeterlo è comunque sicuro — l'import è
 * idempotente per costruzione, i conti si riconoscono dal riferimento
 * all'originale e il segnaposto non arretra — ma la risposta cambia, perché
 * cambia cosa è stato letto.
 *
 * È sincrono. Misurato sui dati reali: ~3 secondi per un import completo (1677
 * movimenti, dieci chiamate alla sorgente), mezzo secondo per uno incrementale.
 * A questi tempi un 202 con polling sarebbe macchinario prematuro; quando i
 * numeri lo chiederanno, il corpo della risposta è già la forma giusta da
 * restituire in differita.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/imports")
@Tag(name = "Imports")
class ImportsController {

    private final ImportService imports;

    ImportsController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping
    List<ImportOutcomeResponse> importNow(@PathVariable UUID userId) {
        return imports.importFor(UserId.of(userId)).stream()
                .map(ImportOutcomeResponse::from)
                .toList();
    }
}
