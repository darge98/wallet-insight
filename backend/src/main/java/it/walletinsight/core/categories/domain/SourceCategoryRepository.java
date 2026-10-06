package it.walletinsight.core.categories.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.source.IngestionSource;

import java.util.List;
import java.util.Optional;

/** Porta di persistenza delle categorie delle sorgenti e dei loro agganci. */
public interface SourceCategoryRepository {

    List<SourceCategory> findByUser(UserId userId);

    List<SourceCategory> findByUserAndSource(UserId userId, IngestionSource source);

    Optional<SourceCategory> findById(UserId userId, SourceCategoryId id);

    void insert(SourceCategory category);

    /** Riscrive nome, gruppo, origine e aggancio. */
    void update(SourceCategory category);

    /** Sposta su {@code to} gli agganci che puntano a {@code from}. */
    int relink(UserId userId, CategoryId from, CategoryId to);
}
