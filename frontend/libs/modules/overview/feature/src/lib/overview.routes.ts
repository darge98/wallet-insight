import { Routes } from '@angular/router';

export const overviewRoutes: Routes = [
  {
    path: '',
    title: 'Panoramica · Wallet Insights',
    loadComponent: () => import('./pages/overview-page').then((m) => m.OverviewPage),
  },
];
