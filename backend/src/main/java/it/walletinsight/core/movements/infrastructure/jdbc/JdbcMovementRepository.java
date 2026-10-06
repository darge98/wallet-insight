package it.walletinsight.core.movements.infrastructure.jdbc;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.domain.CategorySpending;
import it.walletinsight.core.movements.domain.Classification;
import it.walletinsight.core.movements.domain.ConvertedMovements;
import it.walletinsight.core.movements.domain.CounterPartySpending;
import it.walletinsight.core.movements.domain.CumulativeExpensePoint;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.movements.domain.MovementId;
import it.walletinsight.core.movements.domain.MovementKind;
import it.walletinsight.core.movements.domain.MovementRepository;
import it.walletinsight.core.movements.domain.MovementSort;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.movements.domain.MovementTotals;
import it.walletinsight.core.movements.domain.Transfer;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.shared.source.IngestionSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter JDBC dei movimenti: tutto il loro SQL vive qui.
 *
 * Usa anche {@link NamedParameterJdbcTemplate} accanto a {@link JdbcClient}, che
 * negli altri moduli basta: `JdbcClient` non sa fare batch, e un import porta
 * qualche migliaio di righe per volta. Mandarle una per una sarebbe un viaggio di
 * rete per riga.
 */
@Repository
class JdbcMovementRepository implements MovementRepository {

    private static final String TABLE = "movements";

    /**
     * Cosa conta come denaro davvero mosso: né un movimento annullato né una gamba
     * di giroconto.
     *
     * Gli annullati non sono avvenuti. I giroconti sì, ma fra due conti
     * dell'utente: sul netto le due gambe si annullano comunque, e contarle
     * gonfierebbe insieme entrate e uscite con denaro che non è entrato e non è
     * uscito da nessuna parte.
     */
    private static final String COUNTS_TOWARDS_TOTALS = "state <> 'void' and transfer_state is null";

    /**
     * Cosa è un'uscita, e la definizione è la stessa di {@code MovementKind.of}.
     *
     * `direction <> 'income'` e non `= 'expense'`: un verso che la sorgente non
     * dichiara resta un'uscita, perché il denaro se n'è andato comunque.
     * Scriverlo al positivo lascerebbe quei movimenti fuori da ogni filtro e da
     * ogni totale — invisibili, che è il modo peggiore di sbagliare.
     */
    private static final String IS_EXPENSE = COUNTS_TOWARDS_TOTALS + " and direction <> 'income'";

    private static final String COLUMNS = """
            id, user_id, account_id, source, external_id, amount_cents, currency_code,
            converted_amount_cents, recorded_at, direction, state, description, counter_party,
            category_id, source_category_id, source_category_name, transfer_state,
            transfer_external_id
            """;

    /**
     * Una sola istruzione per creare *o* riallineare.
     *
     * `on conflict` sul vincolo che l'import già garantisce: è il database a
     * decidere se la riga c'è, senza una lettura preventiva per ognuna. La `where`
     * in fondo è ciò che rende onesto `updated_at`: se la sorgente non ha cambiato
     * niente — il caso normale, visto che ogni giro rilegge il giorno precedente —
     * non si scrive, e la riga risulta non toccata.
     *
     * `id` non compare fra i campi aggiornati: l'identificatore assegnato la prima
     * volta resta, ed è quello a cui si appoggia tutto ciò che viene dopo.
     *
     * `description`, `counter_party` e `category_id` compaiono fra i campi
     * inseriti ma **non** fra quelli aggiornati, ed è tutta la regola introdotta
     * con V11: l'import li scrive quando il movimento entra, e da quel momento
     * non li tocca più. Non servono `case` né letture preventive per proteggerli —
     * basta non nominarli nel `set`, e nessun giro successivo potrà calpestarli.
     * Per lo stesso motivo restano fuori dal confronto `is distinct from`: se la
     * sorgente cambia una descrizione che non guardiamo più, la riga non è
     * cambiata per noi, e contarla direbbe che l'import ha fatto qualcosa.
     */
    private static final String UPSERT = """
            insert into movements (id, user_id, account_id, source, external_id, amount_cents,
                                   currency_code, converted_amount_cents, recorded_at, direction, state,
                                   description, counter_party, category_id, source_category_id,
                                   source_category_name, transfer_state, transfer_external_id)
            values (:id, :userId, :accountId, :source, :externalId, :amountCents,
                    :currencyCode, :convertedAmountCents, :recordedAt, :direction, :state, :description,
                    :counterParty, :categoryId, :categoryExternalId, :categoryName,
                    :transferState, :transferExternalId)
            on conflict (user_id, source, external_id) do update
               set account_id = excluded.account_id,
                   amount_cents = excluded.amount_cents,
                   currency_code = excluded.currency_code,
                   converted_amount_cents = excluded.converted_amount_cents,
                   recorded_at = excluded.recorded_at,
                   direction = excluded.direction,
                   state = excluded.state,
                   source_category_id = excluded.source_category_id,
                   source_category_name = excluded.source_category_name,
                   transfer_state = excluded.transfer_state,
                   transfer_external_id = excluded.transfer_external_id,
                   updated_at = now()
             where (movements.account_id, movements.amount_cents, movements.currency_code,
                    movements.converted_amount_cents, movements.recorded_at, movements.direction,
                    movements.state, movements.source_category_id, movements.source_category_name,
                    movements.transfer_state, movements.transfer_external_id)
                is distinct from
                   (excluded.account_id, excluded.amount_cents, excluded.currency_code,
                    excluded.converted_amount_cents, excluded.recorded_at, excluded.direction,
                    excluded.state, excluded.source_category_id, excluded.source_category_name,
                    excluded.transfer_state, excluded.transfer_external_id)
            """;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batch;

    JdbcMovementRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    @Override
    public int upsertAll(List<Movement> movements) {
        if (movements.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] parametri = movements.stream()
                .map(JdbcMovementRepository::parametersOf)
                .toArray(SqlParameterSource[]::new);

        int[] righe = batch.batchUpdate(UPSERT, parametri);
        // Un 0 e' una riga gia' allineata: contarla come scritta direbbe che l'import
        // ha fatto qualcosa quando non ha fatto niente.
        int scritte = 0;
        for (int riga : righe) {
            if (riga > 0) {
                scritte++;
            }
        }
        return scritte;
    }

    @Override
    public List<Movement> deleteMissingUncleared(UserId userId, IngestionSource source,
                                                 LocalDate from, LocalDate toExclusive,
                                                 Collection<AccountId> accounts,
                                                 Collection<String> keptExternalIds) {
        if (accounts.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                delete from movements
                 where user_id = :userId
                   and source = :source
                   and state = 'uncleared'
                   and recorded_at >= :from and recorded_at < :toExclusive
                   and account_id = any(:accounts)
                   and not (external_id = any(:kept))
                returning %s
                """.formatted(COLUMNS))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                // Giorni UTC, come la finestra chiesta alla sorgente.
                .param("from", startOf(from, ZoneOffset.UTC))
                .param("toExclusive", startOf(toExclusive, ZoneOffset.UTC))
                .param("accounts", accounts.stream().map(AccountId::value).toArray(UUID[]::new))
                .param("kept", keptExternalIds.toArray(String[]::new))
                .query(this::mapRow)
                .list();
    }

    @Override
    public int linkCategoriesFromSource(UserId userId, IngestionSource source) {
        // `category_id is null` e non "tutti": non e' una riscrittura della
        // classificazione, e' il completamento di quella che manca. Un movimento gia'
        // agganciato lo lascia stare, compresa la riclassificazione dell'utente — che
        // sta comunque in un'altra colonna e qui non viene nemmeno nominata.
        return jdbc.sql("""
                update movements m
                   set category_id = c.category_id,
                       updated_at = now()
                  from source_categories c
                 where m.user_id = :userId
                   and m.source = :source
                   and m.category_id is null
                   and m.source_category_id is not null
                   and c.user_id = m.user_id
                   and c.source = m.source
                   and c.external_id = m.source_category_id
                   and c.category_id is not null
                """)
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .update();
    }

    @Override
    public int reassignCategory(UserId userId, CategoryId from, CategoryId to) {
        return jdbc.sql("""
                update movements set category_id = :to, updated_at = now()
                 where user_id = :userId and category_id = :from
                """)
                .param("userId", userId.value())
                .param("from", from.value())
                .param("to", to.value())
                .update();
    }

    @Override
    public int reassignFromSource(UserId userId, IngestionSource source, String sourceCategoryExternalId,
                                  CategoryId from, CategoryId to) {
        // Solo quelli ancora dove l'aggancio li aveva messi: un movimento spostato a
        // mano in un'altra categoria resta dov'è.
        StatementSpec update = jdbc.sql("""
                update movements set category_id = :to, updated_at = now()
                 where user_id = :userId and source = :source
                   and source_category_id = :externalId
                   and %s
                """.formatted(from == null ? "category_id is null" : "category_id = :from"))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .param("externalId", sourceCategoryExternalId)
                .param("to", to.value());
        return (from == null ? update : update.param("from", from.value())).update();
    }

    @Override
    public Page<Movement> findPage(
            UserId userId, ZoneId zone, MovementFilter filter, MovementSort sort, PageRequest page) {
        // I criteri si compongono una volta sola e valgono sia per la pagina sia per
        // il conteggio: due `where` scritte a mano divergerebbero al primo filtro nuovo,
        // e il totale direbbe una cosa diversa da quello che si vede.
        Criteria criteria = Criteria.of(filter, zone);

        long totale = criteria
                .apply(jdbc.sql("select count(*) from %s where %s"
                        .formatted(TABLE, criteria.whereClause())))
                .param("userId", userId.value())
                .query(Long.class)
                .single();
        if (totale == 0) {
            return Page.of(List.of(), 0L, page);
        }

        List<Movement> items = criteria
                .apply(jdbc.sql("""
                        select %s from %s
                         where %s
                         order by %s
                         limit :limit offset :offset
                        """.formatted(COLUMNS, TABLE, criteria.whereClause(), orderBy(sort))))
                .param("userId", userId.value())
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(this::mapRow)
                .list();
        return Page.of(items, totale, page);
    }

    @Override
    public MovementTotals totals(UserId userId, ZoneId zone, MovementFilter filter) {
        // Gli stessi criteri dell'elenco, cosi' i numeri qui sopra e le righe qui sotto
        // parlano dello stesso insieme. `count(*)` conta tutto — giroconti compresi —
        // perche' e' lo stesso numero che la paginazione mostra; le somme invece li
        // lasciano fuori, insieme agli annullati, perche' non sono denaro entrato o uscito.
        Criteria criteria = Criteria.of(filter, zone);
        return criteria
                .apply(jdbc.sql("""
                        select coalesce(sum(converted_amount_cents)
                                            filter (where %s and direction = 'income'), 0) as entrate,
                               -coalesce(sum(converted_amount_cents) filter (where %s), 0) as uscite,
                               count(*) as conteggio
                          from %s
                         where %s
                        """.formatted(COUNTS_TOWARDS_TOTALS, IS_EXPENSE, TABLE,
                                criteria.whereClause())))
                .param("userId", userId.value())
                .query((rs, rowNum) -> {
                    Money entrate = Money.of(rs.getLong("entrate"));
                    Money uscite = Money.of(rs.getLong("uscite"));
                    return new MovementTotals(
                            entrate, uscite, entrate.minus(uscite), rs.getLong("conteggio"));
                })
                .single();
    }

    @Override
    public List<CategorySpending> expensesByCategory(
            UserId userId, ZoneId zone, MovementFilter filter, int limit) {
        // Raggruppa per la categoria *mostrata*, la stessa su cui filtra l'elenco: una
        // classifica costruita su un valore diverso da quello visibile sembrerebbe
        // sbagliata anche essendo giusta. I movimenti non classificati restano fuori —
        // "senza categoria" non e' un posto dove sono andati i soldi.
        Criteria criteria = Criteria.of(filter, zone);
        return criteria
                .apply(jdbc.sql("""
                        select category_id as categoria,
                               -sum(converted_amount_cents) as totale,
                               count(*) as conteggio
                          from %s
                         where %s
                           and %s
                           and category_id is not null
                         group by 1
                         order by totale desc
                         limit :limit
                        """.formatted(TABLE, criteria.whereClause(), IS_EXPENSE)))
                .param("userId", userId.value())
                .param("limit", limit)
                .query((rs, rowNum) -> new CategorySpending(
                        CategoryId.of(rs.getObject("categoria", UUID.class)),
                        Money.of(rs.getLong("totale")),
                        rs.getLong("conteggio")))
                .list();
    }

    @Override
    public List<CounterPartySpending> topCounterParties(
            UserId userId, ZoneId zone, MovementFilter filter, int limit) {
        // Chi non ha controparte resta fuori invece di finire in un gruppo "senza nome":
        // sarebbe quasi sempre il primo della classifica e non direbbe niente. Misurata,
        // la controparte c'e' su 1082 movimenti su 1678.
        Criteria criteria = Criteria.of(filter, zone);
        return criteria
                .apply(jdbc.sql("""
                        select counter_party as controparte,
                               -sum(converted_amount_cents) as totale,
                               count(*) as conteggio
                          from %s
                         where %s
                           and %s
                           and counter_party is not null
                         group by 1
                         order by totale desc
                         limit :limit
                        """.formatted(TABLE, criteria.whereClause(), IS_EXPENSE)))
                .param("userId", userId.value())
                .param("limit", limit)
                .query((rs, rowNum) -> new CounterPartySpending(
                        rs.getString("controparte"),
                        Money.of(rs.getLong("totale")),
                        rs.getLong("conteggio")))
                .list();
    }

    @Override
    public List<CumulativeExpensePoint> cumulativeExpenses(UserId userId, ZoneId zone, DateRange period) {
        // Il progressivo lo fa la finestra: un `sum() over (order by ...)` sopra il
        // raggruppamento per giorno. Senza, servirebbe una riga per giorno portata in
        // memoria e un ciclo che la somma — lo stesso risultato, pagato due volte.
        //
        // I giorni senza spese non compaiono: fra due punti una curva cumulativa e'
        // piatta da se', e inventare righe a zero non aggiungerebbe informazione.
        return jdbc.sql("""
                select giorno, sum(uscite) over (order by giorno) as cumulato
                  from (select (recorded_at at time zone :zone)::date as giorno,
                               -sum(converted_amount_cents) as uscite
                          from %s
                         where user_id = :userId and recorded_at >= :from and recorded_at < :toExclusive
                           and %s
                         group by 1) as per_giorno
                 order by giorno
                """.formatted(TABLE, IS_EXPENSE))
                .param("userId", userId.value())
                .param("zone", zone.getId())
                .param("from", startOf(period.from(), zone))
                .param("toExclusive", startOf(period.to().plusDays(1), zone))
                .query((rs, rowNum) -> new CumulativeExpensePoint(
                        rs.getObject("giorno", java.time.LocalDate.class),
                        Money.of(rs.getLong("cumulato"))))
                .list();
    }

    /**
     * L'`order by`, costruito dall'enum e mai da una stringa che arriva da fuori.
     *
     * L'importo si ordina per valore assoluto: «i movimenti piu' grandi» sono i piu'
     * grandi, non tutte le entrate prima di tutte le uscite. La controparte si ordina
     * per quella mostrata e in minuscolo, altrimenti 'amazon' e 'Amazon' finirebbero
     * in due punti lontani dell'elenco.
     *
     * L'id chiude sempre l'ordinamento: senza, due righe uguali sul criterio scelto
     * potrebbero cambiare posto fra una pagina e l'altra, e un movimento comparirebbe
     * due volte o sparirebbe.
     */
    private static String orderBy(MovementSort sort) {
        String colonna = switch (sort.field()) {
            case DATE -> "recorded_at";
            case AMOUNT -> "abs(converted_amount_cents)";
            case COUNTER_PARTY -> "lower(counter_party)";
        };
        String verso = sort.direction() == MovementSort.Direction.ASC ? "asc" : "desc";
        return "%s %s, id %s".formatted(colonna, verso, verso);
    }

    @Override
    public Optional<Movement> findById(UserId userId, MovementId id) {
        return jdbc.sql("select %s from %s where user_id = :userId and id = :id"
                .formatted(COLUMNS, TABLE))
                .param("userId", userId.value())
                .param("id", id.value())
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void updateEditable(Movement movement) {
        // Tre colonne e nessun'altra. Non e' una scorciatoia: e' cio' che garantisce
        // che una correzione non possa riscrivere un fatto arrivato dall'import,
        // nemmeno se l'aggregato che arriva qui fosse stato costruito male.
        jdbc.sql("""
                update movements
                   set description = :description,
                       counter_party = :counterParty,
                       category_id = :categoryId,
                       updated_at = now()
                 where id = :id and user_id = :userId
                """)
                .param("id", movement.id().value())
                .param("userId", movement.userId().value())
                .param("description", movement.description())
                .param("counterParty", movement.counterParty())
                .param("categoryId", idOrNull(movement.classification().category()))
                .update();
    }

    @Override
    public Map<AccountId, Long> sumByAccount(UserId userId) {
        // Gli annullati fuori dalla somma: sono movimenti che non sono avvenuti.
        Map<AccountId, Long> somme = new HashMap<>();
        jdbc.sql("""
                select account_id, sum(amount_cents) as totale
                  from %s
                 where user_id = :userId and state <> 'void'
                 group by account_id
                """.formatted(TABLE))
                .param("userId", userId.value())
                .query((rs, rowNum) -> somme.put(
                        AccountId.of(rs.getObject("account_id", UUID.class)),
                        rs.getLong("totale")))
                .list();
        return somme;
    }

    @Override
    public Map<AccountId, ConvertedMovements> convertedByAccount(UserId userId) {
        // Il cambio del primo movimento si ricava dai due importi che abbiamo già, invece
        // di conservare il tasso: serve solo al saldo iniziale dei conti in valuta.
        Map<AccountId, ConvertedMovements> somme = new HashMap<>();
        jdbc.sql("""
                select account_id,
                       coalesce(sum(converted_amount_cents) filter (where state <> 'void'), 0) as totale,
                       (array_agg(converted_amount_cents::numeric / amount_cents order by recorded_at, id)
                            filter (where amount_cents <> 0))[1] as primo_cambio
                  from %s
                 where user_id = :userId
                 group by account_id
                """.formatted(TABLE))
                .param("userId", userId.value())
                .query((rs, rowNum) -> somme.put(
                        AccountId.of(rs.getObject("account_id", UUID.class)),
                        new ConvertedMovements(
                                Money.of(rs.getLong("totale"), Movement.TOTALS_CURRENCY),
                                rs.getBigDecimal("primo_cambio"))))
                .list();
        return somme;
    }

    @Override
    public long countByUserAndSource(UserId userId, IngestionSource source) {
        return jdbc.sql("select count(*) from %s where user_id = :userId and source = :source"
                .formatted(TABLE))
                .param("userId", userId.value())
                .param("source", KebabCase.from(source))
                .query(Long.class)
                .single();
    }

    /**
     * I criteri di un {@link MovementFilter} tradotti in SQL una volta sola.
     *
     * Esiste perché la stessa `where` serve a più query — la pagina, il suo
     * totale e le classifiche che le stanno accanto — e tenerle allineate a mano è il modo in cui un elenco finisce per
     * dichiarare più risultati di quanti ne mostra.
     */
    private record Criteria(String whereClause, Map<String, Object> parameters) {

        static Criteria of(MovementFilter filter, ZoneId zone) {
            List<String> condizioni = new ArrayList<>();
            Map<String, Object> parametri = new HashMap<>();
            condizioni.add("user_id = :userId");

            DateRange period = filter.period();
            if (period != null) {
                // Il periodo è in giorni del calendario dell'utente: diventa un intervallo
                // di istanti, così l'indice su `recorded_at` resta utilizzabile.
                condizioni.add("recorded_at >= :from and recorded_at < :toExclusive");
                parametri.put("from", startOf(period.from(), zone));
                parametri.put("toExclusive", startOf(period.to().plusDays(1), zone));
            }
            if (!filter.accountIds().isEmpty()) {
                condizioni.add("account_id in (:accountIds)");
                parametri.put("accountIds", filter.accountIds().stream()
                        .map(AccountId::value).toList());
            }
            if (!filter.categoryIds().isEmpty()) {
                // La categoria mostrata, non quella della sorgente: e' l'espressione su
                // cui poggia l'indice `movements_per_categoria`.
                condizioni.add("category_id in (:categoryIds)");
                parametri.put("categoryIds", filter.categoryIds().stream()
                        .map(CategoryId::value).toList());
            }
            if (!filter.kinds().isEmpty()) {
                // Piu' nature scelte insieme vogliono dire "una qualsiasi di queste":
                // un movimento ne ha una sola, e metterle in and non darebbe mai nulla.
                condizioni.add(filter.kinds().stream()
                        .sorted()
                        .map(Criteria::predicateFor)
                        .collect(Collectors.joining(" or ", "(", ")")));
            }
            return new Criteria(String.join(" and ", condizioni), Map.copyOf(parametri));
        }

        /**
         * La natura di un movimento tradotta in SQL, con le stesse regole di
         * {@code MovementKind.of}: il giroconto vince sul verso, e tutto cio' che non
         * e' un'entrata dichiarata e' un'uscita.
         *
         * Nessun parametro da legare: i valori vengono dall'enum, non da fuori.
         */
        private static String predicateFor(MovementKind kind) {
            return switch (kind) {
                case TRANSFER -> "transfer_state is not null";
                case INCOME -> "(transfer_state is null and direction = 'income')";
                case EXPENSE -> "(transfer_state is null and direction <> 'income')";
            };
        }

        StatementSpec apply(StatementSpec spec) {
            StatementSpec risultato = spec;
            for (Map.Entry<String, Object> parametro : parameters.entrySet()) {
                risultato = risultato.param(parametro.getKey(), parametro.getValue());
            }
            return risultato;
        }
    }

    /** L'istante in cui comincia quel giorno nel fuso indicato. */
    private static Timestamp startOf(LocalDate day, ZoneId zone) {
        return Timestamp.from(day.atStartOfDay(zone).toInstant());
    }

    private static UUID idOrNull(CategoryId categoryId) {
        return categoryId == null ? null : categoryId.value();
    }

    private static SqlParameterSource parametersOf(Movement movement) {
        Transfer transfer = movement.transfer();
        Classification classification = movement.classification();
        return new MapSqlParameterSource()
                .addValue("id", movement.id().value())
                .addValue("userId", movement.userId().value())
                .addValue("accountId", movement.accountId().value())
                .addValue("source", KebabCase.from(movement.source()))
                .addValue("externalId", movement.externalId())
                .addValue("amountCents", movement.amount().amount())
                .addValue("currencyCode", movement.amount().currency().name())
                .addValue("convertedAmountCents", movement.convertedAmount().amount())
                .addValue("recordedAt", Timestamp.from(movement.recordedAt()))
                .addValue("direction", KebabCase.from(movement.direction()))
                .addValue("state", KebabCase.from(movement.state()))
                .addValue("description", movement.description())
                .addValue("counterParty", movement.counterParty())
                .addValue("categoryId", idOrNull(classification.category()))
                .addValue("categoryExternalId", classification.sourceExternalId())
                .addValue("categoryName", classification.sourceName())
                .addValue("transferState", transfer == null ? null : KebabCase.from(transfer.state()))
                .addValue("transferExternalId", transfer == null ? null : transfer.externalId());
    }

    private Movement mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        CurrencyCode currency =
                enumOf(CurrencyCode.class, resultSet.getString("currency_code"), "currency_code");
        String transferState = resultSet.getString("transfer_state");
        return new Movement(
                MovementId.of(resultSet.getObject("id", UUID.class)),
                UserId.of(resultSet.getObject("user_id", UUID.class)),
                AccountId.of(resultSet.getObject("account_id", UUID.class)),
                enumOf(IngestionSource.class, resultSet.getString("source"), "source"),
                resultSet.getString("external_id"),
                Money.of(resultSet.getLong("amount_cents"), currency),
                Money.of(resultSet.getLong("converted_amount_cents"), Movement.TOTALS_CURRENCY),
                resultSet.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                enumOf(MovementDirection.class, resultSet.getString("direction"), "direction"),
                enumOf(MovementState.class, resultSet.getString("state"), "state"),
                resultSet.getString("description"),
                resultSet.getString("counter_party"),
                new Classification(
                        categoryOrNull(resultSet, "category_id"),
                        resultSet.getString("source_category_id"),
                        resultSet.getString("source_category_name")),
                transferState == null
                        ? null
                        : new Transfer(
                                enumOf(Transfer.TransferState.class, transferState, "transfer_state"),
                                resultSet.getString("transfer_external_id")));
    }

    private static CategoryId categoryOrNull(ResultSet resultSet, String column) throws SQLException {
        UUID value = resultSet.getObject(column, UUID.class);
        return value == null ? null : CategoryId.of(value);
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
