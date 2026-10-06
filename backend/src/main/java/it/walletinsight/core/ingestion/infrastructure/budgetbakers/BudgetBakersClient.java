package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.walletinsight.core.ingestion.domain.RejectedCredentialsException;
import it.walletinsight.core.ingestion.domain.SourceUnavailableException;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountsPageDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoriesPageDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordsPageDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Le chiamate in lettura verso l'API Wallet: conti, categorie, movimenti.
 *
 * Il filtro `recordDate` è sempre esplicito, e non per scelta stilistica:
 * senza, l'API ne applica uno suo di tre mesi a ritroso e lo dichiara soltanto
 * in `appliedRecordDateFilters`. Un import scritto senza saperlo sembrerebbe
 * funzionare leggendo un periodo che non ha chiesto.
 *
 * La finestra si delimita da entrambi i lati: l'estremo superiore serve perché
 * i movimenti pianificati possono avere una data futura, e senza `lt`
 * finirebbero dentro il periodo richiesto.
 *
 * Il token è un parametro e non uno stato del client: ogni connessione ha il
 * suo, e lo stesso client serve tutti gli utenti.
 */
@Component
class BudgetBakersClient {

    private static final Logger log = LoggerFactory.getLogger(BudgetBakersClient.class);

    /** Ogni movimento arriva anche in euro, al cambio del suo giorno: è la valuta dei totali. */
    private static final String CONVERT_TO = "EUR";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final BudgetBakersProperties properties;

    BudgetBakersClient(RestClient budgetBakersRestClient,
                       ObjectMapper budgetBakersObjectMapper,
                       BudgetBakersProperties properties) {
        this.restClient = budgetBakersRestClient;
        this.objectMapper = budgetBakersObjectMapper;
        this.properties = properties;
    }

