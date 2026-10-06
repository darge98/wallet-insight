package it.walletinsight.bff.budgets;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.budgets.domain.Budget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Un PATCH: un campo assente vale «non toccare». Il limite nuovo vale dal mese
 * corrente; il principale di un budget non si cambia.
 */
public record UpdateBudgetRequest(
        @Size(max = Budget.MAX_NAME_LENGTH)
        @Pattern(regexp = ".*\\S.*", message = "non può essere vuoto")
        @Schema(example = "Uscite fuori") String name,

        @Size(min = 1) List<@NotNull UUID> categoryIds,

        @Positive
        @Schema(example = "30000") Long limitCents) {
}
