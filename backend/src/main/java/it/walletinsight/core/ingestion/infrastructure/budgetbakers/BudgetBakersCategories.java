package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.util.Map;

/**
 * In quale voce dell'elenco di base di Wallet Insights confluisce una categoria di BudgetBakers.
 *
 * Le categorie standard di BudgetBakers hanno identificativi deterministici, uguali
 * per tutti gli utenti (la specifica lo dichiara): {@code 5c5c03e8-000a-…} è «Spesa»,
 * con {@code 000a} il codice del gruppo cibo. La tabella è scritta su quelle lette
 * da un utente reale il 04/10/2026, 89 su 95; le altre 6 erano create dall'utente.
 * Ciò che la tabella non conosce ripiega sul gruppo.
 */
final class BudgetBakersCategories {

    private static final String SUFFISSO = "-8000-8000-000000000000";

    private static final Map<String, String> PER_CATEGORIA = Map.ofEntries(
            // communication_pc
            Map.entry("5c5c1b59-0046", "casa/telefono"),
            Map.entry("5c5c1b5a-0046", "casa/internet"),
            Map.entry("5c5c1b5d-0046", "casa/internet"),
            Map.entry("5c5c1b5b-0046", "tempo-libero/abbonamenti-digitali"),
            Map.entry("5c5c1b5c-0046", "shopping/altro-shopping"),
            // financial_expenses
            Map.entry("5c5c1f40-0050", "finanza/tasse"),
            Map.entry("5c5c1f41-0050", "finanza/assicurazioni"),
            Map.entry("5c5c1f42-0050", "finanza/interessi-prestiti"),
            Map.entry("5c5c1f43-0050", "finanza/multe"),
            Map.entry("5c5c1f44-0050", "finanza/consulenze"),
            Map.entry("5c5c1f45-0050", "finanza/commissioni"),
            Map.entry("5c5c1f47-0050", "famiglia/figli"),
            Map.entry("5c5c1f48-0050", "finanza/commissioni"),
            // food_and_drinks: «Cibo» misurato è quasi tutto asporto e consegne
            Map.entry("5c5c03e8-000a", "cibo/spesa"),
            Map.entry("5c5c03e9-000a", "cibo/ristoranti"),
            Map.entry("5c5c03ea-000a", "cibo/bar"),
            Map.entry("5c5c03eb-000a", "cibo/ristoranti"),
            // housing
            Map.entry("5c5c0bb8-001e", "casa/affitto"),
            Map.entry("5c5c0bb9-001e", "casa/mutuo"),
            Map.entry("5c5c0bba-001e", "casa/utenze"),
            Map.entry("5c5c0bbb-001e", "casa/utenze"),
            Map.entry("5c5c0bbc-001e", "casa/manutenzione"),
            Map.entry("5c5c0bbd-001e", "casa/arredamento"),
            Map.entry("5c5c0bc2-001e", "casa/assicurazione-casa"),
            // income
            Map.entry("5c5c2710-0064", "entrate/stipendio"),
            Map.entry("5c5c2711-0064", "entrate/interessi-dividendi"),
            Map.entry("5c5c2712-0064", "entrate/vendite"),
            Map.entry("5c5c2713-0064", "entrate/affitti-percepiti"),
            Map.entry("5c5c2714-0064", "entrate/sussidi"),
            Map.entry("5c5c2715-0064", "entrate/lavoro-autonomo"),
            Map.entry("5c5c2716-0064", "entrate/altre-entrate"),
            Map.entry("5c5c2717-0064", "entrate/altre-entrate"),
            Map.entry("5c5c2718-0064", "entrate/rimborsi"),
            Map.entry("5c5c2719-0064", "entrate/sussidi"),
            Map.entry("5c5c271a-0064", "entrate/regali-ricevuti"),
            Map.entry("5c5c271b-0064", "entrate/altre-entrate"),
            // investments
            Map.entry("5c5c2328-005a", "risparmio/immobili-beni"),
            Map.entry("5c5c2329-005a", "risparmio/immobili-beni"),
            Map.entry("5c5c232a-005a", "risparmio/investimenti"),
            Map.entry("5c5c232b-005a", "risparmio/risparmi"),
            Map.entry("5c5c232c-005a", "risparmio/immobili-beni"),
            Map.entry("5c5c232d-005a", "risparmio/investimenti"),
            // life_entertainment
            Map.entry("5c5c1770-003c", "salute/medico"),
            Map.entry("5c5c1771-003c", "salute/cura-persona"),
            Map.entry("5c5c1772-003c", "salute/sport"),
            Map.entry("5c5c1773-003c", "tempo-libero/cultura-eventi"),
            Map.entry("5c5c1774-003c", "shopping/regali"),
            Map.entry("5c5c1775-003c", "tempo-libero/hobby"),
            Map.entry("5c5c1776-003c", "famiglia/istruzione"),
            Map.entry("5c5c1777-003c", "tempo-libero/abbonamenti-digitali"),
            Map.entry("5c5c1778-003c", "tempo-libero/abbonamenti-digitali"),
            Map.entry("5c5c1779-003c", "tempo-libero/viaggi"),
            Map.entry("5c5c177a-003c", "shopping/regali"),
            Map.entry("5c5c177b-003c", "tempo-libero/alcol-tabacchi"),
            Map.entry("5c5c177c-003c", "tempo-libero/giochi-lotterie"),
            Map.entry("5c5c177d-003c", "tempo-libero/altro-svago"),
            // others
            Map.entry("5c5c2af8-006e", "da-classificare/da-classificare"),
            Map.entry("5c5c2af9-006e", "da-classificare/da-classificare"),
            // shopping
            Map.entry("5c5c07d0-0014", "shopping/abbigliamento"),
            Map.entry("5c5c07d1-0014", "shopping/gioielli"),
            Map.entry("5c5c07d2-0014", "salute/cura-persona"),
            Map.entry("5c5c07d3-0014", "famiglia/figli"),
            Map.entry("5c5c07d4-0014", "casa/arredamento"),
            Map.entry("5c5c07d5-0014", "famiglia/animali"),
            Map.entry("5c5c07d6-0014", "shopping/elettronica"),
            Map.entry("5c5c07d7-0014", "shopping/regali"),
            Map.entry("5c5c07d8-0014", "shopping/altro-shopping"),
            Map.entry("5c5c07d9-0014", "tempo-libero/hobby"),
            Map.entry("5c5c07da-0014", "shopping/altro-shopping"),
            Map.entry("5c5c07db-0014", "salute/farmacia"),
            Map.entry("5c5c07dc-0014", "shopping/altro-shopping"),
            // system_categories
            Map.entry("5c5c4e20-00c8", "finanza/interessi-prestiti"),
            Map.entry("5c5c4e21-00c8", "da-classificare/giroconti"),
            Map.entry("5c5c4e22-00c8", "shopping/altro-shopping"),
            Map.entry("5c5c4e23-00c8", "da-classificare/da-classificare"),
            // transportation
            Map.entry("5c5c0fa0-0028", "trasporti/mezzi-pubblici"),
            Map.entry("5c5c0fa1-0028", "trasporti/taxi"),
            Map.entry("5c5c0fa2-0028", "trasporti/mezzi-pubblici"),
            Map.entry("5c5c0fa3-0028", "trasporti/altro-trasporti"),
            Map.entry("5c5c0fa4-0028", "trasporti/altro-trasporti"),
            // unknown_records
            Map.entry("5c5c32c8-0082", "da-classificare/da-classificare"),
            Map.entry("5c5c32c9-0082", "da-classificare/da-classificare"),
            // vehicle
            Map.entry("5c5c1388-0032", "trasporti/carburante"),
            Map.entry("5c5c1389-0032", "trasporti/parcheggi"),
            Map.entry("5c5c138a-0032", "trasporti/manutenzione-veicolo"),
            Map.entry("5c5c138b-0032", "trasporti/noleggi"),
            Map.entry("5c5c138c-0032", "trasporti/altro-trasporti"),
            Map.entry("5c5c1392-0032", "trasporti/assicurazione-auto"),
            Map.entry("5c5c1f46-0032", "trasporti/rata-leasing"));

