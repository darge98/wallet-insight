package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordsPageDto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la lettura del JSON come arriva davvero da BudgetBakers, con le sue
 * asimmetrie: `transfer` presente ma null, `note` e `counterParty` che
 * spariscono come chiave, campi dichiarati nella specifica che non modelliamo.
 *
 * Usa la stessa configurazione della produzione, non un ObjectMapper di
 * comodo: se quella cambia, questo test se ne accorge.
 */
class RecordsPageDtoJsonTest {

    private final ObjectMapper objectMapper = new BudgetBakersHttpConfiguration().budgetBakersObjectMapper();

    private static final String PAGINA = """
            {
              "records": [
                {
                  "id": "rec-1",
                  "accountId": "acc-1",
                  "accountName": "Credem",
                  "accountIsBankSync": true,
                  "amount": { "value": -9.99, "currencyCode": "EUR" },
                  "recordDate": "2026-09-15T00:00:00.000Z",
                  "category": {
                    "id": "cat-1",
                    "name": "Ristoranti",
                    "group": { "id": "food_and_drinks", "name": "Food & Drinks" },
                    "color": "#cccccc"
                  },
                  "recordState": "cleared",
                  "recordType": "expense",
                  "labels": [],
                  "transfer": null,
                  "source": "web",
                  "createdAt": "2026-09-15T10:24:09.619Z",
                  "updatedAt": "2026-09-15T16:18:37.439Z",
                  "place": { "latitude": 44.7, "longitude": 10.6 }
                }
              ],
              "limit": 200,
              "offset": 0,
              "nextOffset": 200,
              "total": 1672,
              "appliedRecordDateFilters": ["gte.2000-01-01T00:00:00.000Z"]
            }
            """;

    @Test
    void legge_una_pagina_di_movimenti() throws Exception {
        RecordsPageDto pagina = objectMapper.readValue(PAGINA, RecordsPageDto.class);

        assertThat(pagina.records()).hasSize(1);
        assertThat(pagina.total()).isEqualTo(1672);
        assertThat(pagina.appliedRecordDateFilters()).containsExactly("gte.2000-01-01T00:00:00.000Z");
    }

    @Test
    void riconosce_che_ci_sono_altre_pagine_dal_nextOffset() throws Exception {
        RecordsPageDto conAltre = objectMapper.readValue(PAGINA, RecordsPageDto.class);
        RecordsPageDto ultima = objectMapper.readValue(PAGINA.replace("\"nextOffset\": 200,", ""), RecordsPageDto.class);

        assertThat(conAltre.hasMore()).isTrue();
        assertThat(ultima.hasMore()).isFalse();
    }

    @Test
    void legge_l_importo_come_decimale_esatto_e_non_come_double() throws Exception {
        RecordDto movimento = objectMapper.readValue(PAGINA, RecordsPageDto.class).records().getFirst();

        assertThat(movimento.amount().value()).isEqualByComparingTo(new BigDecimal("-9.99"));
        assertThat(movimento.amount().value().scale()).isEqualTo(2);
    }

    @Test
    void accetta_transfer_presente_ma_nullo() throws Exception {
        RecordDto movimento = objectMapper.readValue(PAGINA, RecordsPageDto.class).records().getFirst();

        assertThat(movimento.transfer()).isNull();
    }

    @Test
    void accetta_note_e_counterParty_assenti_come_chiave() throws Exception {
        RecordDto movimento = objectMapper.readValue(PAGINA, RecordsPageDto.class).records().getFirst();

        assertThat(movimento.note()).isNull();
        assertThat(movimento.counterParty()).isNull();
    }

    @Test
    void ignora_i_campi_della_specifica_che_non_modelliamo() throws Exception {
        // `place` è nel JSON di prova: senza FAIL_ON_UNKNOWN_PROPERTIES disattivato
        // la lettura fallirebbe, e un campo in più bloccherebbe tutto l'import.
        RecordDto movimento = objectMapper.readValue(PAGINA, RecordsPageDto.class).records().getFirst();

        assertThat(movimento.id()).isEqualTo("rec-1");
    }

    @Test
    void legge_gli_istanti_senza_spostarli_nel_fuso_locale() throws Exception {
        RecordDto movimento = objectMapper.readValue(PAGINA, RecordsPageDto.class).records().getFirst();

        assertThat(movimento.recordDate()).isEqualTo(Instant.parse("2026-09-15T00:00:00Z"));
    }
}
