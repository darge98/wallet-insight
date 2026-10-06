package it.walletinsight.core.categories.application;

import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.DefaultCategories;
import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.core.categories.domain.SourceCategory;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryServiceTest extends AbstractDatabaseTest {

    @Autowired
    private CategoryService service;

    @Autowired
    private UserRepository users;

    private UserId marta;

    @BeforeEach
    void creaUnUtente() {
        User user = new User(UserId.newId(), "Marta", null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        marta = user.id();
    }

    @Test
    void lElencoDiBaseArrivaUnaVoltaSola() {
        service.ensureDefaults(marta);
        service.ensureDefaults(marta);

        List<Category> categorie = service.listCategories(marta);
        assertThat(categorie).hasSize(DefaultCategories.templates().size());
        assertThat(categorie).filteredOn(Category::isMacro).hasSize(11);
    }

    @Test
    void lImportAggancianoAllaVoceSuggeritaOppureAllaCategoriaDaCuiDeriva() {
        Map<String, CategoryId> agganci = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS, List.of(
                new ImportedCategory("cat-spesa", "Spesa", "food_and_drinks", null, "cibo/spesa"),
                // Creata dall'utente nella sorgente: segue la categoria da cui deriva.
                new ImportedCategory("cat-mercato", "Mercato", "food_and_drinks", "cat-spesa", null),
                new ImportedCategory("cat-boh", "Boh", null, null, null)));

        assertThat(nome(agganci.get("cat-spesa"))).isEqualTo("Spesa");
        assertThat(agganci.get("cat-mercato")).isEqualTo(agganci.get("cat-spesa"));
        assertThat(nome(agganci.get("cat-boh"))).isEqualTo("Da classificare");
    }

    @Test
    void unAggancioCambiatoDallUtenteSopravviveAllImportSuccessivo() {
        service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS, List.of(
                new ImportedCategory("cat-1", "Cibo", "food_and_drinks", null, "cibo/ristoranti")));
        SourceCategory cibo = service.listSourceCategories(marta).getFirst();
        CategoryId spesa = perNome("Spesa");
        service.relinkSourceCategory(marta, cibo.id(), spesa);

        Map<String, CategoryId> dopo = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS, List.of(
                new ImportedCategory("cat-1", "Cibo e bevande", "food_and_drinks", null, "cibo/ristoranti")));

        assertThat(dopo.get("cat-1")).isEqualTo(spesa);
        assertThat(service.listSourceCategories(marta).getFirst().name()).isEqualTo("Cibo e bevande");
    }

    @Test
    void leCategorieHannoDueLivelli() {
        service.ensureDefaults(marta);
        CategoryId spesa = perNome("Spesa");

        assertThatThrownBy(() -> service.createCategory(marta, spesa, "Mercato"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateCategory(marta, perNome("Casa"), null, null, perNome("Cibo e bevande")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unireUnaSottocategoriaNePassaGliAgganci() {
        service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS, List.of(
                new ImportedCategory("cat-bar", "Bar", "food_and_drinks", null, "cibo/bar")));
        CategoryId bar = perNome("Bar e caffè");
        CategoryId ristoranti = perNome("Ristoranti e asporto");

        service.deleteCategory(marta, bar, ristoranti);

        assertThat(service.findCategory(marta, bar)).isEmpty();
        assertThat(service.listSourceCategories(marta).getFirst().category()).isEqualTo(ristoranti);
    }

    @Test
    void unaMacroSiToglieSoloVuota() {
        service.ensureDefaults(marta);

        assertThatThrownBy(() -> service.deleteCategory(marta, perNome("Cibo e bevande"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    private String nome(CategoryId id) {
        return service.findCategory(marta, id).orElseThrow().name();
    }

    private CategoryId perNome(String nome) {
        return service.listCategories(marta).stream()
                .filter(categoria -> categoria.name().equals(nome))
                .findFirst()
                .orElseThrow()
                .id();
    }
}
