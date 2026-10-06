package it.walletinsight.core.ingestion.application;

import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionRepository;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.platform.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Casi d'uso delle sorgenti collegate: elencarle, collegarne una, scollegarla.
 *
 * Collegare è idempotente: una sorgente già presente non viene duplicata, le si
 * sostituiscono le credenziali mantenendo l'identificatore. È ciò che rende
 * sicuro riprovare un onboarding interrotto a metà, dove l'utente può arrivare
 * due volte allo stesso passo.
 *
 * L'esistenza dell'utente viene verificata qui e non lasciata alla chiave esterna
 * del database: un utente sconosciuto è un 404 con un messaggio comprensibile,
 * non una violazione di vincolo che uscirebbe come errore interno.
 *
 * `recordImported` e `recordRun` stanno qui accanto agli altri casi d'uso e non in
 * un servizio a parte: è lo stesso aggregato. A chiamarli è `ImportService`, quando
 * un import è arrivato in fondo.
 */
@Service
@Transactional
public class ImportConnectionService {

    private static final String USER_RESOURCE_TYPE = "Utente";
    private static final String CONNECTION_RESOURCE_TYPE = "Connessione di importazione";

    private final ImportConnectionRepository repository;
    private final UserRepository users;

    public ImportConnectionService(ImportConnectionRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ImportConnection> listConnections(UserId userId) {
        requireUser(userId);
        return repository.findByUser(userId);
    }

    /**
     * Sposta in avanti il segnaposto di una connessione dopo un import riuscito.
     *
     * Idempotente e monotòno: rigirare lo stesso import, o riceverne uno arrivato
     * fuori ordine, non arretra il punto di ripartenza (la regola è in
     * {@link ImportConnection#importedThrough}). Quando non c'è nulla da spostare
     * la scrittura viene proprio saltata.
     */
    public ImportConnection recordImported(
            UserId userId, IngestionSource source, LocalDate lastRecordDate) {
        requireUser(userId);
        ImportConnection connection = repository.find(userId, source)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CONNECTION_RESOURCE_TYPE, KebabCase.from(source)));

        ImportConnection avanzata = connection.importedThrough(lastRecordDate);
        if (!avanzata.equals(connection)) {
            repository.updateLastRecordDate(avanzata);
        }
        return avanzata;
    }

    /**
     * Registra che l'import è girato adesso, qualunque cosa abbia trovato.
     *
     * Diverso da {@link #recordImported}, e la differenza è il punto: quello dice
     * fino a quando si hanno i dati, questo dice quando si è guardato. Un giro a
     * vuoto muove solo il secondo, ed è ciò che permette all'interfaccia di dire
     * "controllato dieci minuti fa, niente di nuovo" invece di lasciare il dubbio.
     */
    public ImportConnection recordRun(UserId userId, IngestionSource source, Instant at) {
        ImportConnection connection = repository.find(userId, source)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CONNECTION_RESOURCE_TYPE, KebabCase.from(source)));

        ImportConnection girata = connection.ranAt(at);
        repository.updateLastRunAt(girata);
        return girata;
    }

    public ImportConnection recordRejectedCredentials(UserId userId, IngestionSource source, Instant at) {
        ImportConnection connection = repository.find(userId, source)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CONNECTION_RESOURCE_TYPE, KebabCase.from(source)));

        ImportConnection rifiutata = connection.credentialsRejected(at);
        repository.updateCredentialsRejectedAt(rifiutata);
        return rifiutata;
    }

    public ImportConnection configure(
            UserId userId, IngestionSource source, PersonalToken token, boolean enabled) {
        requireUser(userId);

        LocalDate today = LocalDate.now();
        Optional<ImportConnection> existing = repository.find(userId, source);
        ImportConnection connection = existing
                .map(current -> current.reconfigure(token, enabled, today))
                .orElseGet(() -> ImportConnection.configure(userId, source, token, enabled, today));

        if (existing.isPresent()) {
            repository.update(connection);
        } else {
            repository.insert(connection);
        }
        return connection;
    }

    public void disconnect(UserId userId, IngestionSource source) {
        requireUser(userId);
        if (!repository.delete(userId, source)) {
            // L'identificativo nell'errore è quello pubblico ('budget-bakers'), non il nome della costante.
            throw new ResourceNotFoundException(CONNECTION_RESOURCE_TYPE, KebabCase.from(source));
        }
    }

    private void requireUser(UserId userId) {
        if (users.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString());
        }
    }
}
