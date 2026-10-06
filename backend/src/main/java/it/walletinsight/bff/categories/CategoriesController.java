package it.walletinsight.bff.categories;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.SourceCategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Le categorie di Wallet Insights e i loro agganci con quelle delle sorgenti.
 *
 * Niente paginazione: sono qualche decina, e servono intere a ogni tendina.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/categories")
@Tag(name = "Categories")
class CategoriesController {

    private final CategoryService service;
    private final CategoryReorganization reorganization;

    CategoriesController(CategoryService service, CategoryReorganization reorganization) {
        this.service = service;
        this.reorganization = reorganization;
    }

    @GetMapping
    List<CategoryResponse> listCategories(@PathVariable UUID userId) {
        return service.listCategories(UserId.of(userId)).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<CategoryResponse> createCategory(
            @PathVariable UUID userId, @Valid @RequestBody CreateCategoryRequest request) {
        Category creata = service.createCategory(
                UserId.of(userId),
                request.parentId() == null ? null : CategoryId.of(request.parentId()),
                request.name());
        URI location = URI.create(ApiPaths.API + "/users/" + userId + "/categories/" + creata.id());
        return ResponseEntity.created(location).body(CategoryResponse.from(creata));
    }

    @PatchMapping("/{categoryId}")
    CategoryResponse updateCategory(
            @PathVariable UUID userId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(service.updateCategory(
                UserId.of(userId),
                CategoryId.of(categoryId),
                request.name(),
                request.color(),
                request.parentId() == null ? null : CategoryId.of(request.parentId())));
    }

    /** Una sottocategoria si toglie unendola a un'altra (`into`), che ne prende i movimenti. */
    @DeleteMapping("/{categoryId}")
    ResponseEntity<Void> deleteCategory(
            @PathVariable UUID userId,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) UUID into) {
        reorganization.delete(UserId.of(userId), CategoryId.of(categoryId),
                into == null ? null : CategoryId.of(into));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sources")
    List<SourceCategoryResponse> listSourceCategories(@PathVariable UUID userId) {
        return service.listSourceCategories(UserId.of(userId)).stream()
                .map(SourceCategoryResponse::from)
                .toList();
    }

    /** Riaggancia una categoria della sorgente; i movimenti già importati la seguono. */
    @PutMapping("/sources/{sourceCategoryId}")
    SourceCategoryResponse relinkSourceCategory(
            @PathVariable UUID userId,
            @PathVariable UUID sourceCategoryId,
            @Valid @RequestBody RelinkSourceCategoryRequest request) {
        return SourceCategoryResponse.from(reorganization.relink(
                UserId.of(userId), SourceCategoryId.of(sourceCategoryId), CategoryId.of(request.categoryId())));
    }
}
