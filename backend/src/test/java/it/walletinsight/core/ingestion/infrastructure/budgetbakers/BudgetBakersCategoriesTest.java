package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import it.walletinsight.core.categories.domain.DefaultCategories;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetBakersCategoriesTest {

    @Test
    void ogniCategoriaNotaConfluisceInUnaVoceCheEsiste() {
        // Una voce scritta male farebbe finire in «Da classificare» un'intera
        // categoria senza che nessuno se ne accorga.
        assertThat(BudgetBakersCategories.knownCategories()).isEqualTo(89);
        for (String gruppo : new String[]{"communication_pc", "food_and_drinks", "housing", "income",
                "investments", "life_entertainment", "shopping", "transportation", "vehicle"}) {
            assertThat(DefaultCategories.exists(BudgetBakersCategories.templateFor(null, gruppo))).isTrue();
        }
        BudgetBakersCategories.allTemplates().forEach(voce -> assertThat(DefaultCategories.exists(voce))
                .as(voce).isTrue());
    }

    @Test
    void laSpesaVaInSpesaEIlCiboGenericoNeiRistoranti() {
        assertThat(BudgetBakersCategories.templateFor("5c5c03e8-000a-8000-8000-000000000000", "food_and_drinks"))
                .isEqualTo("cibo/spesa");
        assertThat(BudgetBakersCategories.templateFor("5c5c03eb-000a-8000-8000-000000000000", "food_and_drinks"))
                .isEqualTo("cibo/ristoranti");
    }

    @Test
    void unaCategoriaSconosciutaRipiegaSulGruppo() {
        assertThat(BudgetBakersCategories.templateFor("d2f26a64-ea97-449f-bea1-b316afa6b5ce", "life_entertainment"))
                .isEqualTo("tempo-libero/altro-svago");
        assertThat(BudgetBakersCategories.templateFor("x", null)).isNull();
    }
}