    private static final Map<String, String> PER_GRUPPO = Map.ofEntries(
            Map.entry("communication_pc", "casa/internet"),
            Map.entry("financial_expenses", "finanza/commissioni"),
            Map.entry("food_and_drinks", "cibo/ristoranti"),
            Map.entry("housing", "casa/manutenzione"),
            Map.entry("income", "entrate/altre-entrate"),
            Map.entry("investments", "risparmio/investimenti"),
            Map.entry("life_entertainment", "tempo-libero/altro-svago"),
            Map.entry("shopping", "shopping/altro-shopping"),
            Map.entry("transportation", "trasporti/altro-trasporti"),
            Map.entry("vehicle", "trasporti/altro-trasporti"));

    private BudgetBakersCategories() {
    }

    /** La voce suggerita per questa categoria; {@code null} se né lei né il gruppo sono noti. */
    static String templateFor(String externalId, String group) {
        if (externalId != null && externalId.endsWith(SUFFISSO)) {
            String voce = PER_CATEGORIA.get(externalId.substring(0, externalId.length() - SUFFISSO.length()));
            if (voce != null) {
                return voce;
            }
        }
        return group == null ? null : PER_GRUPPO.get(group);
    }

    static int knownCategories() {
        return PER_CATEGORIA.size();
    }

    static java.util.stream.Stream<String> allTemplates() {
        return java.util.stream.Stream.concat(PER_CATEGORIA.values().stream(), PER_GRUPPO.values().stream());
    }
}
