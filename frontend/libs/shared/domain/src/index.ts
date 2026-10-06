// Value object e tipi trasversali
export * from './lib/shared/currency';
export * from './lib/shared/money';
export * from './lib/shared/date-range';
export * from './lib/shared/identifier';
export * from './lib/shared/page';
export * from './lib/shared/year-month';

// Entità
export * from './lib/categories/category';
export * from './lib/records/finance-record';
export * from './lib/records/record-edit';
export * from './lib/records/record-query';

// Aggregati di sintesi
export * from './lib/analytics/kpi-summary';
export * from './lib/analytics/cumulative-expense';
export * from './lib/analytics/category-breakdown';
export * from './lib/analytics/spending-target';

// Porte: i contratti che gli adapter devono soddisfare
export * from './lib/ports/category-repository.port';
export * from './lib/ports/record-repository.port';
export * from './lib/ports/analytics-repository.port';
export * from './lib/ports/spending-breakdown-repository.port';
