import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { ACCOUNT_REPOSITORY } from '@wallet/accounts-domain';

import { HttpAccountRepository } from './http-account.repository';

export function provideAccountsHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: ACCOUNT_REPOSITORY, useClass: HttpAccountRepository },
  ]);
}
