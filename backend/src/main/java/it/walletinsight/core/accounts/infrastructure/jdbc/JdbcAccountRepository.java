package it.walletinsight.core.accounts.infrastructure.jdbc;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.accounts.domain.AccountRepository;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC della porta {@link AccountRepository}: tutto l'SQL dei conti vive qui.
 *
 * Come negli altri moduli, le enum sono persistite con i valori kebab-case del
 * contratto HTTP (`current-account`, `budget-bakers`), non con i nomi delle
 * costanti Java: il database resta leggibile senza avere sottomano il codice.
 */
@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final String TABLE = "accounts";

    private static final String COLUMNS = """
            id, user_id, source, external_id, name, source_name, kind, currency_code,
            initial_balance_cents, iban, color, archived, excluded_from_stats
            """;

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Account> findByUser(UserId userId) {
        // `order by id`: gli UUIDv7 sono ordinati per tempo, quindi è l'ordine di creazione.
        return jdbc.sql("select %s from %s where user_id = :userId order by id".formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .query(this::mapRow)
                .list();
    }

    @Override
    public List<Account> findByUserAndSource(UserId userId, IngestionSource source) {
        return jdbc.sql("""
                select %s from %s where user_id = :userId and source = :source order by id
                """.formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .query(this::mapRow)
                .list();
    }

    @Override
    public Optional<Account> findByExternalId(
            UserId userId, IngestionSource source, String externalId) {
        return jdbc.sql("""
                select %s from %s
                 where user_id = :userId and source = :source and external_id = :externalId
                """.formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .param("externalId", externalId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Account> findById(UserId userId, AccountId id) {
        // `user_id` nella where e non in un controllo a valle: un conto di un altro
        // utente non viene trovato, quindi non c'e' un ramo da ricordarsi di scrivere.
        return jdbc.sql("select %s from %s where user_id = :userId and id = :id"
                .formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("id", id.value())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void insert(Account account) {
        jdbc.sql("""
                insert into %s (id, user_id, source, external_id, name, source_name, kind,
                                currency_code, initial_balance_cents, iban, color,
                                archived, excluded_from_stats)
                values (:id, :userId, :source, :externalId, :name, :sourceName, :kind,
                        :currencyCode, :initialBalanceCents, :iban, :color,
                        :archived, :excludedFromStats)
                """.formatted(TABLE))
                .paramSource(parametersOf(account))
                .update();
    }

    @Override
    public void update(Account account) {
        // `source` ed `external_id` non compaiono: sono il riferimento all'originale
        // e non cambiano mai: cambiarli vorrebbe dire che è un altro conto.
        jdbc.sql("""
                update %s
                   set name = :name,
                       source_name = :sourceName,
                       kind = :kind,
                       currency_code = :currencyCode,
                       initial_balance_cents = :initialBalanceCents,
                       iban = :iban,
                       color = :color,
                       archived = :archived,
                       excluded_from_stats = :excludedFromStats,
                       updated_at = now()
                 where id = :id
                """.formatted(TABLE))
                .paramSource(parametersOf(account))
                .update();
    }

    private MapSqlParameterSource parametersOf(Account account) {
        return new MapSqlParameterSource()
                .addValue("id", account.id().value())
                .addValue("userId", account.userId().value())
                .addValue("source", KebabCase.from(account.source()))
                .addValue("externalId", account.externalId())
                .addValue("name", account.name())
                .addValue("sourceName", account.sourceName())
                .addValue("kind", KebabCase.from(account.kind()))
                .addValue("currencyCode", account.currency().name())
                .addValue("initialBalanceCents", account.initialBalance().amount())
                .addValue("iban", account.iban())
                .addValue("color", account.color())
                .addValue("archived", account.archived())
                .addValue("excludedFromStats", account.excludedFromStats());
    }

    private Account mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        CurrencyCode currency = enumOf(CurrencyCode.class, resultSet.getString("currency_code"), "currency_code");
        return new Account(
                AccountId.of(resultSet.getObject("id", UUID.class)),
                UserId.of(resultSet.getObject("user_id", UUID.class)),
                enumOf(IngestionSource.class, resultSet.getString("source"), "source"),
                resultSet.getString("external_id"),
                resultSet.getString("name"),
                resultSet.getString("source_name"),
                enumOf(AccountKind.class, resultSet.getString("kind"), "kind"),
                currency,
                Money.of(resultSet.getLong("initial_balance_cents"), currency),
                resultSet.getString("iban"),
                resultSet.getString("color"),
                resultSet.getBoolean("archived"),
                resultSet.getBoolean("excluded_from_stats"));
    }

    /** Un valore fuori contratto nel database è un dato corrotto (500), non una richiesta errata (400). */
    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, String column) {
        try {
            return KebabCase.to(type, raw);
        } catch (IllegalArgumentException cause) {
            throw CorruptedDataException.invalidColumn(TABLE, column, raw, cause);
        }
    }
}
