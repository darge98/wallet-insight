package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.movements.domain.Transfer;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AmountDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryGroupDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.ConvertedAmountDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.TransferDto;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovementMapperTest {

    private static final UserId UTENTE = UserId.newId();
    private static final AccountId CONTO = AccountId.newId();
    /** Il conto 'acc-1' della sorgente, tradotto nell'identificatore di Wallet Insights. */
    private static final Map<String, AccountId> CONTI = Map.of("acc-1", CONTO);
    private static final CategoryId RISTORANTI = CategoryId.newId();
    private static final Map<String, CategoryId> CATEGORIE = Map.of("cat-1", RISTORANTI);

    private final MovementMapper mapper = new MovementMapper();

    @Test
    void converte_l_importo_decimale_in_centesimi_interi() {
        Movement movement = mappa(record().amount("-9.99").build());

        assertThat(movement.amount().amount()).isEqualTo(-999L);
        assertThat(movement.amount().currency()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void converte_anche_gli_importi_senza_decimali() {
        assertThat(mappa(record().amount("-28").build()).amount().amount()).isEqualTo(-2800L);
    }

    /**
     * Questo è il caso che distingue davvero `BigDecimal` da `double`.
     *
     * `Math.round(1.005 * 100)` in Java dà 100, perché 1.005 non è
     * rappresentabile e il double più vicino è leggermente più piccolo.
     * Spostando la virgola sul BigDecimal il valore resta 1.005 e
     * l'arrotondamento a metà sale a 101.
     *
     * Sui 1672 movimenti reali le due strade coincidono: nessuno ha più di due
     * decimali. Il test serve a impedire che qualcuno "semplifichi" il mapper
     * con un double senza che nulla se ne accorga.
     */
    @Test
    void arrotonda_per_eccesso_a_meta_senza_passare_per_un_double() {
        assertThat(mappa(record().amount("1.005").build()).amount().amount()).isEqualTo(101L);
        assertThat(Math.round(1.005 * 100)).isEqualTo(100L);
    }

    @Test
    void conserva_l_istante_della_sorgente_e_lascia_il_giorno_al_fuso_di_chi_guarda() {
        Movement movement = mappa(record().recordDate("2026-09-15T23:30:00Z").build());

        assertThat(movement.recordedAt()).isEqualTo(Instant.parse("2026-09-15T23:30:00Z"));
        // Le 23:30 UTC del 15 sono l'1:30 del 16 a Roma.
        assertThat(movement.dateIn(ZoneId.of("Europe/Rome"))).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void traduce_i_valori_dichiarati_dalla_specifica() {
        Movement movement =
                mappa(record().recordType("income").recordState("waitForAssign").build());

        assertThat(movement.direction()).isEqualTo(MovementDirection.INCOME);
        assertThat(movement.state()).isEqualTo(MovementState.WAIT_FOR_ASSIGN);
    }

    @Test
    void non_scarta_il_movimento_quando_un_valore_non_e_previsto() {
        Movement movement = mappa(record().recordState("qualcosa-di-nuovo").build());

        // L'importo e' un fatto: perderlo falserebbe il saldo, mentre uno stato
        // sconosciuto e' solo un'etichetta che non sappiamo ancora leggere.
        assertThat(movement.state()).isEqualTo(MovementState.UNKNOWN);
        assertThat(movement.externalId()).isEqualTo("rec-1");
    }

    @Test
    void un_movimento_di_un_conto_sconosciuto_resta_fuori() {
        // Non e' salvabile: poggia su un conto che non esiste qui. Torna null e chi
        // chiama lo conta, invece di far fallire l'import di tutti gli altri.
        assertThat(mapper.toMovement(record().build(), UTENTE, IngestionSource.BUDGET_BAKERS,
                Map.of("acc-altro", CONTO), CATEGORIE)).isNull();
    }

    @Test
    void aggancia_il_movimento_al_conto_di_margine_non_a_quello_della_sorgente() {
        Movement movement = mappa(record().build());

        assertThat(movement.accountId()).isEqualTo(CONTO);
        assertThat(movement.userId()).isEqualTo(UTENTE);
        assertThat(movement.source()).isEqualTo(IngestionSource.BUDGET_BAKERS);
    }

    @Test
    void lascia_null_il_giroconto_quando_il_movimento_non_lo_e() {
        Movement movement = mappa(record().transfer(null).build());

        assertThat(movement.transfer()).isNull();
        assertThat(movement.isTransfer()).isFalse();
    }

    @Test
    void riconosce_un_giroconto_dichiarato_ma_non_collegabile() {
        // 14 movimenti su 1672 hanno transfer.type senza transferId.
        Movement movement = mappa(record().transfer(new TransferDto("unpaired", null)).build());

        assertThat(movement.isTransfer()).isTrue();
        assertThat(movement.transfer().state()).isEqualTo(Transfer.TransferState.UNPAIRED);
        assertThat(movement.transfer().isPairable()).isFalse();
    }

    @Test
    void conserva_i_campi_facoltativi_mancanti() {
        Movement movement = mappa(record().note(null).counterParty(null).build());

        // Assenti dalla sorgente, e nessuno li ha riscritti: non c'e' niente da mostrare.
        assertThat(movement.description()).isNull();
        assertThat(movement.counterParty()).isNull();
        // E soprattutto non sono finiti nel valore dell'utente: un import non ne scrive mai.

    }

    @Test
    void aggancia_la_categoria_di_margine_e_ne_tiene_anche_la_traccia_grezza() {
        Movement movement = mappa(record().build());

        // L'identificatore e' quello di Wallet Insights, non quello della sorgente.
        assertThat(movement.classification().category()).isEqualTo(RISTORANTI);
        // E la traccia resta: permette di ricostruire l'aggancio senza rileggere nulla.
        assertThat(movement.classification().sourceExternalId()).isEqualTo("cat-1");
        assertThat(movement.classification().sourceName()).isEqualTo("Ristoranti");
        // La riclassificazione dell'utente non la scrive nessun import.
    }

    @Test
    void un_movimento_con_una_categoria_sconosciuta_entra_lo_stesso() {
        // A differenza di un conto mancante: senza conto il movimento non ha dove
        // stare, senza categoria e' solo un movimento da classificare.
        Movement movement = mapper.toMovement(record().build(), UTENTE,
                IngestionSource.BUDGET_BAKERS, CONTI, Map.of("cat-altra", RISTORANTI));

        assertThat(movement).isNotNull();
        assertThat(movement.classification().category()).isNull();
        assertThat(movement.classification().sourceName()).isEqualTo("Ristoranti");
    }

    @Test
    void un_movimento_in_euro_e_gia_convertito() {
        Movement movement = mappa(record().amount("-9.99").build());

        assertThat(movement.convertedAmount()).isEqualTo(movement.amount());
    }

    @Test
    void un_movimento_in_valuta_porta_anche_l_importo_in_euro_del_suo_giorno() {
        Movement movement = mappa(record().amount("-50").currency("USD")
                .converted(new ConvertedAmountDto(new BigDecimal("-45.678912"), "EUR",
                        new BigDecimal("0.91357824"), null))
                .build());

        assertThat(movement.amount()).isEqualTo(Money.of(-5000L, CurrencyCode.USD));
        assertThat(movement.convertedAmount()).isEqualTo(Money.of(-4568L, CurrencyCode.EUR));
    }

    @Test
    void senza_il_cambio_del_giorno_l_import_si_ferma() {
        RecordDto senzaCambio = record().amount("-50").currency("USD")
                .converted(new ConvertedAmountDto(null, "EUR", null, "Rate unavailable"))
                .build();

        assertThatThrownBy(() -> mappa(senzaCambio))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rate unavailable");
        assertThatThrownBy(() -> mappa(record().amount("-50").currency("USD").build()))
                .isInstanceOf(IllegalStateException.class);
    }

    private Movement mappa(RecordDto dto) {
        return mapper.toMovement(dto, UTENTE, IngestionSource.BUDGET_BAKERS, CONTI, CATEGORIE);
    }

    private Builder record() {
        return new Builder();
    }

    /** Costruisce un RecordDto plausibile, così ogni test tocca solo ciò che verifica. */
    private static final class Builder {
        private String amount = "-9.99";
        private String currency = "EUR";
        private ConvertedAmountDto converted;
        private String recordDate = "2026-09-15T00:00:00Z";
        private String recordType = "expense";
        private String recordState = "cleared";
        private String source = "web";
        private String note = "una nota";
        private String counterParty = "PayPal EUR";
        private TransferDto transfer = new TransferDto("unpaired", "tr-1");

        Builder amount(String value) { this.amount = value; return this; }
        Builder currency(String value) { this.currency = value; return this; }
        Builder converted(ConvertedAmountDto value) { this.converted = value; return this; }
        Builder recordDate(String value) { this.recordDate = value; return this; }
        Builder recordType(String value) { this.recordType = value; return this; }
        Builder recordState(String value) { this.recordState = value; return this; }
        Builder note(String value) { this.note = value; return this; }
        Builder counterParty(String value) { this.counterParty = value; return this; }
        Builder transfer(TransferDto value) { this.transfer = value; return this; }

        RecordDto build() {
            return new RecordDto("rec-1", "acc-1", "Credem", true, note, counterParty,
                    new AmountDto(new BigDecimal(amount), currency), converted,
                    Instant.parse(recordDate),
                    new CategoryDto("cat-1", "Ristoranti", new CategoryGroupDto("food_and_drinks", "Food"), "#fff", null),
                    recordState, recordType, List.of(), transfer, source,
                    Instant.parse("2026-09-15T10:00:00Z"), Instant.parse("2026-09-15T16:00:00Z"));
        }
    }
}
