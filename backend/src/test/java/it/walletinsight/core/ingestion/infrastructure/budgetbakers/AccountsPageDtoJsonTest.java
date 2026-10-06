package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountsPageDto;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la lettura del JSON come arriva davvero da `/v1/api/accounts`, con le
 * sue asimmetrie: `bankAccountNumber` che sparisce come chiave sui contanti, il
 * blocco `balance` che cambia forma fra conti normali e carte di credito, campi
 * della specifica che non modelliamo.
 *
 * Il payload e' ricalcato su una risposta reale, compresi i valori — una carta
 * prepagata dichiarata `General` e non `CreditCard` — che smentiscono la lettura
 * ovvia del tipo.
 *
 * Usa la stessa configurazione della produzione, non un ObjectMapper di comodo.
 */
class AccountsPageDtoJsonTest {

    private static final UserId UTENTE = UserId.newId();

    private final ObjectMapper objectMapper = new BudgetBakersHttpConfiguration().budgetBakersObjectMapper();
    private final AccountMapper mapper = new AccountMapper();

    private static final String PAGINA = """
            {
              "accounts": [
                {
                  "id": "277f06aa-f852-4da5-b701-434f3919d3f7",
                  "archived": false,
                  "color": "#2e7d32",
                  "name": "Credem",
                  "createdAt": "2024-12-03T07:07:39.874Z",
                  "updatedAt": "2026-09-21T17:29:20.194Z",
                  "accountType": "CurrentAccount",
                  "currencyCode": "EUR",
                  "excludeFromStats": false,
                  "bankAccountNumber": "F010000712861",
                  "isBankSync": true,
                  "isInvestmentAccount": false,
                  "recordStats": {
                    "recordCount": 1285,
                    "totalIncomes": 66361.07,
                    "totalExpenses": 61884.48
                  },
                  "balance": {
                    "initial": 8921.08,
                    "currencyCode": "EUR",
                    "rawCurrentBalance": 13397.67,
                    "currentBalance": 13397.67,
                    "formula": "initial + totalIncomes - totalExpenses",
                    "balanceMode": "standard"
                  }
                },
                {
                  "id": "2f7519e7-dff5-4b79-80bc-a1e9310a914f",
                  "archived": false,
                  "name": "Contanti",
                  "accountType": "Cash",
                  "currencyCode": "EUR",
                  "excludeFromStats": false,
                  "isBankSync": false,
                  "isInvestmentAccount": false,
                  "recordStats": { "recordCount": 66 },
                  "balance": {
                    "initial": 100,
                    "currencyCode": "EUR",
                    "currentBalance": 92,
                    "balanceMode": "standard"
                  }
                },
                {
                  "id": "c5cf77c0-c97c-4c49-b9d3-250681b66b35",
                  "archived": true,
                  "name": "Amex",
                  "accountType": "CreditCard",
                  "currencyCode": "EUR",
                  "excludeFromStats": true,
                  "bankAccountNumber": "374641710491001",
                  "isBankSync": false,
                  "recordStats": { "recordCount": 0 },
                  "balance": {
                    "initial": 0,
                    "currencyCode": "EUR",
                    "rawCurrentBalance": 0,
                    "currentBalance": 0,
                    "creditLimit": 1500,
                    "availableCredit": 1500,
                    "creditBalance": 0,
                    "balanceMode": "creditCardManual",
                    "balanceDisplayOption": "creditBalance"
                  }
                },
                {
                  "id": "304100f2-a7f7-4614-af5a-c2f0cbfdb2b1",
                  "archived": false,
                  "name": "Crypto.com Card",
                  "accountType": "General",
                  "currencyCode": "EUR",
                  "excludeFromStats": false,
                  "bankAccountNumber": "439772******6181 EUR",
                  "isBankSync": true,
                  "recordStats": { "recordCount": 42 },
                  "balance": { "initial": 131.18, "currencyCode": "EUR", "currentBalance": 0.72 }
                }
              ],
              "limit": 200,
              "offset": 0,
              "total": 4
            }""";