    /**
     * Tutti i conti dell'utente, seguendo la paginazione.
     *
     * Gli archiviati sono compresi: il filtro `archived` viene omesso di
     * proposito, perche' un conto archiviato possiede comunque i movimenti
     * storici che l'API dei record continua a restituire. Escluderlo qui
     * lascerebbe quei movimenti senza un conto a cui appoggiarsi.
     *
     * `limit` e' sempre esplicito, come il filtro data sui record e per lo stesso
     * motivo: il valore predefinito e' basso (30 per specifica, 10 nella risposta
     * osservata) e un utente con piu' conti del limite ne perderebbe una parte
     * senza che nulla lo segnali.
     */
    List<AccountDto> accounts(String token) {
        List<AccountDto> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            AccountsPageDto page = accountsPage(token, offset);
            all.addAll(page.accounts());
            log.debug("Pagina conti da offset {}: {} conti, totale dichiarato {}",
                    offset, page.accounts().size(), page.total());
            if (!page.hasMore()) {
                return all;
            }
            offset = advance(offset, page.nextOffset());
        }
    }

    AccountsPageDto accountsPage(String token, int offset) {
        String body = get(token, uriBuilder -> page(uriBuilder, "/v1/api/accounts", offset)
                .build());
        try {
            return objectMapper.readValue(body, AccountsPageDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Risposta di /v1/api/accounts non interpretabile", e);
        }
    }

    /**
     * Tutte le categorie dell'utente, seguendo la paginazione.
     *
     * `limit` esplicito come altrove, e per lo stesso motivo: il valore
     * predefinito e' basso e una parte delle categorie sparirebbe in silenzio.
     * Le categorie misurate su un utente reale sono settanta, quindi una pagina
     * sola con il `pageSize` configurato, ma il ciclo c'e' lo stesso.
     */
    List<CategoryDto> categories(String token) {
        List<CategoryDto> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            CategoriesPageDto page = categoriesPage(token, offset);
            all.addAll(page.categories());
            log.debug("Pagina categorie da offset {}: {} categorie, totale dichiarato {}",
                    offset, page.categories().size(), page.total());
            if (!page.hasMore()) {
                return all;
            }
            offset = advance(offset, page.nextOffset());
        }
    }

    CategoriesPageDto categoriesPage(String token, int offset) {
        String body = get(token, uriBuilder -> page(uriBuilder, "/v1/api/categories", offset)
                .build());
        try {
            CategoriesPageDto page = objectMapper.readValue(body, CategoriesPageDto.class);
            if (page.categories() == null) {
                // La forma della busta di questo endpoint non era documentata da nessuna
                // parte: e' stata dedotta da quelle di /accounts e /records, che sono
                // identiche fra loro. Se un giorno non combaciasse, il corpo nel
                // messaggio e' l'unica cosa che permette di correggerla senza indovinare.
                throw new IllegalStateException(
                        "Nessun campo 'categories' nella risposta: " + truncated(body));
            }
            return page;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Risposta di /v1/api/categories non interpretabile: " + truncated(body), e);
        }
    }

    /** Una pagina di un elenco, sempre con `limit` esplicito e col totale. */
    private UriBuilder page(UriBuilder uriBuilder, String path, int offset) {
        return uriBuilder
                .path(path)
                .queryParam("limit", properties.pageSize())
                .queryParam("offset", offset)
                .queryParam("withTotal", true);
    }

    /** Una sorgente che non avanza farebbe girare il ciclo all'infinito. */
    private static int advance(int offset, int nextOffset) {
        if (nextOffset <= offset) {
            throw new IllegalStateException(
                    "Paginazione ferma: nextOffset %d non supera l'offset %d".formatted(nextOffset, offset));
        }
        return nextOffset;
    }

    /** Il corpo entra nei messaggi d'errore, e i messaggi finiscono nei log: non per intero. */
    private static String truncated(String body) {
        if (body == null) {
            return "(vuota)";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }

    /**
     * Tutti i movimenti nella finestra indicata, seguendo la paginazione.
     *
     * `from` è incluso, `toExclusive` no: è la forma dei filtri `gte`/`lt`
     * dell'API, e tenerla anche qui evita di dover ricordare da che lato cade
     * l'estremo ogni volta che si legge il chiamante.
     */
    List<RecordDto> recordsBetween(String token, LocalDate from, LocalDate toExclusive) {
        List<RecordDto> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            RecordsPageDto page = recordsPage(token, from, toExclusive, offset);
            requireRequestedWindow(page, from);
            all.addAll(page.records());
            log.debug("Pagina da offset {}: {} movimenti, totale dichiarato {}",
                    offset, page.records().size(), page.total());
            if (!page.hasMore()) {
                return all;
            }
            offset = advance(offset, page.nextOffset());
        }
    }

    /**
     * La finestra letta deve essere quella chiesta: l'import cancella i movimenti in
     * sospeso che non vi trova, e una finestra ristretta in silenzio dalla sorgente
     * li farebbe sembrare spariti.
     */
    static void requireRequestedWindow(RecordsPageDto page, LocalDate from) {
        if (page.appliedRecordDateFilters() == null) {
            return;
        }
        for (String filter : page.appliedRecordDateFilters()) {
            if (filter.startsWith("gte.") && filter.length() >= 14) {
                LocalDate applied = LocalDate.parse(filter.substring(4, 14));
                if (applied.isAfter(from)) {
                    throw new IllegalStateException(
                            "BudgetBakers ha ristretto la finestra: chiesto dal %s, applicato %s"
                                    .formatted(from, filter));
                }
            }
        }
    }

    RecordsPageDto recordsPage(String token, LocalDate from, LocalDate toExclusive, int offset) {
        String body = get(token, uriBuilder -> page(uriBuilder, "/v1/api/records", offset)
                .queryParam("recordDate", "gte." + from)
                .queryParam("recordDate", "lt." + toExclusive)
                .queryParam("convertTo", CONVERT_TO)
                .build());
        try {
            return objectMapper.readValue(body, RecordsPageDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Risposta di /v1/api/records non interpretabile", e);
        }
    }

    /**
     * Usa `exchange` e non `retrieve`: quest'ultimo applica sempre un gestore
     * di errori predefinito che solleva su 4xx e 5xx, e il corpo della
     * risposta - dove BudgetBakers spiega cosa non va - andrebbe perso.
     *
     * Rete giù e timeout escono come {@link ResourceAccessException}, il cui
     * messaggio porta l'URL con i filtri: diventano un errore che dice cosa è successo.
     */
    private String get(String token, Function<UriBuilder, URI> uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange((request, response) -> {
                        String body = response.bodyTo(String.class);
                        if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                            throw new RejectedCredentialsException(
                                    "BudgetBakers ha rifiutato il token: va sostituito con uno nuovo.");
                        }
                        if (response.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                            throw troppeRichieste(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            // Il dettaglio non cita il token: finisce nei log come tutto il resto.
                            var dettaglio = new IllegalStateException("BudgetBakers ha risposto %s: %s"
                                    .formatted(response.getStatusCode(), truncated(body)));
                            if (response.getStatusCode().is5xxServerError()) {
                                throw new SourceUnavailableException(
                                        "BudgetBakers ha un problema in questo momento: riprova più tardi.",
                                        dettaglio);
                            }
                            throw dettaglio;
                        }
                        return body;
                    });
        } catch (ResourceAccessException e) {
            throw new SourceUnavailableException(
                    "BudgetBakers non è raggiungibile o non ha risposto in tempo.", e);
        }
    }

    /**
     * Il limite di BudgetBakers è di 300 richieste l'ora per token, condiviso con l'MCP:
     * un 429 non è un errore da ritentare subito ma un invito a rallentare, e chi insiste
     * rischia la revoca del token. Nessun nuovo tentativo qui: ci pensa il giro dopo.
     */
    static SourceUnavailableException troppeRichieste(String retryAfter) {
        String attesa = minutiDiAttesa(retryAfter)
                .map("riprova fra %d minuti."::formatted)
                .orElse("riprova più tardi.");
        return new SourceUnavailableException(
                "BudgetBakers ha ricevuto troppe richieste da questo token: " + attesa,
                new IllegalStateException("BudgetBakers ha risposto 429, Retry-After: " + retryAfter));
    }

    /** `Retry-After` in secondi; la forma con la data HTTP non l'abbiamo mai vista, e ricade sul generico. */
    private static Optional<Long> minutiDiAttesa(String retryAfter) {
        try {
            long secondi = Long.parseLong(retryAfter.trim());
            return Optional.of(Math.max(1, (secondi + 59) / 60));
        } catch (NumberFormatException | NullPointerException _) {
            return Optional.empty();
        }
    }
}
