/**
 * Identificatori tipizzati (branded types).
 *
 * Impediscono a compile-time di passare per errore l'id di un conto dove è
 * atteso l'id di una categoria, senza costo a runtime (restano stringhe).
 */
declare const idBrand: unique symbol;

export type Identifier<TBrand extends string> = string & { readonly [idBrand]: TBrand };

export type AccountId = Identifier<'Account'>;
export type CategoryId = Identifier<'Category'>;
export type SourceCategoryId = Identifier<'SourceCategory'>;
export type RecordId = Identifier<'FinanceRecord'>;

export const asAccountId = (raw: string): AccountId => raw as AccountId;
export const asCategoryId = (raw: string): CategoryId => raw as CategoryId;
export const asSourceCategoryId = (raw: string): SourceCategoryId => raw as SourceCategoryId;
export const asRecordId = (raw: string): RecordId => raw as RecordId;
