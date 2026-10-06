import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { asCategoryId, Category } from '@wallet/shared-domain';

import { CategoryPicker } from './category-picker';

function categoria(id: string, name: string, parentId: string | null = null): Category {
  return {
    id: asCategoryId(id),
    parentId: parentId === null ? null : asCategoryId(parentId),
    name,
    color: null,
  };
}

const CATEGORIE = [
  categoria('cibo', 'Cibo e bevande'),
  categoria('bar', 'Bar e caffè', 'cibo'),
  categoria('spesa', 'Spesa', 'cibo'),
  categoria('trasporti', 'Trasporti'),
  categoria('carburante', 'Carburante', 'trasporti'),
  categoria('taxi', 'Taxi', 'trasporti'),
];

describe('CategoryPicker', () => {
  let fixture: ComponentFixture<CategoryPicker>;

  const campo = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
  // Il pannello si apre nel livello superiore della pagina, fuori dal componente.
  const opzioni = () =>
    [...document.querySelectorAll<HTMLElement>('[role="option"]')].map((option) =>
      option.textContent?.trim(),
    );

  async function scrivi(testo: string): Promise<void> {
    campo().click();
    campo().value = testo;
    campo().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    // jsdom non sa scorrere: l'opzione attiva resta dov'è.
    Element.prototype.scrollIntoView ??= () => undefined;
    fixture = TestBed.createComponent(CategoryPicker);
    fixture.componentRef.setInput('categories', CATEGORIE);
    fixture.componentRef.setInput('emptyLabel', 'Nessuna');
    fixture.componentRef.setInput('label', 'Categoria');
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('a riposo mostra la scelta', async () => {
    fixture.componentRef.setInput('value', 'taxi');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(campo().value).toBe('Taxi');
  });

  it('aperto mostra tutte le sottocategorie, non le macro', async () => {
    campo().click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(opzioni()).toEqual(['Nessuna', 'Bar e caffè', 'Spesa', 'Carburante', 'Taxi']);
  });

  it('cerca dentro il nome, senza badare ad accenti e maiuscole', async () => {
    await scrivi('CAFFE');

    expect(opzioni()).toEqual(['Bar e caffè']);
  });

  it('il nome della macro porta con sé tutte le sue sottocategorie', async () => {
    await scrivi('trasp');

    expect(opzioni()).toEqual(['Carburante', 'Taxi']);
  });

  it('scegliere un’opzione ne fa il valore', async () => {
    await scrivi('spe');

    const spesa = document.querySelector<HTMLElement>('[role="option"]');
    spesa?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    spesa?.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.value()).toBe('spesa');
    expect(campo().value).toBe('Spesa');
  });
});
