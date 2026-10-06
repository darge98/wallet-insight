import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { SUBSCRIPTION_REPOSITORY } from '@wallet/subscriptions-domain';

import { HttpSubscriptionRepository } from './http-subscription.repository';

export function provideSubscriptionsHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: SUBSCRIPTION_REPOSITORY, useClass: HttpSubscriptionRepository },
  ]);
}
