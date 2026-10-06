package it.walletinsight.bff.accounts;

import it.walletinsight.core.accounts.application.AccountService;
import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * Contratto dei conti: kebab-case delle enum, centesimi interi, e i due nomi
 * esposti entrambi — senza `sourceName` l'interfaccia non potrebbe offrire di
 * tornare al nome originale senza richiederlo al server.
 */
@WebMvcTest(AccountsController.class)
class AccountsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final AccountId CONTO =
            AccountId.of(UUID.fromString("019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61"));
    private static final String URL = "/api/users/" + UTENTE + "/accounts";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private AccountService service;

    @MockitoBean
    private MovementService movements;

    @Test
    void elencaIContiConLaSorgenteDaCuiArrivano() {
        given(service.listAccounts(UTENTE)).willReturn(List.of(conto("Credem", "Credem")));
        given(movements.movedByAccount(UTENTE)).willReturn(Map.of(CONTO, -108L));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{
                          "id": "019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61",
                          "source": "budget-bakers",
                          "name": "Credem",
                          "sourceName": "Credem",
                          "kind": "current-account",
                          "currencyCode": "EUR",
                          "initialBalanceCents": 892108,
                          "balanceCents": 892000,
                          "archived": false,
                          "excludedFromStats": false
                        }]""");
    }

    @Test
    void unContoSenzaMovimentiHaIlSaldoDiPartenza() {
        given(service.listAccounts(UTENTE)).willReturn(List.of(conto("Credem", "Credem")));
        given(movements.movedByAccount(UTENTE)).willReturn(Map.of());

        // Nessun movimento importato non vuol dire saldo zero: vuol dire che il
        // saldo e' ancora quello che la sorgente ha dichiarato come iniziale.
        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[0].balanceCents").isEqualTo(892108);
    }

    @Test
    void unContoRinominatoMostraEntrambiINomi() {
        given(service.listAccounts(UTENTE))
                .willReturn(List.of(conto("Conto stipendio", "Credem")));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[0].sourceName").isEqualTo("Credem");
    }

    @Test
    void delNumeroDiContoEscanoSoloLeUltimeQuattroCifre() {
        given(service.listAccounts(UTENTE)).willReturn(List.of(conto("Credem", "Credem")));

        // Il valore intero resta nel database: da qui esce quanto basta a distinguere
        // due conti nella stessa banca, che e' l'unica cosa per cui la UI lo usa.
        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[0].ibanLast4").isEqualTo("3456");
        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().doesNotHavePath("$[0].iban");
    }

    @Test
    void leQuattroCifreSiPrendonoAncheQuandoIlNumeroENonSoloCifre() {
        // Nei dati reali questo campo contiene testo libero: '439772******6181 EUR',
        // gia' mascherato dalla sorgente. Tagliare gli ultimi quattro caratteri
        // darebbe ' EUR' — un'etichetta che non distingue niente.
        given(service.listAccounts(UTENTE)).willReturn(List.of(conto("Crypto", "Crypto")));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[0].ibanLast4").isEqualTo("6181");
    }

    @Test
    void unNumeroSenzaNessunaCifraNonProduceUnEtichetta() {
        // Per un conto PayPal la sorgente scrive 'PayPal EUR': non c'e' nessun numero
        // da mostrare, e il campo sparisce invece di inventarne uno.
        given(service.listAccounts(UTENTE)).willReturn(List.of(conto("PayPal", "PayPal")));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().doesNotHavePath("$[0].ibanLast4");
    }

    @Test
    void unContoSenzaNumeroNonPortaIlCampo() {
        given(service.listAccounts(UTENTE)).willReturn(List.of(conto("Contanti", "Contanti")));

        // `default-property-inclusion: non_null`: assente, non null.
        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().doesNotHavePath("$[0].ibanLast4");
    }

    @Test
    void senzaContiLElencoEVuoto() {
        given(service.listAccounts(UTENTE)).willReturn(List.of());

        assertThat(mockMvc.get().uri(URL)).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void rinominaUnConto() {
        given(service.updateAppearance(UTENTE, CONTO, "Conto stipendio", null))
                .willReturn(conto("Conto stipendio", "Credem"));

        assertThat(mockMvc.patch().uri(URL + "/" + CONTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Conto stipendio"}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Conto stipendio");
    }

    @Test
    void cambiaSoloIlColoreLasciandoStareIlNome() {
        // Un campo assente vale "non toccare", non "azzera": e' la semantica del PATCH,
        // ed e' cio' che permette al selettore di colore di non sapere nulla del nome.
        given(service.updateAppearance(UTENTE, CONTO, null, "#f97316"))
                .willReturn(conto("Credem", "Credem").coloredWith("#f97316"));

        assertThat(mockMvc.patch().uri(URL + "/" + CONTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"color": "#f97316"}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.color").isEqualTo("#f97316");
    }

    @Test
    void unColoreFuoriFormatoEUnaRichiestaSbagliata() {
        assertThat(mockMvc.patch().uri(URL + "/" + CONTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"color": "arancione"}"""))
                .hasStatus(400);
    }

    @Test
    void unNomeVuotoEUnaRichiestaSbagliata() {
        assertThat(mockMvc.patch().uri(URL + "/" + CONTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "   "}"""))
                .hasStatus(400);

        // La validazione ferma la richiesta prima del dominio: il servizio non viene toccato.
        then(service).should(org.mockito.Mockito.never())
                .updateAppearance(any(), any(), any(), any());
    }

    @Test
    void unContoSconosciutoEUn404() {
        willThrow(new ResourceNotFoundException("Conto", CONTO.toString()))
                .given(service).updateAppearance(eq(UTENTE), eq(CONTO), any(), any());

        assertThat(mockMvc.patch().uri(URL + "/" + CONTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Qualsiasi"}"""))
                .hasStatus(404);
    }

    private static Account conto(String name, String sourceName) {
        String iban = switch (sourceName) {
            case "Contanti" -> null;
            case "Crypto" -> "439772******6181 EUR";
            case "PayPal" -> "PayPal EUR";
            default -> "IT60X0542811101000000123456";
        };
        return new Account(CONTO, UTENTE, IngestionSource.BUDGET_BAKERS, "acc-1",
                name, sourceName,
                "Contanti".equals(sourceName) ? AccountKind.CASH : AccountKind.CURRENT_ACCOUNT,
                CurrencyCode.EUR, Money.of(892_108L, CurrencyCode.EUR), iban, null, false, false);
    }
}
