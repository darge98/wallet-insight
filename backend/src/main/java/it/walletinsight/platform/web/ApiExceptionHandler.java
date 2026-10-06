package it.walletinsight.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Ogni errore esce come ProblemDetail (RFC 9457).
 *
 * `HIGHEST_PRECEDENCE` perché Boot registra un proprio `ProblemDetailsExceptionHandler`
 * senza ordine (quindi ultimo): per le eccezioni gestite da entrambi — come
 * `MethodArgumentNotValidException` — deve vincere questa advice, che parla la stessa
 * lingua del resto dell'API. Le eccezioni senza handler qui cadono comunque su quella.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://margine.app/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail nonTrovato(ResourceNotFoundException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Risorsa non trovata");
        problem.setType(URI.create(TYPE_PREFIX + KebabCase.from(Kind.RESOURCE_NOT_FOUND)));
        problem.setProperty("resourceType", exception.resourceType());
        problem.setProperty("resourceId", exception.id());
        return problem;
    }

    /** Fallimento della Bean Validation sui corpi delle richieste (`@Valid`). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validazioneFallita(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Alcuni campi della richiesta non sono validi.");
        problem.setTitle("Richiesta non valida");
        problem.setType(URI.create(TYPE_PREFIX + KebabCase.from(Kind.INVALID_REQUEST)));
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Violazione di un vincolo di unicità (es. email già registrata). Il dettaglio è
     * volutamente generico: quale colonna ha causato il conflitto è un'informazione
     * sullo schema che non serve al client.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail conflitto(DuplicateKeyException exception) {
        log.debug("Violato un vincolo di unicità.", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Esiste già una risorsa con questi vincoli di unicità.");
        problem.setTitle("Conflitto");
        problem.setType(URI.create(TYPE_PREFIX + KebabCase.from(Kind.CONFLICT)));
        return problem;
    }

    /**
     * Un'operazione che lo stato attuale della risorsa non ammette: la richiesta è
     * scritta bene, è il momento a essere sbagliato.
     *
     * È un 409 e non un 400 perché il client non ha nulla da correggere nel corpo —
     * riprovare con gli stessi dati può benissimo funzionare più tardi, quando la
     * risorsa sarà in un altro stato. Come per {@link IllegalArgumentException} il
     * messaggio finisce testualmente nella risposta: chi la solleva lo tratti come
     * pubblico.
     */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail statoNonAmmesso(IllegalStateException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Operazione non ammessa");
        problem.setType(URI.create(TYPE_PREFIX + KebabCase.from(Kind.CONFLICT)));
        return problem;
    }

    /**
     * Contratto pubblico: questo handler è application-wide, non specifico di un modulo.
     * Ogni {@link IllegalArgumentException} sollevata in qualunque punto del codice arriva qui
     * e il suo {@code getMessage()} finisce testualmente nel body della risposta al client.
     * Chi solleva questa eccezione deve quindi trattare il proprio messaggio come pubblico e
     * sicuro da esporre — mai dati sensibili o dettagli interni.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail richiestaNonValida(IllegalArgumentException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Richiesta non valida");
        problem.setType(URI.create(TYPE_PREFIX + KebabCase.from(Kind.INVALID_REQUEST)));
        return problem;
    }

    /**
     * Una riga corrotta è un errore di dati, non una richiesta malformata: a differenza del
     * ramo {@link IllegalArgumentException}, il messaggio (che contiene il valore letto dal
     * database) finisce nel log e MAI nel corpo della risposta.
     */
    @ExceptionHandler(CorruptedDataException.class)
    ProblemDetail datiCorrotti(CorruptedDataException exception) {
        log.error("Dato corrotto nel database.", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Si è verificato un errore interno. Il team è stato informato.");
        problem.setTitle("Errore interno");
        problem.setType(URI.create(TYPE_PREFIX + KebabCase.from(Kind.INTERNAL_ERROR)));
        return problem;
    }

    private enum Kind { RESOURCE_NOT_FOUND, INVALID_REQUEST, CONFLICT, INTERNAL_ERROR }
}
