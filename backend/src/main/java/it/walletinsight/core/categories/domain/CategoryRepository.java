package it.walletinsight.core.categories.domain;

import it.walletinsight.core.users.domain.UserId;

import java.util.List;
import java.util.Optional;

/** Porta di persistenza delle categorie di Wallet Insights. */
public interface CategoryRepository {

    /** Le categorie di un utente, macro e sottocategorie insieme, in ordine di nome. */
    List<Category> findByUser(UserId userId);

    /** Solo fra quelle dell'utente: chiedere una categoria altrui deve dare «non esiste». */
    Optional<Category> findById(UserId userId, CategoryId id);

    /** In ordine: una macro va scritta prima delle sue figlie. */
    void insertAll(List<Category> categories);

    void update(Category category);

    void delete(UserId userId, CategoryId id);
}
