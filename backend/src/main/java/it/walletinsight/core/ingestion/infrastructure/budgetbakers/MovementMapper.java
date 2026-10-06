package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.domain.Classification;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.movements.domain.Transfer;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.ConvertedAmountDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.TransferDto;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Traduce un movimento dalla forma di BudgetBakers all'aggregato {@link Movement}.
 *
 * È l'unico punto in cui le stranezze della sorgente hanno diritto di esistere:
 * oltre questo confine il dominio non sa che BudgetBakers esiste. I valori di
 * filo (`waitForAssign`, `expense`) stanno qui dentro e non sulle enum del
 * dominio — è la stessa scelta fatta per {@code AccountKind}, e serve a non
 * lasciare il vocabolario di una sorgente dentro tipi che dovranno servirne altre.
 *
 * Quando incontra un valore che la specifica non dichiara non scarta il
 * movimento: lo mappa su {@code UNKNOWN} e scrive un ERROR con l'identificativo e
 * il valore grezzo, così il dato entra comunque — il suo importo è un fatto, e
 * perderlo falserebbe il saldo — e la novità resta visibile.
 *
 * Nota e controparte diventano il *valore della sorgente* dei due testi contesi
 * del movimento, mai quello dell'utente: un import non ha niente da dire su
 * quello, e infatti non c'è modo di passarglielo.
 */
@Component
class MovementMapper {

    private static final Logger log = LoggerFactory.getLogger(MovementMapper.class);

    /** I centesimi sono due cifre: oltre, la conversione perde informazione. */
    private static final int MINOR_UNIT_DIGITS = 2;

    /**
     * L'aggregato corrispondente, oppure {@code null} se il conto non è fra quelli noti.
     *
     * Un movimento senza il suo conto non è salvabile — poggia su di esso — e non è
     * nemmeno una condizione che l'utente possa correggere. Torna `null` e chi
     * chiama lo conta: fermare l'intero import per un movimento orfano perderebbe
     * anche tutti gli altri.
     */
    Movement toMovement(RecordDto dto,
                        UserId userId,
                        IngestionSource source,
                        Map<String, AccountId> accountsByExternalId,
                        Map<String, CategoryId> categoriesByExternalId) {
        AccountId accountId = accountsByExternalId.get(dto.accountId());
        if (accountId == null) {
            log.error("Movimento {}: il conto {} ('{}') non è fra quelli importati. "
                            + "Il movimento non ha dove appoggiarsi e resta fuori.",
                    dto.id(), dto.accountId(), dto.accountName());
            return null;
        }

        Money amount = Money.of(toCents(dto), currencyOf(dto));
        return Movement.imported(
                userId,
                accountId,
                source,
                dto.id(),
                amount,
                toConverted(dto, amount),
                dto.recordDate(),
                toDirection(dto),
                toState(dto),
                dto.note(),
                dto.counterParty(),
                toClassification(dto, categoriesByExternalId),
                toTransfer(dto));
    }

    /**
     * La categoria del movimento, risolta nell'identificatore di Wallet Insights.
     *
     * Una categoria che non è fra quelle allineate **non** fa scartare il
     * movimento, a differenza di un conto mancante: un conto è ciò su cui il
     * movimento poggia e senza non esiste dove metterlo, mentre una categoria
     * mancante lascia solo un movimento da classificare. Le due colonne grezze
     * restano valorizzate, quindi la classificazione si potrà ricostruire al
     * prossimo import senza rileggere niente dalla sorgente.
     */
    private Classification toClassification(
            RecordDto dto, Map<String, CategoryId> categoriesByExternalId) {
        CategoryDto category = dto.category();
        if (category == null) {
            return Classification.none();
        }
        CategoryId categoryId = categoriesByExternalId.get(category.id());
        if (categoryId == null) {
            log.error("Movimento {}: la categoria {} ('{}') non è fra quelle importate. "
                            + "Il movimento entra non classificato, con la traccia della sorgente.",
                    dto.id(), category.id(), category.name());
        }
        return Classification.fromSource(categoryId, category.id(), category.name());
    }

