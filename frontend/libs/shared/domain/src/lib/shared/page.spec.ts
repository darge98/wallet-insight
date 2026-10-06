import { describe, expect, it } from 'vitest';

import { buildPage, emptyPage } from './page';

const ITEMS = Array.from({ length: 7 }, (_, index) => index);

describe('buildPage', () => {
  it('taglia la pagina richiesta e calcola il numero di pagine', () => {
    const page = buildPage(ITEMS, { index: 1, size: 3 });

    expect(page.items).toEqual([3, 4, 5]);
    expect(page.pageCount).toBe(3);
    expect(page.total).toBe(7);
  });

  it("riporta l'indice dentro i limiti disponibili", () => {
    expect(buildPage(ITEMS, { index: 99, size: 3 }).index).toBe(2);
    expect(buildPage(ITEMS, { index: -5, size: 3 }).index).toBe(0);
  });

  it('gestisce le collezioni vuote', () => {
    expect(buildPage([], { index: 0, size: 10 }).items).toEqual([]);
    expect(emptyPage().total).toBe(0);
  });
});
