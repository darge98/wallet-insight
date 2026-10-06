package it.walletinsight.core.ingestion.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.source.IngestionSource;

import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza delle {@link ImportConnection}.
 *
 * L'implementazione in `infrastructure/jdbc` cifra il segreto scrivendo e lo
 * decifra leggendo: qui non compare, perché il dominio maneggia il token in
 * chiaro e non sa come venga custodito.
 */
public interface ImportConnectionRepository {

    /** Le connessioni di un utente, in ordine di identificatore (cronologico, con gli UUIDv7). */
    List<ImportConnection> findByUser(UserId userId);

    Optional<ImportConnection> find(UserId userId, IngestionSource source);

    /** Le sole connessioni su cui un import deve lavorare: le altre l'utente le ha messe in pausa. */
    List<ImportConnection> findEnabledByUser(UserId userId);

    /** Le connessioni attive di una sorgente, di tutti gli utenti, in ordine di iscrizione. */
    List<ImportConnection> findEnabledBySource(IngestionSource source);

    void insert(ImportConnection connection);

    void update(ImportConnection connection);

    /**
     * Scrive il solo segnaposto dell'import.
     *
     * Separato da {@link #update} perché quello riscrive anche il segreto, e
     * ricifrare una credenziale a ogni giro di job è lavoro inutile su un dato
     * che nessuno ha cambiato.
     */
    void updateLastRecordDate(ImportConnection connection);

    /**
     * Scrive l'istante dell'ultima esecuzione e, con lui, le credenziali tornate buone.
     *
     * Separato per la stessa ragione del segnaposto: passare da {@link #update}
     * vorrebbe dire ricifrare la credenziale a ogni giro.
     */
    void updateLastRunAt(ImportConnection connection);

    void updateCredentialsRejectedAt(ImportConnection connection);

    /** `false` se non c'era nulla da scollegare: decidere cosa farne spetta all'application. */
    boolean delete(UserId userId, IngestionSource source);
}
