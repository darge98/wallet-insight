package it.walletinsight.bff.accounts;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.platform.web.KebabCase;

/**
 * Un conto come lo vede il frontend.
 *
 * Porta **due** nomi, non uno: `name` è quello da mostrare, `sourceName` è come
 * il conto si chiama nella sorgente. Servono entrambi perché l'interfaccia possa
 * dire "rinominato da te, si chiama X là" e offrire di tornare indietro senza
 * chiedere al server qual era il nome originale.
 *
 * `source` dice da dove il conto arriva ed è parte della sua identità: un conto
 * di Wallet Insights non esiste per conto proprio, è la controparte di qualcosa. In
 * kebab-case come ogni enum del contratto.
 *
 * Del numero di conto escono solo le ultime quattro cifre. Il valore intero resta
 * nel database — servirà il giorno di un riconoscimento PSD2 o di un export SEPA —
 * ma da qui non esce: quello che la sorgente chiama `bankAccountNumber` non è
 * sempre un IBAN, e sui conti carta è il numero della carta per intero. Farlo
 * viaggiare, finire in una cache del browser o in un log di proxy per mostrare
 * quattro cifre in fondo a un nome non è uno scambio che conviene. Le quattro
 * cifre bastano a distinguere due conti nella stessa banca, che è l'unica cosa
 * per cui l'interfaccia lo usa.
 *
 * Ci sono **due** saldi. `initialBalanceCents` è il punto di partenza dichiarato
 * dalla sorgente; `balanceCents` è quello di oggi, cioè il primo più la somma dei
 * movimenti. Nessuno dei due sta memorizzato come "saldo corrente": quella
 * colonna invecchierebbe fra un import e l'altro e darebbe due verità in
 * disaccordo. La somma la fa PostgreSQL a ogni richiesta, e i due campi restano
 * entrambi perché la loro differenza è esattamente quanto i movimenti importati
 * dicono di aver spostato — il modo più diretto di accorgersi che ne manca uno.
 *
 * @param id                   identificatore di Wallet Insights (UUIDv7), non quello della sorgente
 * @param source               la sorgente da cui il conto è stato importato, in kebab-case
 * @param name                 il nome mostrato: della sorgente finché l'utente non lo cambia
 * @param sourceName           il nome nella sorgente, riscritto a ogni import
 * @param kind                 natura del conto normalizzata, in kebab-case
 * @param currencyCode         valuta ISO 4217
 * @param initialBalanceCents  saldo di partenza in centesimi interi
 * @param balanceCents         saldo corrente: il precedente più i movimenti importati
 * @param color                colore scelto dall'utente (`#rrggbb`), assente se non l'ha scelto
 * @param ibanLast4            ultime quattro cifre del numero di conto, assenti se non ne ha
 * @param archived             archiviato nella sorgente: resta, ma la UI può nasconderlo
 * @param excludedFromStats    escluso dai totali per scelta fatta nella sorgente
 */
public record AccountResponse(
        String id,
        String source,
        String name,
        String sourceName,
        String kind,
        String currencyCode,
        long initialBalanceCents,
        long balanceCents,
        String color,
        String ibanLast4,
        boolean archived,
        boolean excludedFromStats) {

    /**
     * Le ultime quattro cifre, ignorando tutto ciò che cifra non è.
     *
     * Quello che BudgetBakers chiama `bankAccountNumber` è testo libero, e nei dati
     * reali contiene di tutto: `374641710491001`, `F010000712861`, `439772******6181
     * EUR` (già mascherato da loro), e per un conto PayPal la stringa `PayPal EUR`,
     * che di cifre non ne ha nessuna. Tagliare gli ultimi quattro caratteri darebbe
     * ` EUR`: un'etichetta che non distingue niente da niente. Le cifre, invece, se
     * ci sono sono quelle giuste; se non ci sono, il campo sparisce — che è la
     * verità, non c'è nessun numero da mostrare.
     */
    private static String last4(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String cifre = accountNumber.replaceAll("\\D", "");
        if (cifre.isEmpty()) {
            return null;
        }
        return cifre.length() <= 4 ? cifre : cifre.substring(cifre.length() - 4);
    }

    /**
     * @param movedCents quanto i movimenti importati hanno spostato su questo conto
     */
    public static AccountResponse from(Account account, long movedCents) {
        return new AccountResponse(
                account.id().toString(),
                KebabCase.from(account.source()),
                account.name(),
                account.sourceName(),
                KebabCase.from(account.kind()),
                account.currency().name(),
                account.initialBalance().amount(),
                account.initialBalance().amount() + movedCents,
                account.color(),
                last4(account.iban()),
                account.archived(),
                account.excludedFromStats());
    }
}
