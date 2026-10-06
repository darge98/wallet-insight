package it.walletinsight.bff.categories;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.categories.domain.Category;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Un PATCH: un campo assente vale «non toccare». Il colore vuoto lo toglie;
 * `parentId` sposta una sottocategoria in un'altra macro.
 */
public record UpdateCategoryRequest(
        @Size(max = Category.MAX_NAME_LENGTH)
        @Pattern(regexp = "\\s*\\S.*", message = "non può essere vuoto")
        @Schema(example = "Ristoranti") String name,

        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "deve essere nel formato #rrggbb o la stringa vuota")
        @Schema(example = "#f97316") String color,

        UUID parentId) {
}
