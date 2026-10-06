package it.walletinsight.bff.categories;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.categories.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** @param parentId la macro in cui crearla; assente per creare una macro */
public record CreateCategoryRequest(
        @NotBlank @Size(max = Category.MAX_NAME_LENGTH)
        @Schema(example = "Cinema") String name,
        UUID parentId) {
}
