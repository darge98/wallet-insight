package it.walletinsight.core.users.infrastructure.jdbc;

import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.page.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC della porta {@link UserRepository}: tutto l'SQL degli utenti vive qui.
 *
 * Le enum sono persistite con i valori kebab-case del contratto HTTP (`it`,
 * `current-month`), non con i nomi delle costanti Java: una riga che contiene un
 * valore fuori contratto è dati corrotti, non una richiesta malformata.
 */
@Repository
class JdbcUserRepository implements UserRepository {

    private static final String TABLE = "users";

    private static final String COLUMNS =
            "id, first_name, last_name, email, time_zone, language, default_dashboard_period";

    private final JdbcClient jdbc;

    JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jdbc.sql("select %s from %s where id = :id".formatted(COLUMNS, TABLE))
                .param("id", id.value())
                .query(JdbcUserRepository::mapRow)
                .optional();
    }

    @Override
    public List<User> findAll(PageRequest request) {
        // `order by id`: gli UUIDv7 sono ordinati per tempo, quindi è l'ordine di creazione.
        return jdbc.sql("select %s from %s order by id limit :limit offset :offset"
                        .formatted(COLUMNS, TABLE))
                .param("limit", request.size())
                .param("offset", request.offset())
                .query(JdbcUserRepository::mapRow)
                .list();
    }

    @Override
    public long count() {
        return jdbc.sql("select count(*) from %s".formatted(TABLE))
                .query(Long.class)
                .single();
    }

    @Override
    public void insert(User user) {
        jdbc.sql("""
                insert into %s (id, first_name, last_name, email, time_zone, language, default_dashboard_period)
                values (:id, :firstName, :lastName, :email, :timeZone, :language, :dashboardPeriod)
                """.formatted(TABLE))
                .paramSource(parametersOf(user))
                .update();
    }

    @Override
    public void update(User user) {
        jdbc.sql("""
                update %s
                   set first_name = :firstName,
                       last_name = :lastName,
                       email = :email,
                       time_zone = :timeZone,
                       language = :language,
                       default_dashboard_period = :dashboardPeriod,
                       updated_at = now()
                 where id = :id
                """.formatted(TABLE))
                .paramSource(parametersOf(user))
                .update();
    }

    @Override
    public boolean deleteById(UserId id) {
        return jdbc.sql("delete from %s where id = :id".formatted(TABLE))
                .param("id", id.value())
                .update() > 0;
    }

    private static MapSqlParameterSource parametersOf(User user) {
        UserSettings settings = user.settings();
        return new MapSqlParameterSource()
                .addValue("id", user.id().value())
                .addValue("firstName", user.firstName())
                .addValue("lastName", user.lastName())
                .addValue("email", user.email())
                .addValue("timeZone", settings.timeZone())
                .addValue("language", KebabCase.from(settings.language()))
                .addValue("dashboardPeriod", KebabCase.from(settings.defaultDashboardPeriod()));
    }

    private static User mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        UserSettings settings = new UserSettings(
                language(resultSet),
                resultSet.getString("time_zone"),
                dashboardPeriod(resultSet));
        return new User(
                UserId.of(resultSet.getObject("id", UUID.class)),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("email"),
                settings);
    }

    private static UserLanguage language(ResultSet resultSet) throws SQLException {
        String raw = resultSet.getString("language");
        try {
            return KebabCase.to(UserLanguage.class, raw);
        } catch (IllegalArgumentException cause) {
            throw CorruptedDataException.invalidColumn(TABLE, "language", raw, cause);
        }
    }

    private static DashboardPeriod dashboardPeriod(ResultSet resultSet) throws SQLException {
        String raw = resultSet.getString("default_dashboard_period");
        try {
            return KebabCase.to(DashboardPeriod.class, raw);
        } catch (IllegalArgumentException cause) {
            throw CorruptedDataException.invalidColumn(TABLE, "default_dashboard_period", raw, cause);
        }
    }
}
