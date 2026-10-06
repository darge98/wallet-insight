package it.walletinsight.core.ingestion.application;

import it.walletinsight.core.accounts.application.AccountService;
import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionRepository;
import it.walletinsight.core.ingestion.domain.ImportOutcome;
import it.walletinsight.core.ingestion.domain.ImportSource;
import it.walletinsight.core.ingestion.domain.ImportedMovements;
import it.walletinsight.core.ingestion.domain.RejectedCredentialsException;
import it.walletinsight.core.ingestion.domain.SourceUnavailableException;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.ImportedWindow;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.source.IngestionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Importare i dati di un utente dalle sorgenti che ha collegato.
 *
 * È il caso d'uso, e vive qui perché sia uno solo: lo chiamano l'onboarding,
 * l'import a mano e le schedulazioni delle sorgenti. Che a chiamarlo sia un
 * browser o un cron non cambia cosa succede.
 *
 * Sta nel backend e non in un processo separato per una ragione precisa: il
 * token della sorgente non deve uscire da dove vive la chiave che lo decifra.
 * Finché l'import girava altrove serviva un endpoint che lo consegnasse in
 * chiaro; ora non serve più, e non esiste più.
 *
 * **Non è transazionale, di proposito.** In mezzo ci sono chiamate HTTP a terzi
 * che durano secondi: tenere aperta una transazione per tutta la loro durata
 * significherebbe occupare una connessione al database mentre si aspetta la
 * rete. Ogni scrittura è già atomica per conto suo — allineare i conti, salvare
 * i movimenti, spostare il segnaposto — e sono i confini giusti: i conti
 * allineati restano validi anche se i movimenti falliscono subito dopo, e il
 * segnaposto si muove per ultimo, quando c'è davvero qualcosa a cui corrisponde.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final String USER_RESOURCE_TYPE = "Utente";

    /**
     * Ciò che l'utente legge quando l'errore non è uno dei due scritti per lui.
     *
     * Il messaggio di un'eccezione qualsiasi può portare SQL, nomi di colonne o la risposta
     * grezza della sorgente: va nei log, mai nell'esito che arriva al client.
     */
    static final String GENERIC_FAILURE =
            "L'importazione non è riuscita per un errore interno: riprova più tardi.";

    private final ImportConnectionRepository connections;
    private final ImportConnectionService connectionService;
    private final AccountService accounts;
    private final CategoryService categories;
    private final MovementService movements;
    private final UserRepository users;
    private final List<ImportSource> sources;

    ImportService(ImportConnectionRepository connections,
                  ImportConnectionService connectionService,
                  AccountService accounts,
                  CategoryService categories,
                  MovementService movements,
                  UserRepository users,
                  List<ImportSource> sources) {
        this.connections = connections;
        this.connectionService = connectionService;
        this.accounts = accounts;
        this.categories = categories;
        this.movements = movements;
        this.users = users;
        this.sources = List.copyOf(sources);
    }

    /**
     * L'adapter che sa leggere questa sorgente, se esiste.
     *
     * Cercato ogni volta invece di essere indicizzato all'avvio: gli adapter sono
     * una manciata, e un indice costruito nel costruttore fa fallire l'intero
     * contesto — con un messaggio che non spiega nulla — se un adapter non è
     * ancora pronto a dire quale sorgente serve.
     */
    private Optional<ImportSource> adapterFor(IngestionSource source) {
        return sources.stream().filter(candidato -> candidato.source() == source).findFirst();
    }

    /**
     * Importa da tutte le sorgenti attive dell'utente, una per una.
     *
     * Restituisce un esito per sorgente, riuscite e fallite: chi ha chiamato
     * deve poter dire all'utente cos'è successo su ciascuna, non solo se
     * "l'aggiornamento" è andato bene.
     */
    public List<ImportOutcome> importFor(UserId userId) {
        if (users.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString());
        }

        return connections.findEnabledByUser(userId).stream()
                .map(this::importOne)
                .toList();
    }

    /** Importa una sola sorgente dell'utente, se è collegata e attiva. */
    public Optional<ImportOutcome> importFor(UserId userId, IngestionSource source) {
        return connections.find(userId, source)
                .filter(ImportConnection::enabled)
                .map(this::importOne);
    }

    /**
     * Importa una sorgente per ogni utente che l'ha collegata e attiva, in sequenza.
     * Un utente che fallisce riceve un esito fallito e non ferma gli altri.
     *
     * @return l'esito per utente, in ordine di iscrizione
     */
    public Map<UserId, ImportOutcome> importForAllUsers(IngestionSource source) {
        return importForAllUsers(source, connection -> true);
    }

    /** Come {@link #importForAllUsers(IngestionSource)}, limitato alle connessioni scelte. */
    public Map<UserId, ImportOutcome> importForAllUsers(IngestionSource source,
                                                        Predicate<ImportConnection> daImportare) {
        List<ImportConnection> attive = connections.findEnabledBySource(source).stream()
                .filter(daImportare)
                .toList();
        Map<UserId, ImportOutcome> esiti = new LinkedHashMap<>();
        for (ImportConnection connection : attive) {
            try {
                esiti.put(connection.userId(), importOne(connection));
            } catch (RuntimeException e) {
                log.error("Import {} fallito per l'utente {}: {}",
                        source, connection.userId(), e.getMessage(), e);
                esiti.put(connection.userId(), ImportOutcome.failed(source, GENERIC_FAILURE));
            }
        }

        long falliti = esiti.values().stream().filter(esito -> !esito.succeeded()).count();
        log.info("Import {} di tutti gli utenti: {} connessioni importate, {} non riuscite",
                source, attive.size(), falliti);
        return esiti;
    }

    private ImportOutcome importOne(ImportConnection connection) {
        Optional<ImportSource> adapter = adapterFor(connection.source());
        if (adapter.isEmpty()) {
            // Una sorgente collegabile ma senza adapter non e' un errore dell'utente.
            return ImportOutcome.failed(connection.source(),
                    "L'importazione da questa sorgente non è ancora disponibile.");
        }
        ImportSource source = adapter.get();

        try {
            // Prima i conti, e non e' un ordine di comodo: ogni movimento poggia su un
            // conto, e i conti allineati sono anche cio' che traduce gli identificativi
            // della sorgente nei nostri. Senza, i movimenti non avrebbero dove andare.
            List<Account> letti = source.readAccounts(connection);
            List<Account> allineati = letti.isEmpty()
                    ? List.of()
                    : accounts.syncFromSource(connection.userId(), connection.source(), letti);

            // Poi le categorie: un movimento e' classificato in una categoria di
            // Wallet Insights, e l'aggancio fra quella della sorgente e la nostra nasce qui.
            List<ImportedCategory> categorieLette = source.readCategories(connection);
            Map<String, CategoryId> agganci = categories.syncFromSource(
                    connection.userId(), connection.source(), categorieLette);

            // Riaggancia i movimenti che portano la traccia della categoria ma non il
            // riferimento risolto. Va fatto qui e non solo sui movimenti di questo giro:
            // un import e' incrementale, e senza questo passo uno storico gia' importato
            // resterebbe senza classificazione — misurato, 1677 movimenti su 1678.
            int riagganciati = movements.linkCategories(connection.userId(), connection.source());
            if (riagganciati > 0) {
                log.info("Import {} per l'utente {}: {} movimenti riagganciati alla loro categoria",
                        connection.source(), connection.userId(), riagganciati);
            }

            ImportedMovements movimenti =
                    source.readMovements(connection, allineati, agganci);
            Set<AccountId> contiLetti = allineati.stream().map(Account::id).collect(Collectors.toSet());
            ImportedWindow scritti = movements.saveImportedWindow(connection.userId(), connection.source(),
                    movimenti.from(), movimenti.toExclusive(), contiLetti, movimenti.movements());
            int salvati = scritti.saved();
            scritti.removed().forEach(rimosso -> log.info(
                    "Import {} per l'utente {}: tolto il movimento in sospeso {} del {} ({} cent), "
                            + "la sorgente non lo restituisce più",
                    connection.source(), connection.userId(), rimosso.externalId(),
                    rimosso.recordedAt(), rimosso.amount().amount()));

            // Il segnaposto per ultimo, e solo ora: avanzarlo prima di aver scritto i
            // movimenti significherebbe, al primo errore di scrittura, non rileggere mai
            // piu' la finestra che si e' persa.
            LocalDate segnaposto = aggiornaSegnaposto(connection, movimenti);

            // E poi l'istante dell'esecuzione, che invece si muove sempre: e' cio' che
            // distingue "controllato ora, niente di nuovo" da "non si sa da quando".
            connectionService.recordRun(connection.userId(), connection.source(), Instant.now());

            log.info("Import {} per l'utente {}: {} conti, {} categorie, {} movimenti letti "
                            + "di cui {} salvati, segnaposto {}",
                    connection.source(), connection.userId(), allineati.size(),
                    categorieLette.size(), movimenti.size(), salvati, segnaposto);
            return ImportOutcome.succeeded(connection.source(), allineati.size(),
                    categorieLette.size(), movimenti.size(), salvati, segnaposto);
        } catch (RejectedCredentialsException e) {
            log.warn("Import {} per l'utente {}: credenziali rifiutate",
                    connection.source(), connection.userId());
            connectionService.recordRejectedCredentials(
                    connection.userId(), connection.source(), Instant.now());
            return ImportOutcome.failed(connection.source(), e.getMessage());
        } catch (SourceUnavailableException e) {
            log.warn("Import {} per l'utente {}: sorgente non disponibile",
                    connection.source(), connection.userId(), e);
            return ImportOutcome.failed(connection.source(), e.getMessage());
        } catch (RuntimeException e) {
            // Il messaggio puo' contenere la risposta della sorgente, mai il token.
            log.error("Import {} fallito per l'utente {}: {}",
                    connection.source(), connection.userId(), e.getMessage(), e);
            return ImportOutcome.failed(connection.source(), GENERIC_FAILURE);
        }
    }

    /**
     * Sposta il segnaposto solo se qualcosa è stato letto davvero.
     *
     * Un giro a vuoto non è un import più recente: se il segnaposto avanzasse
     * comunque, un movimento registrato in ritardo su un giorno già superato
     * resterebbe fuori per sempre.
     */
    private LocalDate aggiornaSegnaposto(ImportConnection connection, ImportedMovements movimenti) {
        Optional<LocalDate> ultimo = movimenti.lastRecordDate();
        if (ultimo.isEmpty()) {
            return connection.lastRecordDate();
        }
        return connectionService
                .recordImported(connection.userId(), connection.source(), ultimo.get())
                .lastRecordDate();
    }
}
