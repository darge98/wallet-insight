package it.walletinsight.bff.accounts;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.accounts.application.AccountService;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * I conti di un utente: leggerli e rinominarli.
 *
 * Non si creano da qui, e non è un'omissione. Un conto nasce da un import —
 * `POST /api/users/{id}/imports` — perché la sua identità comprende il
 * riferimento al dato originale, e un conto inventato dall'interfaccia non
 * avrebbe nulla a cui agganciarsi. Non si cancellano nemmeno: un conto sparito
 * dalla sorgente possiede movimenti storici, e non elencarlo più non è una buona
 * ragione per distruggerli.
 *
 * Restano quindi due sole cose che l'utente decide su un conto: come si chiama e
 * di che colore lo vede. `PATCH` e non `PUT` perché è davvero una modifica
 * parziale — tutto il resto del conto appartiene alla sorgente, e un `PUT`
 * prometterebbe di poterlo sostituire.
 *
 * Niente paginazione sull'elenco: i conti di una persona sono una decina, e
 * paginarli costringerebbe ogni schermata che ne mostra il nome a girare le
 * pagine per trovarlo.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/accounts")
@Tag(name = "Accounts")
class AccountsController {

    private final AccountService service;
    private final MovementService movements;

    AccountsController(AccountService service, MovementService movements) {
        this.service = service;
        this.movements = movements;
    }

    /**
     * I conti con il saldo di oggi.
     *
     * Due moduli in una risposta sola, ed è il motivo per cui il BFF esiste: il
     * saldo è il saldo iniziale del conto più la somma dei suoi movimenti, e i due
     * pezzi vivono in moduli diversi che non si conoscono. Comporli qui costa una
     * seconda query — un'aggregazione sola per tutto l'utente, non una per conto —
     * e risparmia al browser di doverlo fare a mano.
     */
    @GetMapping
    List<AccountResponse> listAccounts(@PathVariable UUID userId) {
        UserId id = UserId.of(userId);
        Map<AccountId, Long> spostato = movements.movedByAccount(id);
        return service.listAccounts(id).stream()
                .map(account -> AccountResponse.from(
                        account, spostato.getOrDefault(account.id(), 0L)))
                .toList();
    }

    @PatchMapping("/{accountId}")
    AccountResponse update(
            @PathVariable UUID userId,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        UserId id = UserId.of(userId);
        AccountId conto = AccountId.of(accountId);
        // La modifica non tocca i movimenti, ma la risposta e' la stessa forma
        // dell'elenco: una sola verita' sul saldo, non due a seconda dell'endpoint.
        return AccountResponse.from(
                service.updateAppearance(id, conto, request.name(), request.color()),
                movements.movedByAccount(id).getOrDefault(conto, 0L));
    }
}
