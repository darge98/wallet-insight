package it.walletinsight.core.accounts.domain;

import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza dell'aggregato {@link Account}.
 *
 * Implementata in `infrastructure/jdbc` con SQL esplicito: il dominio non conosce
 * né Spring Data né il database, come vuole la forma esagonale dei moduli.
 */
public interface AccountRepository {

    /** I conti di un utente, in ordine di identificatore (cronologico, con gli UUIDv7). */
    List<Account> findByUser(UserId userId);

    /** I conti che un utente ha importato da una sorgente. */
    List<Account> findByUserAndSource(UserId userId, IngestionSource source);

    /** Il conto che corrisponde a un dato della sorgente: è la chiave dell'import idempotente. */
    Optional<Account> findByExternalId(UserId userId, IngestionSource source, String externalId);

    /**
     * Un conto per identificatore, ma solo fra quelli dell'utente indicato.
     *
     * L'utente è parte della domanda, non un controllo aggiunto dopo: chiedere un
     * conto che non è suo deve dare "non esiste", e con la ricerca già ristretta
     * non c'è modo di dimenticarsi di verificarlo.
     */
    Optional<Account> findById(UserId userId, AccountId id);

    void insert(Account account);

    void update(Account account);
}
