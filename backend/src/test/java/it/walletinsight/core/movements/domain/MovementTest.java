package it.walletinsight.core.movements.domain;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regola che governa questo aggregato: tre campi li scrive l'import quando il
 * movimento entra e poi non li tocca più, tutto il resto lo riscrive ogni volta.
 *
 * Sono un valore ciascuno, non una coppia «sorgente più utente» come fra V9 e
 * V11: è ciò che rende `null` di nuovo capace di dire «vuoto», e quindi ciò che
 * rende possibile svuotare un campo.
 */
class MovementTest {

    private static final UserId MARTA = UserId.newId();
    private static final AccountId CREDEM = AccountId.newId();
    private static final CategoryId RISTORANTI = CategoryId.newId();
    private static final CategoryId SPESA = CategoryId.newId();
    private static final LocalDate GIORNO = LocalDate.of(2026, 9, 18);

    @Test
    void unMovimentoImportatoPortaIDatiDellImportNeiSuoiCampi() {
        // Non ci sono due posti dove guardare: l'import scrive nel campo, e il
        // campo e' il dato. E' cio' che permette a `null` di voler dire «vuoto».
        Movement movimento = importato("PAGAMENTO DEBINT", "Conad", RISTORANTI);

        assertThat(movimento.description()).isEqualTo("PAGAMENTO DEBINT");
        assertThat(movimento.counterParty()).isEqualTo("Conad");
        assertThat(movimento.classification().category()).isEqualTo(RISTORANTI);
    }

    @Test
    void riscrivereLaDescrizioneSostituisceQuellaImportata() {
        Movement riscritto = importato("PAGAMENTO DEBINT", "Conad", RISTORANTI)
                .describedAs("Spesa della settimana");

        assertThat(riscritto.description()).isEqualTo("Spesa della settimana");
    }

    @Test
    void ilPagatoreSiPuoIndicareAncheQuandoLaSorgenteTace() {
        // Succede su piu' di un movimento su tre: la sorgente non lo dira' mai, e
        // scriverlo qui e' l'unico modo di sapere da chi o dove e' passato il denaro.
        Movement attribuito = importato("Bonifico", null, RISTORANTI).attributedTo("Pirru");

        assertThat(attribuito.counterParty()).isEqualTo("Pirru");
    }

    @Test
    void spostareLaCategoriaLaSostituisce() {
        Movement spostato = importato("Cena", "Piadineria", RISTORANTI).classifiedAs(SPESA);

        assertThat(spostato.classification().category()).isEqualTo(SPESA);
        // La traccia grezza resta: non serve a mostrare niente, serve al riaggancio.
        assertThat(spostato.classification().sourceName()).isEqualTo("Ristoranti");
    }

    @Test
    void unImportSuccessivoAggiornaIFattiENonITreCampi() {
        Movement mio = importato("PAGAMENTO DEBINT", "Conad", RISTORANTI)
                .describedAs("Spesa della settimana")
                .attributedTo("Pirru")
                .classifiedAs(SPESA);

        Movement dopo = mio.refreshedFrom(importato("ALTRO TRACCIATO", "Amazon", RISTORANTI));

        // I tre campi li ha scritti l'import la prima volta, e da allora sono del
        // movimento: un giro successivo non ha niente da dire su di loro.
        assertThat(dopo.description()).isEqualTo("Spesa della settimana");
        assertThat(dopo.counterParty()).isEqualTo("Pirru");
        assertThat(dopo.classification().category()).isEqualTo(SPESA);
        // E l'identificatore non cambia mai: e' quello a cui tutto il resto si appoggia.
        assertThat(dopo.id()).isEqualTo(mio.id());
    }

    @Test
    void unTestoSvuotatoLasciaIlCampoSenzaNiente() {
        // E' la ragione per cui i campi sono uno solo: con due, `null` voleva gia'
        // dire «segui l'altro», e svuotare rimetteva in mostra il tracciato.
        Movement svuotato = importato("PAGAMENTO DEBINT", "Conad", RISTORANTI)
                .describedAs("Spesa della settimana")
                .describedAs("");

        assertThat(svuotato.description()).isNull();
    }

    @Test
    void ancheUnTestoDiSoliSpaziEUnCampoVuoto() {
        Movement svuotato = importato("PAGAMENTO DEBINT", "Conad", RISTORANTI).describedAs("   ");

        assertThat(svuotato.description()).isNull();
    }

    @Test
    void classificareANullLasciaIlMovimentoSenzaCategoria() {
        Movement senza = importato("Cena", "Piadineria", RISTORANTI)
                .classifiedAs(SPESA)
                .classifiedAs(null);

        assertThat(senza.classification().category()).isNull();
    }

    @Test
    void unMovimentoDaContabilizzareNonSiCorregge() {
        // Quando la banca lo conferma, la sorgente non lo aggiorna: ne crea uno nuovo
        // con un altro identificativo e fa sparire questo. Quello che ci si scrive
        // sopra adesso morirebbe con lui.
        Movement inAttesa = conStato(MovementState.UNCLEARED);

        assertThat(inAttesa.editable()).isFalse();
    }

    @Test
    void tuttiGliAltriStatiSiCorreggono() {
        assertThat(conStato(MovementState.CLEARED).editable()).isTrue();
        assertThat(conStato(MovementState.RECONCILED).editable()).isTrue();
        assertThat(conStato(MovementState.WAIT_FOR_ASSIGN).editable()).isTrue();
    }

    private static Movement conStato(MovementState stato) {
        return new Movement(MovementId.newId(), MARTA, CREDEM, IngestionSource.BUDGET_BAKERS,
                "rec-1", Money.of(-999L), istante(GIORNO), MovementDirection.EXPENSE, stato,
                "PAGAMENTO DEBINT", "Conad",
                Classification.fromSource(RISTORANTI, "cat-1", "Ristoranti"), null);
    }

    @Test
    void unMovimentoAnnullatoNonPesaSulSaldo() {
        Movement annullato = new Movement(MovementId.newId(), MARTA, CREDEM,
                IngestionSource.BUDGET_BAKERS, "rec-1", Money.of(-999L), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.VOID, null, null, null, null);

        assertThat(annullato.countsTowardsBalance()).isFalse();
        // Tutti gli altri stati sono denaro che si e' mosso davvero.
        assertThat(importato("x", null, null).countsTowardsBalance()).isTrue();
    }

    private static Movement importato(String nota, String controparte, CategoryId categoria) {
        return Movement.imported(MARTA, CREDEM, IngestionSource.BUDGET_BAKERS, "rec-1",
                Money.of(-999L), Money.of(-999L), istante(GIORNO), MovementDirection.EXPENSE, MovementState.CLEARED,
                nota, controparte,
                categoria == null
                        ? Classification.none()
                        : Classification.fromSource(categoria, "cat-1", "Ristoranti"),
                null);
    }

    /** La mezzanotte UTC del giorno: com'è codificata la maggior parte dei movimenti della sorgente. */
    private static Instant istante(LocalDate giorno) {
        return giorno.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
