package it.walletinsight.core.users.domain;

import it.walletinsight.shared.page.PageRequest;

import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza dell'aggregato {@link User}.
 *
 * Implementata in `infrastructure/jdbc` con SQL esplicito: il dominio non conosce
 * né Spring Data né il database, come vuole la forma esagonale dei moduli.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    /** Una pagina di utenti in ordine di ID: con gli UUIDv7 equivale all'ordine di creazione. */
    List<User> findAll(PageRequest request);

    long count();

    void insert(User user);

    void update(User user);

    /** `false` se l'utente non esisteva: decidere cosa fare spetta all'application. */
    boolean deleteById(UserId id);
}
