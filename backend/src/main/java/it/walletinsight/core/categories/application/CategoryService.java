package it.walletinsight.core.categories.application;

import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.categories.domain.DefaultCategories;
import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.core.categories.domain.SourceCategory;
import it.walletinsight.core.categories.domain.SourceCategoryId;
import it.walletinsight.core.categories.domain.SourceCategoryRepository;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.source.IngestionSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Casi d'uso delle categorie di Wallet Insights e degli agganci con quelle delle sorgenti.
 *
 * Spostare i movimenti quando una categoria viene unita o riagganciata non è
 * compito di questo modulo, che non li conosce: lo fa il BFF, nella stessa
 * transazione, con ciò che questi metodi restituiscono.
 */
@Service
@Transactional
public class CategoryService {

    private static final String USER_RESOURCE_TYPE = "Utente";
    private static final String CATEGORY_RESOURCE_TYPE = "Categoria";
    private static final String SOURCE_CATEGORY_RESOURCE_TYPE = "Categoria della sorgente";

    private final CategoryRepository repository;
    private final SourceCategoryRepository sources;
    private final UserRepository users;

    public CategoryService(CategoryRepository repository,
                           SourceCategoryRepository sources,
                           UserRepository users) {
        this.repository = repository;
        this.sources = sources;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories(UserId userId) {
        requireUser(userId);
        return repository.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Category> findCategory(UserId userId, CategoryId id) {
        return repository.findById(userId, id);
    }

    /** Dà all'utente l'elenco di base, se non ha ancora nessuna categoria. Idempotente. */
    public List<Category> ensureDefaults(UserId userId) {
        requireUser(userId);
        List<Category> esistenti = repository.findByUser(userId);
        if (!esistenti.isEmpty()) {
            return esistenti;
        }
        List<Category> base = DefaultCategories.instantiate(userId);
        repository.insertAll(base);
        return base;
    }

    /** Una macro se {@code parentId} è {@code null}, altrimenti una sottocategoria di quella. */
    public Category createCategory(UserId userId, CategoryId parentId, String name) {
        requireUser(userId);
        if (parentId != null) {
            requireMacro(userId, parentId);
        }
        Category nuova = Category.create(userId, parentId, name);
        repository.insertAll(List.of(nuova));
        return nuova;
    }

    /**
     * Cambia nome, colore o macro di una categoria; un parametro {@code null} vuol
     * dire «non toccare», un colore vuoto lo toglie. Una macro non si sposta: le sue
     * sottocategorie finirebbero a un terzo livello.
     */
    public Category updateCategory(UserId userId, CategoryId id, String name, String color,
                                   CategoryId parentId) {
        requireUser(userId);
        Category categoria = requireCategory(userId, id);
        Category aggiornata = categoria;
        if (name != null) {
            aggiornata = aggiornata.renamedTo(name);
        }
        if (color != null) {
            aggiornata = aggiornata.coloredWith(color.isEmpty() ? null : color);
        }
        if (parentId != null && !parentId.equals(categoria.parentId())) {
            if (categoria.isMacro()) {
                throw new IllegalStateException(
                        "«%s» è una macro: si spostano solo le sottocategorie.".formatted(categoria.name()));
            }
            requireMacro(userId, parentId);
            aggiornata = aggiornata.movedUnder(parentId);
        }
        if (aggiornata.equals(categoria)) {
            return categoria;
        }
        repository.update(aggiornata);
        return aggiornata;
    }

    /**
     * Toglie una categoria. Una sottocategoria va unita a un'altra ({@code into}),
     * che ne eredita gli agganci; una macro si toglie solo vuota.
     *
     * I movimenti li sposta il chiamante, prima di questa chiamata: dopo, la
     * categoria non c'è più.
     */
    public void deleteCategory(UserId userId, CategoryId id, CategoryId into) {
        requireUser(userId);
        Category categoria = requireCategory(userId, id);
        if (categoria.isMacro()) {
            boolean haFiglie = repository.findByUser(userId).stream()
                    .anyMatch(altra -> id.equals(altra.parentId()));
            if (haFiglie) {
                throw new IllegalStateException(
                        "«%s» contiene ancora delle sottocategorie: spostale o uniscile prima."
                                .formatted(categoria.name()));
            }
        } else {
            requireSubcategoryTarget(userId, id, into);
            sources.relink(userId, id, into);
        }
        repository.delete(userId, id);
    }

    /** Il bersaglio di un'unione: una sottocategoria dell'utente, diversa da quella che sparisce. */
    public Category requireSubcategoryTarget(UserId userId, CategoryId from, CategoryId into) {
        if (into == null) {
            throw new IllegalArgumentException(
                    "Scegli la categoria in cui spostare i movimenti di quella che togli.");
        }
        if (into.equals(from)) {
            throw new IllegalArgumentException("Una categoria non si unisce con se stessa.");
        }
        Category bersaglio = requireCategory(userId, into);
        if (bersaglio.isMacro()) {
            throw new IllegalArgumentException("I movimenti si spostano in una sottocategoria, non in una macro.");
        }
        return bersaglio;
    }

    /** Una categoria su cui si può classificare un movimento: dell'utente e non una macro. */
    @Transactional(readOnly = true)
    public Category requireSubcategory(UserId userId, CategoryId id) {
        return subcategory(userId, id);
    }

    private Category subcategory(UserId userId, CategoryId id) {
        Category categoria = requireCategory(userId, id);
        if (categoria.isMacro()) {
            throw new IllegalArgumentException(
                    "«%s» è una macro: scegli una delle sue sottocategorie.".formatted(categoria.name()));
        }
        return categoria;
    }

    @Transactional(readOnly = true)
    public List<SourceCategory> listSourceCategories(UserId userId) {
        requireUser(userId);
        return sources.findByUser(userId).stream()
                .sorted(Comparator.comparing(SourceCategory::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Aggancia una categoria della sorgente a un'altra categoria di Wallet Insights e
     * restituisce com'era prima: il chiamante ci sposta i movimenti già importati.
     */
    public SourceCategory relinkSourceCategory(UserId userId, SourceCategoryId id, CategoryId categoryId) {
        requireUser(userId);
        SourceCategory prima = sources.findById(userId, id).orElseThrow(
                () -> new ResourceNotFoundException(SOURCE_CATEGORY_RESOURCE_TYPE, id.toString()));
        subcategory(userId, categoryId);
        if (!categoryId.equals(prima.category())) {
            sources.update(prima.linkedTo(categoryId));
        }
        return prima;
    }

    /**
     * Allinea le categorie di una sorgente e dice in quale categoria di Wallet Insights
     * finisce ognuna, per identificativo della sorgente.
     *
     * Una categoria nuova si aggancia alla voce dell'elenco di base suggerita
     * dall'adapter; se manca, a quella in cui confluisce la categoria da cui deriva;
     * altrimenti a «Da classificare». Gli agganci esistenti non si toccano: sono
     * dell'utente.
     */
    public Map<String, CategoryId> syncFromSource(UserId userId, IngestionSource source,
                                                  List<ImportedCategory> incoming) {
        Map<String, CategoryId> perChiave = new HashMap<>();
        for (Category categoria : ensureDefaults(userId)) {
            if (categoria.templateKey() != null) {
                perChiave.put(categoria.templateKey(), categoria.id());
            }
        }

        Map<String, SourceCategory> esistenti = new HashMap<>();
        for (SourceCategory nota : sources.findByUserAndSource(userId, source)) {
            esistenti.put(nota.externalId(), nota);
        }

        Map<String, CategoryId> agganci = new LinkedHashMap<>();
        // Prima quelle senza origine: una categoria derivata ripiega sull'aggancio
        // della sua, che deve quindi essere già deciso.
        List<ImportedCategory> ordinate = incoming.stream()
                .sorted(Comparator.comparing(categoria -> categoria.parentExternalId() != null))
                .toList();
        for (ImportedCategory letta : ordinate) {
            SourceCategory esistente = esistenti.get(letta.externalId());
            SourceCategory risultato;
            if (esistente == null) {
                CategoryId aggancio = Optional.ofNullable(letta.template()).map(perChiave::get)
                        .or(() -> Optional.ofNullable(letta.parentExternalId()).map(agganci::get))
                        .orElse(perChiave.get(DefaultCategories.UNCLASSIFIED));
                risultato = new SourceCategory(SourceCategoryId.newId(), userId, source,
                        letta.externalId(), letta.name(), letta.group(), letta.parentExternalId(), aggancio);
                sources.insert(risultato);
            } else {
                risultato = esistente.refreshedFrom(letta);
                if (!risultato.equals(esistente)) {
                    sources.update(risultato);
                }
            }
            if (risultato.category() != null) {
                agganci.put(risultato.externalId(), risultato.category());
            }
        }
        return agganci;
    }

    private Category requireMacro(UserId userId, CategoryId id) {
        Category categoria = requireCategory(userId, id);
        if (!categoria.isMacro()) {
            throw new IllegalArgumentException(
                    "«%s» è già una sottocategoria: le categorie hanno due livelli.".formatted(categoria.name()));
        }
        return categoria;
    }

    private Category requireCategory(UserId userId, CategoryId id) {
        return repository.findById(userId, id)
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY_RESOURCE_TYPE, id.toString()));
    }

    private void requireUser(UserId userId) {
        if (users.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString());
        }
    }
}
