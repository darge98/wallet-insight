package it.walletinsight.shared.money;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(CurrencyCode left, CurrencyCode right) {
        super("Impossibile operare su valute diverse: %s e %s.".formatted(left, right));
    }
}
