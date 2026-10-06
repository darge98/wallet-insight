import { describe, expect, it } from 'vitest';

import {
  MovementResponse,
  MovementsPageResponse,
  toCategoryBreakdownSlice,
  toFinanceRecord,
  toRecordSearchResult,
} from './movement-contract';

/**
 * La traduzione fra la forma del server e quella del dominio.
 *
 * Qui stanno le due regole che i due lati devono applicare allo stesso modo — la
 * natura di un movimento e il significato di un campo assente — ed è il punto in
 * cui possono divergere senza che niente fallisca: il server continua a
 * rispondere, il client continua a disegnare, e i numeri smettono di tornare.
 */
describe('contratto dei movimenti', () => {
  const base: MovementResponse = {
    id: '019b4c60-2f4f-7c03-8e44-2b8f6d3c0e72',
    accountId: '019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61',
    source: 'budget-bakers',
    amountCents: -4453,
    currencyCode: 'EUR',
    date: '2026-09-19',
    direction: 'expense',
    state: 'cleared',
  };

  it('il giroconto vince sul verso', () => {
    // Le due gambe di un trasferimento hanno una direzione ciascuna: senza questa
    // precedenza una delle due risulterebbe una spesa come tutte le altre, e
    // "quanto ho speso" conterebbe denaro che non ha lasciato le tasche di nessuno.
    const record = toFinanceRecord({ ...base, direction: 'expense', transferState: 'paired' });

    expect(record.type).toBe('transfer');
  });

  it("tutto ciò che non è dichiarato entrata è un'uscita", () => {
    // Un verso che la sorgente non dichiara resta un'uscita: il denaro se n'è
    // andato comunque, e lasciarlo fuori lo renderebbe invisibile a ogni filtro.
    expect(toFinanceRecord({ ...base, direction: 'unknown' }).type).toBe('expense');
    expect(toFinanceRecord({ ...base, direction: 'income' }).type).toBe('income');
  });

  it("l'importo conserva il segno che arriva dal server", () => {
    expect(toFinanceRecord(base).amount).toEqual({ amount: -4453, currency: 'EUR' });
  });

  it('un campo omesso dal server diventa null, non undefined', () => {
    // Il server omette i campi vuoti (`default-property-inclusion: non_null`);
    // nel dominio l'assenza si scrive `null`, e una sola volta.
    const record = toFinanceRecord(base);

    expect(record.description).toBeNull();
    expect(record.counterParty).toBeNull();
    expect(record.categoryId).toBeNull();
  });

  it('di ogni campo prende il valore da mostrare, e basta', () => {
    // Il server manda anche da dove quel valore viene; qui non serve a nessuno,
    // e portarselo dietro vorrebbe dire due verità da tenere allineate.
    const record = toFinanceRecord({ ...base, description: 'Spesa della settimana' });

    expect(record.description).toBe('Spesa della settimana');
  });

  it("un campo che l'utente ha svuotato si mostra come un campo che non c'è", () => {
    // Il server distingue «nessuno ha detto niente» da «l'ho svuotato io», e fa
    // bene: il primo riceve gli aggiornamenti, il secondo no. Da mostrare sono
    // però la stessa cosa, e il titolo della riga deve poter ripiegare sul resto
    // invece di restare bianco.
    const record = toFinanceRecord({ ...base, description: '', counterParty: '   ' });

    expect(record.description).toBeNull();
    expect(record.counterParty).toBeNull();
  });

  it('legge i totali accanto alla pagina', () => {
    const response: MovementsPageResponse = {
      page: { items: [base], total: 29, index: 0, size: 25, pageCount: 2 },
      totals: {
        incomeCents: 202794,
        expensesCents: 131180,
        netCents: 71614,
        currencyCode: 'EUR',
        count: 29,
      },
    };

    const result = toRecordSearchResult(response);

    expect(result.page.total).toBe(29);
    // Le uscite restano positive: il segno meno accanto a "Uscite" direbbe la
    // stessa cosa due volte.
    expect(result.totals.expenses).toEqual({ amount: 131180, currency: 'EUR' });
    expect(result.totals.net).toEqual({ amount: 71614, currency: 'EUR' });
  });

  it('rifiuta una valuta fuori contratto invece di ripiegare', () => {
    expect(() => toFinanceRecord({ ...base, currencyCode: 'XXX' })).toThrow();
  });

  it('porta la quota in percentuale, come la dichiara il dominio', () => {
    // Il server la manda fra 0 e 1: la conversione sta qui e in un posto solo,
    // altrimenti prima o poi si moltiplica per cento due volte.
    const slice = toCategoryBreakdownSlice({
      categoryId: '019b4c60-2f4f-7d04-9f55-3c9a7e4d1f83',
      name: 'Ristoranti',
      totalCents: 10550,
      count: 5,
      share: 0.08,
    });

    expect(slice.share).toBeCloseTo(8);
    expect(slice.color).toBeNull();
  });
});
