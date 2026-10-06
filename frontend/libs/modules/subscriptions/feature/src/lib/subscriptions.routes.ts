import { Routes } from '@angular/router';

export const subscriptionsRoutes: Routes = [
  {
    path: '',
    title: 'Abbonamenti · Wallet Insights',
    loadComponent: () => import('./pages/subscriptions-page').then((m) => m.SubscriptionsPage),
  },
];
