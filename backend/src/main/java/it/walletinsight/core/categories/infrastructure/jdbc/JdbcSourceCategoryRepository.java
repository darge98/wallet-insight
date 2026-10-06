package it.walletinsight.core.categories.infrastructure.jdbc;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.SourceCategory;
import it.walletinsight.core.categories.domain.SourceCategoryId;
import it.walletinsight.core.categories.domain.SourceCategoryRepository;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.source.IngestionSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter JDBC delle categorie delle sorgenti. */
@Repository
class JdbcSourceCategoryRepository implements SourceCategoryRepository {

    private static final String TABLE = "source_categories";
    private static final String COLUMNS =
            "id, user_id, source, external_id, name, source_group, parent_external_id, category_id";

    private final JdbcClient jdbc;

    JdbcSourceCategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SourceCategory> findByUser(UserId userId) {
        return jdbc.sql("select %s from %s where user_id = :userId".formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .query(JdbcSourceCategoryRepository::mapRow)
                .list();
    }

    @Override
    public List<SourceCategory> findByUserAndSource(UserId userId, IngestionSource source) {
        return jdbc.sql("select %s from %s where user_id = :userId and source = :source"
                        .formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .query(JdbcSourceCategoryRepository::mapRow)
                .list();
    }

    @Override
    public Optional<SourceCategory> findById(UserId userId, SourceCategoryId id) {
        return jdbc.sql("select %s from %s where user_id = :userId and id = :id".formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("id", id.value())
                .query(JdbcSourceCategoryRepository::mapRow)
                .optional();
    }

    @Override
    public void insert(SourceCategory category) {
        jdbc.sql("""
                insert into source_categories (id, user_id, source, external_id, name, source_group,
                                               parent_external_id, category_id)
                values (:id, :userId, :source, :externalId, :name, :group, :parentExternalId, :categoryId)
                """)
                .param("id", category.id().value())
                .param("userId", category.userId().value())
                .param("source", KebabCase.from(category.source()))
                .param("externalId", category.externalId())
                .param("name", category.name())
                .param("group", category.group())
                .param("parentExternalId", category.parentExternalId())
                .param("categoryId", category.category() == null ? null : category.category().value())
                .update();
    }

    @Override
    public void update(SourceCategory category) {
        jdbc.sql("""
                update source_categories
                   set name = :name, source_group = :group, parent_external_id = :parentExternalId,
                       category_id = :categoryId, updated_at = now()
                 where id = :id and user_id = :userId
                """)
                .param("id", category.id().value())
                .param("userId", category.userId().value())
                .param("name", category.name())
                .param("group", category.group())
                .param("parentExternalId", category.parentExternalId())
                .param("categoryId", category.category() == null ? null : category.category().value())
                .update();
    }

    @Override
    public int relink(UserId userId, CategoryId from, CategoryId to) {
        return jdbc.sql("""
                update source_categories set category_id = :to, updated_at = now()
                 where user_id = :userId and category_id = :from
                """)
                .param("userId", userId.value())
                .param("from", from.value())
                .param("to", to.value())
                .update();
    }

    private static SourceCategory mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID categoria = rs.getObject("category_id", UUID.class);
        return new SourceCategory(
                SourceCategoryId.of(rs.getObject("id", UUID.class)),
                UserId.of(rs.getObject("user_id", UUID.class)),
                sourceOf(rs.getString("source")),
                rs.getString("external_id"),
                rs.getString("name"),
                rs.getString("source_group"),
                rs.getString("parent_external_id"),
                categoria == null ? null : CategoryId.of(categoria));
    }

    private static IngestionSource sourceOf(String raw) {
        try {
            return KebabCase.to(IngestionSource.class, raw);
        } catch (IllegalArgumentException cause) {
            throw CorruptedDataException.invalidColumn(TABLE, "source", raw, cause);
        }
    }
}
