import { Routes } from '@angular/router';

export const budgetsRoutes: Routes = [
  {
    path: '',
    title: 'Budget · Wallet Insights',
    loadComponent: () => import('./pages/budgets-page').then((m) => m.BudgetsPage),
  },
];
