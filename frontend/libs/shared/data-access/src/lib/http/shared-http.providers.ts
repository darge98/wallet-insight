import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import {
  ANALYTICS_REPOSITORY,
  CATEGORY_REPOSITORY,
  RECORD_REPOSITORY,
  SPENDING_BREAKDOWN_REPOSITORY,
} from '@wallet/shared-domain';

import { HttpAnalyticsRepository } from './http-analytics.repository';
import { HttpCategoryRepository } from './http-category.repository';
import { HttpRecordRepository } from './http-record.repository';
import { HttpSpendingBreakdownRepository } from './http-spending-breakdown.repository';

/**
 * Gli adapter HTTP delle porte che stanno in `shared-domain`: movimenti,
 * categorie, classifiche di periodo e KPI. Sono qui e non in un modulo perché
 * le leggono sia la Panoramica sia i Movimenti.
 */
export function provideSharedHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: RECORD_REPOSITORY, useClass: HttpRecordRepository },
    { provide: CATEGORY_REPOSITORY, useClass: HttpCategoryRepository },
    { provide: SPENDING_BREAKDOWN_REPOSITORY, useClass: HttpSpendingBreakdownRepository },
    { provide: ANALYTICS_REPOSITORY, useClass: HttpAnalyticsRepository },
  ]);
}
