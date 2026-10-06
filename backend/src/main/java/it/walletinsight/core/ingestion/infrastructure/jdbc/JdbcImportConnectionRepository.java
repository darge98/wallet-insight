package it.walletinsight.core.ingestion.infrastructure.jdbc;

import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.core.ingestion.domain.ImportConnectionRepository;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.ingestion.infrastructure.crypto.SecretCipher;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.platform.web.KebabCase;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC della porta {@link ImportConnectionRepository}: tutto l'SQL delle
 * connessioni vive qui, e qui il segreto viene cifrato e decifrato.
 *
 * La cifratura sta in questo layer e non nel dominio perché è una scelta su *come*
 * il dato viene custodito: il dominio conosce il token, il database non lo vede mai
 * in chiaro. Il contesto passato al cifrario lega ogni valore alla coppia
 * (utente, sorgente) della sua riga, così una riga copiata altrove non si decifra.
 *
 * Come nel modulo `users`, le enum sono persistite con i valori kebab-case del
 * contratto HTTP (`budget-bakers`), non con i nomi delle costanti Java.
 */
@Repository
class JdbcImportConnectionRepository implements ImportConnectionRepository {

    private static final String TABLE = "import_connections";

    private static final String COLUMNS =
            "id, user_id, source, encrypted_secret, enabled, configured_at, last_record_date, last_run_at, "
                    + "credentials_rejected_at";

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    JdbcImportConnectionRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public List<ImportConnection> findByUser(UserId userId) {
        // `order by id`: gli UUIDv7 sono ordinati per tempo, quindi è l'ordine di collegamento.
        return jdbc.sql("select %s from %s where user_id = :userId order by id".formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .query(this::mapRow)
                .list();
    }

    @Override
    public Optional<ImportConnection> find(UserId userId, IngestionSource source) {
        return jdbc.sql("select %s from %s where user_id = :userId and source = :source"
                        .formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<ImportConnection> findEnabledByUser(UserId userId) {
        // `order by id`: con gli UUIDv7 è l'ordine in cui le connessioni sono nate,
        // quindi due import dello stesso utente lavorano sempre nella stessa sequenza.
        return jdbc.sql("""
                select %s from %s where user_id = :userId and enabled = true order by id
                """.formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .query(this::mapRow)
                .list();
    }

    @Override
    public List<ImportConnection> findEnabledBySource(IngestionSource source) {
        return jdbc.sql("""
                select %s from %s where source = :source and enabled = true order by user_id
                """.formatted(COLUMNS, TABLE))
                .param("source", KebabCase.from(source))
                .query(this::mapRow)
                .list();
    }

    @Override
    public void insert(ImportConnection connection) {
        jdbc.sql("""
                insert into %s (id, user_id, source, encrypted_secret, enabled, configured_at,
                                last_record_date, last_run_at, credentials_rejected_at)
                values (:id, :userId, :source, :secret, :enabled, :configuredAt,
                        :lastRecordDate, :lastRunAt, :credentialsRejectedAt)
                """.formatted(TABLE))
                .paramSource(parametersOf(connection))
                .update();
    }

    @Override
    public void update(ImportConnection connection) {
        jdbc.sql("""
                update %s
                   set encrypted_secret = :secret,
                       enabled = :enabled,
                       configured_at = :configuredAt,
                       credentials_rejected_at = :credentialsRejectedAt,
                       updated_at = now()
                 where id = :id
                """.formatted(TABLE))
                .paramSource(parametersOf(connection))
                .update();
    }

    @Override
    public void updateLastRecordDate(ImportConnection connection) {
        jdbc.sql("""
                update %s set last_record_date = :lastRecordDate, updated_at = now() where id = :id
                """.formatted(TABLE))
                .param("id", connection.id().value())
                .param("lastRecordDate", connection.lastRecordDate())
                .update();
    }

    @Override
    public void updateLastRunAt(ImportConnection connection) {
        jdbc.sql("""
                update %s
                   set last_run_at = :lastRunAt,
                       credentials_rejected_at = :credentialsRejectedAt,
                       updated_at = now()
                 where id = :id
                """.formatted(TABLE))
                .param("id", connection.id().value())
                .param("lastRunAt", toTimestamp(connection.lastRunAt()))
                .param("credentialsRejectedAt", toTimestamp(connection.credentialsRejectedAt()))
                .update();
    }

    @Override
    public void updateCredentialsRejectedAt(ImportConnection connection) {
        jdbc.sql("""
                update %s set credentials_rejected_at = :credentialsRejectedAt, updated_at = now()
                 where id = :id
                """.formatted(TABLE))
                .param("id", connection.id().value())
                .param("credentialsRejectedAt", toTimestamp(connection.credentialsRejectedAt()))
                .update();
    }

    @Override
    public boolean delete(UserId userId, IngestionSource source) {
        return jdbc.sql("delete from %s where user_id = :userId and source = :source".formatted(TABLE))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .update() > 0;
    }

    private MapSqlParameterSource parametersOf(ImportConnection connection) {
        return new MapSqlParameterSource()
                .addValue("id", connection.id().value())
                .addValue("userId", connection.userId().value())
                .addValue("source", KebabCase.from(connection.source()))
                .addValue("secret", cipher.encrypt(
                        connection.token().value(),
                        context(connection.userId(), connection.source())))
                .addValue("enabled", connection.enabled())
                .addValue("configuredAt", connection.configuredAt())
                .addValue("lastRecordDate", connection.lastRecordDate())
                .addValue("lastRunAt", toTimestamp(connection.lastRunAt()))
                .addValue("credentialsRejectedAt", toTimestamp(connection.credentialsRejectedAt()));
    }

    private ImportConnection mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        UserId userId = UserId.of(resultSet.getObject("user_id", UUID.class));
        IngestionSource source = source(resultSet);
        return new ImportConnection(
                ImportConnectionId.of(resultSet.getObject("id", UUID.class)),
                userId,
                source,
                token(resultSet, userId, source),
                resultSet.getBoolean("enabled"),
                resultSet.getObject("configured_at", LocalDate.class),
                resultSet.getObject("last_record_date", LocalDate.class),
                toInstant(resultSet.getObject("last_run_at", OffsetDateTime.class)),
                toInstant(resultSet.getObject("credentials_rejected_at", OffsetDateTime.class)));
    }

    /** `timestamptz` va e viene come `Timestamp`/`OffsetDateTime`; il dominio parla in `Instant`. */
    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static IngestionSource source(ResultSet resultSet) throws SQLException {
        String raw = resultSet.getString("source");
        try {
            return KebabCase.to(IngestionSource.class, raw);
        } catch (IllegalArgumentException cause) {
            throw CorruptedDataException.invalidColumn(TABLE, "source", raw, cause);
        }
    }

    private PersonalToken token(ResultSet resultSet, UserId userId, IngestionSource source)
            throws SQLException {
        String decrypted = cipher.decrypt(resultSet.getString("encrypted_secret"), context(userId, source));
        try {
            return new PersonalToken(decrypted);
        } catch (IllegalArgumentException cause) {
            // Il messaggio non cita il valore: è il segreto in chiaro.
            throw new CorruptedDataException(
                    "Segreto non valido in %s.encrypted_secret.".formatted(TABLE), cause);
        }
    }

    /** Dato autenticato aggiuntivo: lega il valore cifrato alla riga in cui è nato. */
    private static String context(UserId userId, IngestionSource source) {
        return userId + ":" + KebabCase.from(source);
    }
}
