package it.walletinsight.core.subscriptions.infrastructure.jdbc;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.subscriptions.domain.Cadence;
import it.walletinsight.core.subscriptions.domain.CadenceUnit;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionId;
import it.walletinsight.core.subscriptions.domain.SubscriptionRepository;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.money.Money;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Adapter JDBC degli abbonamenti: tutto il loro SQL vive qui. */
@Repository
class JdbcSubscriptionRepository implements SubscriptionRepository {

    private static final String COLUMNS =
            "id, user_id, name, amount_cents, cadence_every, cadence_unit, start_date, end_date, category_id, account_id";

    private static final RowMapper<Subscription> ROW_MAPPER = (rs, rowNum) -> new Subscription(
            SubscriptionId.of(rs.getObject("id", UUID.class)),
            UserId.of(rs.getObject("user_id", UUID.class)),
            rs.getString("name"),
            Money.of(rs.getLong("amount_cents")),
            new Cadence(rs.getInt("cadence_every"), KebabCase.to(CadenceUnit.class, rs.getString("cadence_unit"))),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class),
            nullable(rs.getObject("category_id", UUID.class), CategoryId::of),
            nullable(rs.getObject("account_id", UUID.class), AccountId::of));

    private final JdbcClient jdbc;

    JdbcSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Subscription> findByUser(UserId userId) {
        return jdbc.sql("select " + COLUMNS + " from subscriptions where user_id = :userId order by lower(name), id")
                .param("userId", userId.value())
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public Optional<Subscription> findById(UserId userId, SubscriptionId id) {
        return jdbc.sql("select " + COLUMNS + " from subscriptions where id = :id and user_id = :userId")
                .param("id", id.value())
                .param("userId", userId.value())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public void insert(Subscription subscription) {
        jdbc.sql("""
                insert into subscriptions
                       (id, user_id, name, amount_cents, cadence_every, cadence_unit, start_date, end_date,
                        category_id, account_id)
                values (:id, :userId, :name, :amount, :every, :unit, :startDate, :endDate,
                        :categoryId, :accountId)
                """)
                .paramSource(params(subscription))
                .update();
    }

    @Override
    public void update(Subscription subscription) {
        jdbc.sql("""
                update subscriptions
                   set name = :name, amount_cents = :amount, cadence_every = :every, cadence_unit = :unit,
                       start_date = :startDate, end_date = :endDate,
                       category_id = :categoryId, account_id = :accountId, updated_at = now()
                 where id = :id and user_id = :userId
                """)
                .paramSource(params(subscription))
                .update();
    }

    @Override
    public void delete(UserId userId, SubscriptionId id) {
        jdbc.sql("delete from subscriptions where id = :id and user_id = :userId")
                .param("id", id.value())
                .param("userId", userId.value())
                .update();
    }

    @Override
    public void reassignCategory(UserId userId, CategoryId from, CategoryId into) {
        jdbc.sql("""
                update subscriptions set category_id = :into, updated_at = now()
                 where user_id = :userId and category_id = :from
                """)
                .param("userId", userId.value())
                .param("from", from.value())
                .param("into", into.value())
                .update();
    }

    private static <T, R> R nullable(T value, Function<T, R> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    private static MapSqlParameterSource params(Subscription subscription) {
        return new MapSqlParameterSource()
                .addValue("id", subscription.id().value())
                .addValue("userId", subscription.userId().value())
                .addValue("name", subscription.name())
                .addValue("amount", subscription.amount().amount())
                .addValue("every", subscription.cadence().every())
                .addValue("unit", KebabCase.from(subscription.cadence().unit()))
                .addValue("startDate", subscription.startDate())
                .addValue("endDate", subscription.endDate())
                .addValue("categoryId", nullable(subscription.categoryId(), CategoryId::value))
                .addValue("accountId", nullable(subscription.accountId(), AccountId::value));
    }
}
