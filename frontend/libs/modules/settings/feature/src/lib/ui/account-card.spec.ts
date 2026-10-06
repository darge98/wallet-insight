import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { Account } from '@wallet/accounts-domain';
import { asAccountId } from '@wallet/shared-domain';

import { AccountCard } from './account-card';

const CONTO: Account = {
  id: asAccountId('0199ab7c-0000-7000-8000-0000000000aa'),
  source: 'budget-bakers',
  name: 'Credem',
  sourceName: 'Credem',
  kind: 'current-account',
  initialBalance: { amount: 892_001, currency: 'EUR' },
  balance: { amount: 1_335_207, currency: 'EUR' },
  color: null,
  numberLast4: '2861',
  archived: false,
  excludedFromStats: false,
};

describe('AccountCard', () => {
  let fixture: ComponentFixture<AccountCard>;
  let emesso: string[];
  let colori: string[];

  const campo = (): HTMLInputElement =>
    fixture.nativeElement.querySelector('input[type="text"]') as HTMLInputElement;

  /**
   * Digitare e lasciare che Angular ridisegni.
   *
   * Senza l'attesa il binding `[value]` non ha ancora visto il valore digitato,
   * e un successivo ritorno al nome di prima non riscriverebbe il campo: per
   * Angular non sarebbe un cambiamento. Nel browser non succede — ogni `set` su
   * un signal programma un giro di rilevamento — ma nel test va reso esplicito.
   */
  const scrivi = async (valore: string) => {
    campo().value = valore;
    campo().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    emesso = [];
    colori = [];
    fixture = TestBed.createComponent(AccountCard);
    fixture.componentRef.setInput('account', CONTO);
    fixture.componentInstance.renamedTo.subscribe((name) => emesso.push(name));
    fixture.componentInstance.recoloredTo.subscribe((color) => colori.push(color));
    await fixture.whenStable();
  });

  it('mostra il nome del conto nel campo', () => {
    expect(campo().value).toBe('Credem');
  });

  it('conferma la rinomina uscendo dal campo', async () => {
    await scrivi('Conto stipendio');
    campo().dispatchEvent(new Event('blur'));

    expect(emesso).toEqual(['Conto stipendio']);
  });

  it('non manda niente se il nome non è cambiato', async () => {
    await scrivi('Credem');
    campo().dispatchEvent(new Event('blur'));

    // Una scrittura a vuoto muoverebbe `updated_at` per niente, e farebbe
    // comparire un salvataggio che non è avvenuto.
    expect(emesso).toEqual([]);
  });

  it('rifiuta un nome vuoto e rimette quello di prima', async () => {
    await scrivi('   ');
    campo().dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    // Un conto senza nome non esiste: il server lo rifiuterebbe comunque, e qui
    // l'utente si ritroverebbe con un campo vuoto senza sapere cosa c'era.
    expect(emesso).toEqual([]);
    expect(campo().value).toBe('Credem');
  });

  it('con Esc torna al nome di prima senza mandare niente', async () => {
    await scrivi('Un errore');
    campo().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();

    expect(emesso).toEqual([]);
    expect(campo().value).toBe('Credem');
  });

  it('mostra il nome della sorgente solo quando è stato rinominato', async () => {
    expect(fixture.nativeElement.textContent).not.toContain('Nella sorgente');

    fixture.componentRef.setInput('account', { ...CONTO, name: 'Conto stipendio' });
    await fixture.whenStable();

    // È l'unico modo di ritrovare quel conto nell'applicazione da cui proviene.
    expect(fixture.nativeElement.textContent).toContain('Nella sorgente: Credem');
    expect(campo().value).toBe('Conto stipendio');
  });

  it('la tavolozza è un popover ancorato al pallino, non una sezione che sposta la carta', () => {
    const pallino = fixture.nativeElement.querySelector(
      `[aria-label="Scegli un colore per Credem"]`,
    ) as HTMLButtonElement;
    const tavolozza = fixture.nativeElement.querySelector('[role="group"]') as HTMLElement;

    // Il legame fra i due è dichiarativo: è il browser ad aprirla e a chiuderla,
    // e il pannello sta nel top layer, quindi non occupa spazio nella carta.
    expect(tavolozza.getAttribute('popover')).toBe('auto');
    expect(pallino.getAttribute('popovertarget')).toBe(tavolozza.id);
    expect(tavolozza.id).not.toBe('');
  });

  it('scegliendo un colore lo emette e chiede la chiusura del popover', () => {
    const verde = fixture.nativeElement.querySelector('[aria-label="Verde"]') as HTMLButtonElement;

    verde.click();

    expect(colori).toEqual(['#16a34a']);
    expect(verde.getAttribute('popovertargetaction')).toBe('hide');
  });

  it('non manda niente se il colore è già quello', () => {
    fixture.componentRef.setInput('account', { ...CONTO, color: '#16a34a' });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[aria-label="Verde"]') as HTMLButtonElement).click();

    expect(colori).toEqual([]);
  });
});
