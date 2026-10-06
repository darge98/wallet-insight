package it.walletinsight.bff.movements;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.movements.domain.MovementId;
import it.walletinsight.core.movements.domain.MovementKind;
import it.walletinsight.core.movements.domain.MovementSort;
import it.walletinsight.core.users.application.UserService;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * I movimenti di un utente: leggerli e correggere le tre cose che gli appartengono.
 *
 * Non si creano e non si cancellano da qui, come i conti e per la stessa
 * ragione: un movimento nasce da un import, la sua identità comprende il
 * riferimento al dato originale, e uno inventato dall'interfaccia non avrebbe
 * nulla a cui agganciarsi — oltre a falsare un saldo che è la somma di questi.
 *
 * Quello che si può fare è correggere ciò che la sorgente non sa dire bene:
 * riscrivere una descrizione che arriva come tracciato bancario, indicare chi
 * c'è dall'altra parte quando la sorgente tace, spostare il movimento in
 * un'altra categoria. L'import le scrive quando il movimento entra e poi non le
 * tocca più, quindi una correzione non viene mai sovrascritta.
 *
 * Qui la paginazione c'è, a differenza dei conti: i conti di una persona sono
 * una decina, i suoi movimenti sono 1678 dopo due anni e continuano a crescere.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/movements")
@Tag(name = "Movements")
class MovementsController {

    private final MovementService service;
    private final UserService users;

    MovementsController(MovementService service, UserService users) {
        this.service = service;
        this.users = users;
    }

    /**
     * Una pagina di movimenti con i totali dell'intero filtro.
     *
     * I filtri sono tutti facoltativi e si combinano in **and**; più valori
     * dello stesso filtro si combinano in **or**, perché `?accountId=a&accountId=b`
     * vuol dire «uno qualsiasi dei due» — un movimento sta su un conto solo, e
     * intersecarli non darebbe mai niente.
     *
     * Le due date vanno insieme: una finestra con un solo estremo non è una
     * finestra, e accettarla significherebbe decidere noi l'altro capo senza
     * dirlo — un elenco che mostra un periodo diverso da quello chiesto è peggio
     * di un errore.
     *
     * Il filtro per categoria guarda quella **mostrata**, non quella della
     * sorgente: chiedere «i movimenti in Ristoranti» e non vedere quello che ci si
     * è appena spostati a mano sarebbe incomprensibile.
     *
     * Non c'è una ricerca testuale, ed è una scelta rimandata e non una svista:
     * farla bene vuol dire un indice apposta e decidere cosa significhi
     * «trovare»; farla come un `like '%…%'` costerebbe una scansione a ogni tasto
     * premuto per dare risultati che sembrano casuali.
     */
    @GetMapping
    MovementsPageResponse listMovements(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<UUID> accountId,
            @RequestParam(required = false) List<UUID> categoryId,
            @RequestParam(required = false) List<String> type,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        UserId id = UserId.of(userId);
        MovementFilter filter = new MovementFilter(
                periodOf(from, to),
                kindsOf(type),
                mapOrEmpty(accountId, AccountId::of),
                mapOrEmpty(categoryId, CategoryId::of));
        MovementSort sort = sortOf(sortBy, sortDirection);

        Page<Movement> movimenti =
                service.listMovements(id, filter, sort, PageRequest.of(page, size));
        ZoneId fuso = zoneOf(id);
        Page<MovementResponse> pagina = new Page<>(
                movimenti.items().stream().map(movimento -> MovementResponse.from(movimento, fuso)).toList(),
                movimenti.total(),
                movimenti.index(),
                movimenti.size(),
                movimenti.pageCount());

        return new MovementsPageResponse(
                pagina, MovementTotalsResponse.from(service.totals(id, filter)));
    }

    @PatchMapping("/{movementId}")
    MovementResponse update(
            @PathVariable UUID userId,
            @PathVariable UUID movementId,
            @Valid @RequestBody UpdateMovementRequest request) {
        UserId id = UserId.of(userId);
        return MovementResponse.from(service.updateMovement(
                id,
                MovementId.of(movementId),
                request.description(),
                request.counterParty(),
                categoryOf(request.categoryId())), zoneOf(id));
    }

    private ZoneId zoneOf(UserId userId) {
        return ZoneId.of(users.getSettings(userId).timeZone());
    }

    /**
     * L'identificatore della categoria, oppure {@code null} per non toccarla.
     *
     * La stringa vuota finisce anch'essa su {@code null}, e non perché sia un
     * caso da gestire: è il valore che arriva da un movimento senza categoria —
     * che nei dati veri non esiste — e togliere una categoria non è
     * un'operazione che questo modello conosca. Meglio non fare niente che
     * inventarsi cosa volesse dire.
     */
    private static CategoryId categoryOf(String rawCategoryId) {
        if (rawCategoryId == null || rawCategoryId.isEmpty()) {
            return null;
        }
        return CategoryId.of(UUID.fromString(rawCategoryId));
    }

    /**
     * Le nature richieste, tradotte dal kebab-case del contratto.
     *
     * Un valore che non esiste è un 400 e non un filtro ignorato: chiedere
     * `?type=uscite` e ricevere tutto sembrerebbe funzionare, e sarebbe il modo
     * peggiore di scoprire un errore di battitura.
     */
    private static Set<MovementKind> kindsOf(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return raw.stream()
                .map(value -> KebabCase.to(MovementKind.class, value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static MovementSort sortOf(String sortBy, String sortDirection) {
        return new MovementSort(
                KebabCase.to(MovementSort.Field.class, sortBy),
                KebabCase.to(MovementSort.Direction.class, sortDirection));
    }

    private static <T> List<T> mapOrEmpty(
            List<UUID> raw, java.util.function.Function<UUID, T> mapper) {
        return raw == null ? List.of() : raw.stream().map(mapper).toList();
    }

    /**
     * La finestra di date, se c'è.
     *
     * Un solo estremo è una richiesta ambigua e diventa un 400 con un messaggio,
     * non una finestra chiusa d'ufficio dall'altra parte.
     */
    private static DateRange periodOf(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Il periodo va indicato con entrambi gli estremi: from e to.");
        }
        return new DateRange(from, to);
    }
}
