import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withHashLocation,
  withInMemoryScrolling,
} from '@angular/router';
import { provideEchartsCore } from 'ngx-echarts';

import { provideAccountsHttp } from '@wallet/accounts-data-access';
import { provideBudgetsHttp } from '@wallet/budgets-data-access';
import { provideIngestionHttp } from '@wallet/ingestion-data-access';
import { provideOnboardingHttp } from '@wallet/onboarding-data-access';
import { provideSharedHttp } from '@wallet/shared-data-access';
import { provideSubscriptionsHttp } from '@wallet/subscriptions-data-access';
import { provideUserHttp } from '@wallet/user-data-access';

import { appRoutes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      appRoutes,
      withComponentInputBinding(),
      // Routing con hash: l'app resta apribile da qualsiasi hosting statico
      // (e da file://) senza configurare rewrite lato server.
      withHashLocation(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),
    // ECharts è caricato on demand dall'entry point secondario della libreria UI:
    // resta fuori dal bundle iniziale.
    provideEchartsCore({
      echarts: () => import('@wallet/shared-ui/echarts').then((m) => m.echarts),
    }),

    provideHttpClient(withFetch()),

    // Nessun `API_BASE_URL`: vale il default relativo `/api`, sempre sulla stessa
    // origine della pagina. Nello stack lo inoltra nginx, con `npm start` il proxy
    // del dev server (`apps/wallet/proxy.conf.json`) — verso :8080, che sia il
    // backend del compose o un `bootRun`. Niente CORS in nessuno dei due casi.

    // Ogni modulo collega il proprio adapter alle proprie porte.
    provideSharedHttp(),
    provideUserHttp(),
    provideOnboardingHttp(),
    provideIngestionHttp(),
    provideAccountsHttp(),
    provideBudgetsHttp(),
    provideSubscriptionsHttp(),
  ],
};
