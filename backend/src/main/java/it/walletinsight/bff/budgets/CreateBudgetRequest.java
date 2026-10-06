package it.walletinsight.bff.budgets;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.budgets.domain.Budget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** @param parentId il budget principale, se questo è un sotto-budget */
public record CreateBudgetRequest(
        @NotBlank @Size(max = Budget.MAX_NAME_LENGTH)
        @Schema(example = "Uscite fuori") String name,

        UUID parentId,

        @NotEmpty List<@NotNull UUID> categoryIds,

        @NotNull @Positive
        @Schema(example = "30000") Long limitCents) {
}
