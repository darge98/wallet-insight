import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import {
  asCategoryId,
  asSourceCategoryId,
  CategoryRejectedError,
  CategoryRepository,
  CATEGORY_REPOSITORY,
} from '@wallet/shared-domain';

import { CategoriesFacade } from './categories-facade';

const SPESA = asCategoryId('cat-spesa');
const CIBO_BB = asSourceCategoryId('bb-cibo');

function facade(relink: CategoryRepository['relink']): CategoriesFacade {
  const repository: Pick<CategoryRepository, 'findAll' | 'findSources' | 'relink'> = {
    findAll: async () => [],
    findSources: async () => [],
    relink,
  };
  TestBed.configureTestingModule({
    providers: [CategoriesFacade, { provide: CATEGORY_REPOSITORY, useValue: repository }],
  });
  return TestBed.inject(CategoriesFacade);
}

describe('CategoriesFacade', () => {
  it('un aggancio rifiutato dice il motivo del server', async () => {
    const categorie = facade(async () => {
      throw new CategoryRejectedError('«Cibo» è una macro: scegli una sottocategoria.');
    });

    await categorie.relink(CIBO_BB, SPESA);

    expect(categorie.failure()).toBe('«Cibo» è una macro: scegli una sottocategoria.');
    expect(categorie.busy()).toBe(false);
  });

  it('un errore qualsiasi diventa un messaggio generico, che si può chiudere', async () => {
    const categorie = facade(async () => {
      throw new Error('rete');
    });

    await categorie.relink(CIBO_BB, SPESA);
    expect(categorie.failure()).toBe('Non è stato possibile salvare. Riprova tra poco.');

    categorie.dismissFailure();
    expect(categorie.failure()).toBeNull();
  });
});
