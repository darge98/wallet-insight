package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountBalanceDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountDto;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Traduce un conto dalla forma di BudgetBakers all'aggregato {@link Account}.
 *
 * Come {@link MovementMapper}, è l'unico punto in cui le stranezze della sorgente
 * hanno diritto di esistere, e un valore che la specifica non dichiara non fa
 * scartare il conto: il tipo diventa {@code UNKNOWN} con un ERROR nel log.
 * Perdere un conto significherebbe perdere più avanti anche tutti i suoi
 * movimenti.
 *
 * Produce direttamente l'aggregato e non un tipo intermedio: il conto tradotto
 * *è* un conto di Wallet Insights, e un record di passaggio ripeterebbe gli stessi campi
 * per poi buttarli una riga dopo.
 */
@Component
class AccountMapper {

    private static final Logger log = LoggerFactory.getLogger(AccountMapper.class);

    /** I centesimi sono due cifre: oltre, la conversione perde informazione. */
    private static final int MINOR_UNIT_DIGITS = 2;

    /**
     * I tipi dichiarati da BudgetBakers, tradotti nei nomi di Wallet Insights.
     *
     * Attenzione a non leggere troppo nel tipo: nei dati reali una carta
     * prepagata compare come {@code General}, non come {@code CreditCard}. È
     * un'etichetta che l'utente sceglie, non un fatto accertato sul conto.
     */
    private static AccountKind kindOf(String wireValue) {
        if (wireValue == null) {
            return AccountKind.UNKNOWN;
        }
        return switch (wireValue.toLowerCase(Locale.ROOT)) {
            case "general" -> AccountKind.GENERAL;
            case "cash" -> AccountKind.CASH;
            case "currentaccount" -> AccountKind.CURRENT_ACCOUNT;
            case "creditcard" -> AccountKind.CREDIT_CARD;
            case "savingaccount" -> AccountKind.SAVINGS;
            case "insurance" -> AccountKind.INSURANCE;
            case "investment" -> AccountKind.INVESTMENT;
            case "loan" -> AccountKind.LOAN;
            case "mortgage" -> AccountKind.MORTGAGE;
            case "overdraft" -> AccountKind.OVERDRAFT;
            case "bonus" -> AccountKind.BONUS;
            default -> AccountKind.UNKNOWN;
        };
    }

    Account toAccount(AccountDto dto, UserId userId, IngestionSource source) {
        AccountKind kind = kindOf(dto.accountType());
        if (kind == AccountKind.UNKNOWN) {
            log.error("Conto {} ('{}'): tipo '{}' non previsto dalla specifica di BudgetBakers. "
                            + "Il conto entra come UNKNOWN.",
                    dto.id(), dto.name(), dto.accountType());
        }

        AccountBalanceDto balance = dto.balance();
        CurrencyCode currency = currencyOf(dto);
        long iniziale = toCents(dto, balance == null ? null : balance.initial(), "balance.initial");

        // Il saldo che calcolano loro non viene salvato: il nostro sara' la somma dei
        // movimenti. Loggarlo accanto al nostro e' il modo piu' semplice di accorgersi
        // che un import ha perso qualcosa per strada.
        long loro = toCents(dto, balance == null ? null : balance.currentBalance(), "balance.currentBalance");
        log.debug("Conto {} ('{}'): iniziale {} cent, attuale nella sorgente {} cent, {} movimenti dichiarati",
                dto.id(), dto.name(), iniziale, loro,
                dto.recordStats() == null ? "?" : dto.recordStats().recordCount());

        return Account.imported(
                userId,
                source,
                dto.id(),
                dto.name(),
                kind,
                currency,
                Money.of(iniziale, currency),
                dto.bankAccountNumber(),
                Boolean.TRUE.equals(dto.archived()),
                Boolean.TRUE.equals(dto.excludeFromStats()));
    }

    /**
     * Una valuta che Wallet Insights non gestisce ferma l'import invece di far passare il
     * conto con una valuta sbagliata: i suoi movimenti sarebbero sommati insieme
     * agli altri, e il totale non direbbe più niente.
     */
    private static CurrencyCode currencyOf(AccountDto dto) {
        try {
            return CurrencyCode.valueOf(dto.currencyCode().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException cause) {
            throw new IllegalStateException(
                    "Il conto '%s' è in %s, una valuta che Wallet Insights non gestisce ancora."
                            .formatted(dto.name(), dto.currencyCode()), cause);
        }
    }

    /**
     * Converte un importo decimale in centesimi interi.
     *
     * Non passa mai per un `double`: `movePointRight` sposta la virgola sul
     * `BigDecimal`, quindi 13397.67 diventa esattamente 1339767.
     */
    private long toCents(AccountDto dto, BigDecimal value, String campo) {
        if (value == null) {
            return 0L;
        }
        int scale = value.stripTrailingZeros().scale();
        if (scale > MINOR_UNIT_DIGITS && log.isErrorEnabled()) {
            log.error("Conto {} ('{}'): {} vale {} con {} decimali, piu' dei {} rappresentabili "
                            + "in centesimi. Arrotondato, ma il valore originale e' andato perso.",
                    dto.id(), dto.name(), campo, value.toPlainString(), scale, MINOR_UNIT_DIGITS);
        }
        return value.movePointRight(MINOR_UNIT_DIGITS)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
