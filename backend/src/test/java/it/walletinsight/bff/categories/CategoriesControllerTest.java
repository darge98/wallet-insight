package it.walletinsight.bff.categories;

import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(CategoriesController.class)
class CategoriesControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final CategoryId CIBO =
            CategoryId.of(UUID.fromString("019b4c60-2f4f-7d04-9f55-3c9a7e4d1f83"));
    private static final CategoryId RISTORANTI =
            CategoryId.of(UUID.fromString("019b4c60-2f4f-7d04-9f55-3c9a7e4d1f84"));
    private static final String URL = "/api/users/" + UTENTE + "/categories";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private CategoryService service;

    @MockitoBean
    private CategoryReorganization reorganization;

    @Test
    void elencaMacroESottocategorieConIlLoroLegame() {
        given(service.listCategories(UTENTE)).willReturn(List.of(
                new Category(CIBO, UTENTE, null, "Cibo e bevande", null, "cibo"),
                new Category(RISTORANTI, UTENTE, CIBO, "Ristoranti", "#f97316", "cibo/ristoranti")));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [
                          {"id": "%s", "name": "Cibo e bevande"},
                          {"id": "%s", "parentId": "%s", "name": "Ristoranti", "color": "#f97316"}
                        ]""".formatted(CIBO, RISTORANTI, CIBO));
        assertThat(mockMvc.get().uri(URL)).bodyJson().doesNotHavePath("$[0].parentId");
    }

    @Test
    void creareUnaSottocategoria() {
        given(service.createCategory(UTENTE, CIBO, "Aperitivi"))
                .willReturn(Category.create(UTENTE, CIBO, "Aperitivi"));

        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Aperitivi", "parentId": "%s"}""".formatted(CIBO)))
                .hasStatus(201)
                .bodyJson().isLenientlyEqualTo("""
                        {"name": "Aperitivi", "parentId": "%s"}""".formatted(CIBO));
    }

    @Test
    void ilPatchPassaNullPerICampiAssenti() {
        given(service.updateCategory(any(), any(), any(), any(), any()))
                .willReturn(new Category(RISTORANTI, UTENTE, CIBO, "Fuori a cena", null, null));

        assertThat(mockMvc.patch().uri(URL + "/" + RISTORANTI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Fuori a cena"}""")).hasStatusOk();

        then(service).should().updateCategory(UTENTE, RISTORANTI, "Fuori a cena", null, null);
    }

    @Test
    void togliereUnaCategoriaInUnBudgetEUnConflitto() {
        willThrow(new IllegalStateException("La categoria è nel budget «Cibo»: toglila prima da lì."))
                .given(reorganization).delete(any(), any(), any());

        assertThat(mockMvc.delete().uri(URL + "/" + RISTORANTI + "?into=" + CIBO)).hasStatus(409);
    }

    @Test
    void unColoreFuoriFormatoEUnaRichiestaSbagliata() {
        assertThat(mockMvc.patch().uri(URL + "/" + RISTORANTI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"color": "rosso"}""")).hasStatus(400);
    }
}
