import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { asBudgetId, Budget, BudgetDraft } from '@wallet/budgets-domain';
import { asCategoryId, Category, money } from '@wallet/shared-domain';

import { BudgetEditor } from './budget-editor';

function categoria(id: string, name: string, parentId: string | null): Category {
  return {
    id: asCategoryId(id),
    parentId: parentId ? asCategoryId(parentId) : null,
    name,
    color: null,
  };
}

const CIBO = categoria('cibo', 'Cibo e bevande', null);
const SVAGO = categoria('svago', 'Tempo libero', null);
const RISTORANTI = categoria('ristoranti', 'Ristoranti', 'cibo');
const SPESA = categoria('spesa', 'Spesa', 'cibo');
const BAR = categoria('bar', 'Bar cafe', 'cibo');
const CINEMA = categoria('cinema', 'Cinema', 'svago');

/** Un altro budget tiene già il Bar: la macro «Cibo e bevande» deve lasciarlo lì. */
const USCITE: Budget = {
  id: asBudgetId('uscite'),
  parentId: null,
  name: 'Uscite',
  categoryIds: [BAR.id],
  limit: money(10_000),
};

describe('BudgetEditor', () => {
  let fixture: ComponentFixture<BudgetEditor>;
  let emitted: BudgetDraft[];

  beforeEach(() => {
    fixture = TestBed.createComponent(BudgetEditor);
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('currentMonth', '2026-10');
    fixture.componentRef.setInput('budgets', [USCITE]);
    fixture.componentRef.setInput('categories', [CIBO, SVAGO, RISTORANTI, SPESA, BAR, CINEMA]);
    emitted = [];
    fixture.componentInstance.saved.subscribe((draft) => emitted.push(draft));
    fixture.detectChanges();
  });

  const root = () => fixture.nativeElement as HTMLElement;

  function groupBox(label: string): HTMLInputElement {
    const labelElement = [...root().querySelectorAll('label')].find(
      (element) => element.textContent?.trim() === label,
    );
    const box = labelElement && root().querySelector<HTMLInputElement>(`#${labelElement.htmlFor}`);
    if (!box) throw new Error(`Gruppo assente: ${label}`);
    return box;
  }

  function click(element: HTMLElement): void {
    element.click();
    fixture.detectChanges();
  }

  function type(selector: string, value: string): void {
    const field = root().querySelector<HTMLInputElement>(selector);
    if (!field) throw new Error(`Campo assente: ${selector}`);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('la macro prende tutte le categorie libere del gruppo, non quelle di un altro budget', async () => {
    click(groupBox('Cibo e bevande'));
    type('#budget-name', 'Cibo');
    type('#budget-limit', '400');

    root().querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(emitted).toHaveLength(1);
    expect([...(emitted[0]?.categoryIds ?? [])].sort()).toEqual([RISTORANTI.id, SPESA.id].sort());
    expect(emitted[0]?.limit).toEqual(money(40_000));
  });

  it('la macro scelta per intero si toglie con un secondo clic', () => {
    const box = groupBox('Cibo e bevande');
    click(box);
    expect(box.checked).toBe(true);

    click(box);
    expect(box.checked).toBe(false);
    expect(box.indeterminate).toBe(false);
  });

  it('una scelta parziale si vede sulla macro', () => {
    click(groupBox('Cibo e bevande').parentElement?.querySelector('button') as HTMLElement);
    const spesa = [...root().querySelectorAll('label')]
      .find((element) => element.textContent?.includes('Spesa'))
      ?.querySelector('input');
    click(spesa as HTMLElement);

    expect(groupBox('Cibo e bevande').indeterminate).toBe(true);
  });

  it('un sotto-budget oltre quanto resta al principale non si salva', async () => {
    fixture.componentRef.setInput('parent', USCITE);
    fixture.detectChanges();
    click(groupBox('Uscite'));
    type('#budget-name', 'Bar');
    // Il principale ha 100 €: 150 non ci stanno.
    type('#budget-limit', '150');

    root().querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(emitted).toEqual([]);
  });
});