    /**
     * Una valuta che Wallet Insights non gestisce ferma l'import invece di far passare il
     * movimento con quella del conto: finirebbe sommato agli altri e il totale non
     * direbbe più niente.
     */
    private static CurrencyCode currencyOf(RecordDto dto) {
        String code = dto.amount() == null ? null : dto.amount().currencyCode();
        try {
            return CurrencyCode.valueOf(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException cause) {
            throw new IllegalStateException(
                    "Il movimento %s è in %s, una valuta che Wallet Insights non gestisce ancora."
                            .formatted(dto.id(), code), cause);
        }
    }

    /**
     * Il movimento in euro, al cambio del suo giorno.
     *
     * Un movimento in euro lo è già, e non passa per il tasso: niente arrotondamenti
     * da spiegare. Negli altri casi un cambio mancante ferma l'import invece di far
     * entrare il movimento: contato a zero, o nella valuta sbagliata, falserebbe ogni
     * totale senza che nulla lo segnali. Il giro dopo riprova.
     */
    private Money toConverted(RecordDto dto, Money amount) {
        if (amount.currency() == Movement.TOTALS_CURRENCY) {
            return amount;
        }
        ConvertedAmountDto converted = dto.convertedAmount();
        if (converted == null || converted.value() == null || converted.error() != null
                || !Movement.TOTALS_CURRENCY.name().equalsIgnoreCase(converted.currencyCode())) {
            throw new IllegalStateException(
                    "Il movimento %s delle %s è in %s e BudgetBakers non ne dà il cambio in %s: %s"
                            .formatted(dto.id(), dto.recordDate(), amount.currency(),
                                    Movement.TOTALS_CURRENCY,
                                    converted == null ? "conversione assente" : converted.error()));
        }
        // Prodotto per un tasso, ha quasi sempre più di due decimali: arrotondarlo è atteso.
        long cents = converted.value().movePointRight(MINOR_UNIT_DIGITS)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return Money.of(cents, Movement.TOTALS_CURRENCY);
    }

    /**
     * Converte l'importo decimale in centesimi interi.
     *
     * Non passa mai per un `double`: `movePointRight` sposta la virgola sul
     * `BigDecimal`, quindi -9.99 diventa esattamente -999 e non -998.
     */
    private long toCents(RecordDto dto) {
        BigDecimal value = dto.amount().value();
        int scale = value.stripTrailingZeros().scale();
        if (scale > MINOR_UNIT_DIGITS) {
            log.error("Movimento {}: importo {} {} ha {} decimali, piu' dei {} rappresentabili in centesimi. "
                            + "Arrotondato, ma il valore originale e' andato perso.",
                    dto.id(), value.toPlainString(), dto.amount().currencyCode(), scale, MINOR_UNIT_DIGITS);
        }
        return value.movePointRight(MINOR_UNIT_DIGITS)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private MovementDirection toDirection(RecordDto dto) {
        MovementDirection direction = switch (String.valueOf(dto.recordType())) {
            case "income" -> MovementDirection.INCOME;
            case "expense" -> MovementDirection.EXPENSE;
            default -> MovementDirection.UNKNOWN;
        };
        warnIfUnknown(direction == MovementDirection.UNKNOWN, dto.id(), "recordType", dto.recordType());
        return direction;
    }

    private MovementState toState(RecordDto dto) {
        MovementState state = switch (String.valueOf(dto.recordState())) {
            case "reconciled" -> MovementState.RECONCILED;
            case "cleared" -> MovementState.CLEARED;
            case "uncleared" -> MovementState.UNCLEARED;
            case "void" -> MovementState.VOID;
            case "waitForAssign" -> MovementState.WAIT_FOR_ASSIGN;
            default -> MovementState.UNKNOWN;
        };
        warnIfUnknown(state == MovementState.UNKNOWN, dto.id(), "recordState", dto.recordState());
        return state;
    }

    private Transfer toTransfer(RecordDto dto) {
        TransferDto transfer = dto.transfer();
        if (transfer == null) {
            return null;
        }
        Transfer.TransferState state = switch (String.valueOf(transfer.type())) {
            case "paired" -> Transfer.TransferState.PAIRED;
            case "unpaired" -> Transfer.TransferState.UNPAIRED;
            default -> Transfer.TransferState.UNKNOWN;
        };
        warnIfUnknown(state == Transfer.TransferState.UNKNOWN, dto.id(), "transfer.type", transfer.type());
        return new Transfer(state, transfer.transferId());
    }

    private void warnIfUnknown(boolean unknown, String id, String field, String rawValue) {
        if (unknown) {
            log.error("Movimento {}: valore '{}' non previsto per {}. "
                    + "Non e' nella specifica di BudgetBakers: il movimento passa come UNKNOWN.",
                    id, rawValue, field);
        }
    }
}
