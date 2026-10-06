package it.walletinsight.bff.subscriptions;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.subscriptions.domain.Cadence;
import it.walletinsight.core.subscriptions.domain.CadenceUnit;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionDefinition;
import it.walletinsight.platform.web.KebabCase;
import it.walletinsight.shared.money.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * La definizione intera di un abbonamento, per crearlo e per riscriverlo: un PUT e
 * non un PATCH perché togliere la data di fine deve potersi dire, e un campo
 * assente e un {@code null} arrivano identici.
 *
 * @param unit    `week`, `month` o `year`
 * @param endDate    l'ultimo giorno in cui può cadere un addebito; assente se non è disdetto
 * @param categoryId una sottocategoria, facoltativa
 * @param accountId  il conto di addebito, facoltativo
 */
public record SubscriptionRequest(
        @NotBlank @Size(max = Subscription.MAX_NAME_LENGTH)
        @Schema(example = "Netflix") String name,

        @NotNull @Positive
        @Schema(example = "1399") Long amountCents,

        @NotNull @Min(1) @Max(Cadence.MAX_EVERY)
        @Schema(example = "1") Integer every,

        @NotBlank
        @Schema(example = "month", allowableValues = {"week", "month", "year"}) String unit,

        @NotNull LocalDate startDate,

        LocalDate endDate,

        UUID categoryId,

        UUID accountId) {

    SubscriptionDefinition toDefinition() {
        return new SubscriptionDefinition(
                name,
                Money.of(amountCents),
                new Cadence(every, KebabCase.to(CadenceUnit.class, unit)),
                startDate,
                endDate,
                categoryId == null ? null : CategoryId.of(categoryId),
                accountId == null ? null : AccountId.of(accountId));
    }
}
