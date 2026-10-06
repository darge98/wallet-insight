package it.walletinsight.core.categories.infrastructure.jdbc;

import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.users.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter JDBC delle categorie di Wallet Insights. */
@Repository
class JdbcCategoryRepository implements CategoryRepository {

    private static final String COLUMNS = "id, user_id, parent_id, name, color, template_key";

    private final JdbcClient jdbc;

    JdbcCategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Category> findByUser(UserId userId) {
        return jdbc.sql("select %s from categories where user_id = :userId order by lower(name), id"
                        .formatted(COLUMNS))
                .param("userId", userId.value())
                .query(JdbcCategoryRepository::mapRow)
                .list();
    }

    @Override
    public Optional<Category> findById(UserId userId, CategoryId id) {
        return jdbc.sql("select %s from categories where user_id = :userId and id = :id".formatted(COLUMNS))
                .param("userId", userId.value())
                .param("id", id.value())
                .query(JdbcCategoryRepository::mapRow)
                .optional();
    }

    @Override
    public void insertAll(List<Category> categories) {
        for (Category category : categories) {
            jdbc.sql("""
                    insert into categories (id, user_id, parent_id, name, color, template_key)
                    values (:id, :userId, :parentId, :name, :color, :templateKey)
                    """)
                    .param("id", category.id().value())
                    .param("userId", category.userId().value())
                    .param("parentId", category.parentId() == null ? null : category.parentId().value())
                    .param("name", category.name())
                    .param("color", category.color())
                    .param("templateKey", category.templateKey())
                    .update();
        }
    }

    @Override
    public void update(Category category) {
        jdbc.sql("""
                update categories
                   set parent_id = :parentId, name = :name, color = :color, updated_at = now()
                 where id = :id and user_id = :userId
                """)
                .param("id", category.id().value())
                .param("userId", category.userId().value())
                .param("parentId", category.parentId() == null ? null : category.parentId().value())
                .param("name", category.name())
                .param("color", category.color())
                .update();
    }

    @Override
    public void delete(UserId userId, CategoryId id) {
        jdbc.sql("delete from categories where id = :id and user_id = :userId")
                .param("id", id.value())
                .param("userId", userId.value())
                .update();
    }

    private static Category mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID padre = rs.getObject("parent_id", UUID.class);
        return new Category(
                CategoryId.of(rs.getObject("id", UUID.class)),
                UserId.of(rs.getObject("user_id", UUID.class)),
                padre == null ? null : CategoryId.of(padre),
                rs.getString("name"),
                rs.getString("color"),
                rs.getString("template_key"));
    }
}