    private AccountsPageDto pagina() throws Exception {
        return objectMapper.readValue(PAGINA, AccountsPageDto.class);
    }

    private Account conto(int indice) throws Exception {
        return mapper.toAccount(
                pagina().accounts().get(indice), UTENTE, IngestionSource.BUDGET_BAKERS);
    }

    @Test
    void senzaNextOffsetLaPaginazioneEFinita() throws Exception {
        AccountsPageDto pagina = pagina();

        assertThat(pagina.accounts()).hasSize(4);
        assertThat(pagina.hasMore()).isFalse();
        assertThat(pagina.total()).isEqualTo(4);
    }

    @Test
    void soloIlSaldoInizialeEntraNellAggregato() throws Exception {
        // 8921.08 letto come double e moltiplicato per cento darebbe 892107.
        assertThat(conto(0).initialBalance()).isEqualTo(Money.of(892_108L, CurrencyCode.EUR));
        assertThat(conto(3).initialBalance()).isEqualTo(Money.of(13_118L, CurrencyCode.EUR));
        // Il saldo corrente della sorgente non entra nell'aggregato: si calcola dai movimenti.
    }

    @Test
    void traduceITipiNeiNomiDiMargine() throws Exception {
        assertThat(conto(0).kind()).isEqualTo(AccountKind.CURRENT_ACCOUNT);
        assertThat(conto(1).kind()).isEqualTo(AccountKind.CASH);
        assertThat(conto(2).kind()).isEqualTo(AccountKind.CREDIT_CARD);
        // Una carta prepagata dichiarata General: il tipo non e' una deduzione sicura.
        assertThat(conto(3).kind()).isEqualTo(AccountKind.GENERAL);
    }

    @Test
    void unContoSenzaIbanNonNeInventaUno() throws Exception {
        assertThat(conto(1).iban()).isNull();
        assertThat(conto(0).iban()).isEqualTo("F010000712861");
    }

    @Test
    void leggeArchiviazioneEdEsclusioneDalleStatistiche() throws Exception {
        assertThat(conto(2).archived()).isTrue();
        assertThat(conto(2).excludedFromStats()).isTrue();
        assertThat(conto(0).archived()).isFalse();
        assertThat(conto(0).excludedFromStats()).isFalse();
    }

    @Test
    void ilNomeDellaSorgenteValeAncheComeNomeDiMargineAllaPrimaLettura() throws Exception {
        assertThat(conto(0).name()).isEqualTo("Credem");
        assertThat(conto(0).sourceName()).isEqualTo("Credem");
        assertThat(conto(0).renamedByUser()).isFalse();
        // Il riferimento all'originale: e' cio' che reggera' una rinomina successiva.
        assertThat(conto(0).externalId()).isEqualTo("277f06aa-f852-4da5-b701-434f3919d3f7");
        assertThat(conto(0).source()).isEqualTo(IngestionSource.BUDGET_BAKERS);
    }

    @Test
    void unTipoFuoriSpecificaNonFaScartareIlConto() {
        // Perdere il conto significherebbe perdere piu' avanti anche i suoi movimenti.
        AccountDto strano = new AccountDto("acc-x", "Salvadanaio", "Piggybank", "EUR",
                false, null, false, false, null, null, null, null);

        assertThat(mapper.toAccount(strano, UTENTE, IngestionSource.BUDGET_BAKERS).kind())
                .isEqualTo(AccountKind.UNKNOWN);
    }

    @Test
    void unaValutaNonGestitaFermaLImportInveceDiFalsareITotali() {
        AccountDto svedese = new AccountDto("acc-y", "Conto svedese", "CurrentAccount", "SEK",
                false, null, false, false, null, null, null, null);

        assertThatThrownBy(() -> mapper.toAccount(svedese, UTENTE, IngestionSource.BUDGET_BAKERS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEK");
    }
}
