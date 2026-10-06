import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { BUDGET_REPOSITORY } from '@wallet/budgets-domain';

import { HttpBudgetRepository } from './http-budget.repository';

export function provideBudgetsHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: BUDGET_REPOSITORY, useClass: HttpBudgetRepository }]);
}
