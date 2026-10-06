import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { TokenForm } from './token-form';

const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234';

describe('TokenForm', () => {
  let fixture: ComponentFixture<TokenForm>;
  let inviati: string[];

  const root = () => fixture.nativeElement as HTMLElement;
  const campo = () => root().querySelector('input') as HTMLInputElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(TokenForm);
    fixture.componentRef.setInput('fieldId', 'token');
    inviati = [];
    fixture.componentInstance.submitted.subscribe((token) => inviati.push(token));
    fixture.detectChanges();
  });

  async function invia(valore: string): Promise<void> {
    campo().value = valore;
    campo().dispatchEvent(new Event('input'));
    root().querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('manda il token senza spazi e svuota il campo', async () => {
    await invia(`  ${TOKEN} `);

    expect(inviati).toEqual([TOKEN]);
    expect(campo().value).toBe('');
  });

  it('un testo che non è un token non parte e lo dice', async () => {
    await invia('non-un-token');

    expect(inviati).toEqual([]);
    expect(root().textContent).toContain('Token non valido.');
  });
});
