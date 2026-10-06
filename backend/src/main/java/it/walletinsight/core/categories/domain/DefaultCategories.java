package it.walletinsight.core.categories.domain;

import it.walletinsight.core.users.domain.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L'elenco di base: le categorie con cui ogni utente parte, così che l'import
 * classifichi da subito senza chiedergli niente.
 *
 * Le chiavi ({@code cibo/spesa}) sono il contratto con gli adapter delle sorgenti,
 * che vi fanno confluire le proprie categorie, e non si rinominano: sono salvate in
 * {@code categories.template_key}. I nomi invece sono solo il punto di partenza.
 */
public final class DefaultCategories {

    /** Dove finisce ciò che nessuna regola sa dove mettere. */
    public static final String UNCLASSIFIED = "da-classificare/da-classificare";

    public record Template(String key, String parentKey, String name) {
    }

    private static final List<Template> TEMPLATES = new ArrayList<>();

    static {
        macro("casa", "Casa",
                "affitto", "Affitto",
                "mutuo", "Mutuo",
                "utenze", "Utenze",
                "internet", "Internet",
                "telefono", "Telefono",
                "manutenzione", "Manutenzione",
                "arredamento", "Arredamento",
                "assicurazione-casa", "Assicurazione casa");
        macro("cibo", "Cibo e bevande",
                "spesa", "Spesa",
                "ristoranti", "Ristoranti e asporto",
                "bar", "Bar e caffè");
        macro("trasporti", "Trasporti",
                "carburante", "Carburante",
                "mezzi-pubblici", "Mezzi pubblici",
                "taxi", "Taxi",
                "parcheggi", "Parcheggi e pedaggi",
                "manutenzione-veicolo", "Manutenzione veicolo",
                "assicurazione-auto", "Assicurazione auto",
                "rata-leasing", "Rata o leasing",
                "noleggi", "Noleggi",
                "altro-trasporti", "Altro trasporti");
        macro("salute", "Salute e benessere",
                "medico", "Medico e cure",
                "farmacia", "Farmacia",
                "sport", "Sport e fitness",
                "cura-persona", "Cura della persona");
        macro("shopping", "Shopping",
                "abbigliamento", "Abbigliamento",
                "elettronica", "Elettronica",
                "regali", "Regali e donazioni",
                "gioielli", "Gioielli e accessori",
                "altro-shopping", "Altro shopping");
        macro("tempo-libero", "Tempo libero",
                "viaggi", "Viaggi e vacanze",
                "hobby", "Hobby",
                "cultura-eventi", "Cultura ed eventi",
                "abbonamenti-digitali", "Abbonamenti digitali",
                "alcol-tabacchi", "Alcol e tabacchi",
                "giochi-lotterie", "Giochi e lotterie",
                "altro-svago", "Altro svago");
        macro("famiglia", "Famiglia",
                "figli", "Figli",
                "istruzione", "Istruzione e formazione",
                "animali", "Animali");
        macro("finanza", "Finanza e tasse",
                "tasse", "Tasse",
                "commissioni", "Commissioni bancarie",
                "interessi-prestiti", "Interessi e prestiti",
                "assicurazioni", "Assicurazioni",
                "multe", "Multe",
                "consulenze", "Consulenze");
        macro("risparmio", "Risparmio e investimenti",
                "risparmi", "Risparmi",
                "investimenti", "Investimenti",
                "pensione", "Pensione",
                "immobili-beni", "Immobili e beni");
        macro("entrate", "Entrate",
                "stipendio", "Stipendio",
                "rimborsi", "Rimborsi",
                "vendite", "Vendite",
                "interessi-dividendi", "Interessi e dividendi",
                "affitti-percepiti", "Affitti percepiti",
                "lavoro-autonomo", "Lavoro autonomo",
                "sussidi", "Sussidi",
                "regali-ricevuti", "Regali ricevuti",
                "altre-entrate", "Altre entrate");
        macro("da-classificare", "Da classificare",
                "da-classificare", "Da classificare",
                "giroconti", "Giroconti");
    }

    private DefaultCategories() {
    }

    private static void macro(String key, String name, String... children) {
        TEMPLATES.add(new Template(key, null, name));
        for (int i = 0; i < children.length; i += 2) {
            TEMPLATES.add(new Template(key + "/" + children[i], key, children[i + 1]));
        }
    }

    public static List<Template> templates() {
        return List.copyOf(TEMPLATES);
    }

    public static boolean exists(String key) {
        return TEMPLATES.stream().anyMatch(template -> template.key().equals(key));
    }

    /** L'elenco di base per un utente, con identificatori nuovi: prima le macro, poi le figlie. */
    public static List<Category> instantiate(UserId userId) {
        Map<String, CategoryId> perChiave = new HashMap<>();
        List<Category> categorie = new ArrayList<>(TEMPLATES.size());
        for (Template template : TEMPLATES) {
            CategoryId id = CategoryId.newId();
            perChiave.put(template.key(), id);
            CategoryId padre = template.parentKey() == null ? null : perChiave.get(template.parentKey());
            categorie.add(new Category(id, userId, padre, template.name(), null, template.key()));
        }
        return categorie;
    }
}
