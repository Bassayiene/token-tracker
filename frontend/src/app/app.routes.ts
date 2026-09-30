import { Routes } from '@angular/router';

import { TokenList } from './pages/token-list/token-list';

export const routes: Routes = [
  { path: '', component: TokenList, title: 'Token tracker' },
  {
    path: 'tokens/:assetId',
    // Lazy, so Chart.js is only downloaded when a token is opened
    loadComponent: () => import('./pages/token-detail/token-detail').then((m) => m.TokenDetail),
    title: 'Token · Token tracker',
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/auth/auth-page').then((m) => m.AuthPage),
    data: { mode: 'login' },
    title: 'Log in · Token tracker',
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/auth/auth-page').then((m) => m.AuthPage),
    data: { mode: 'register' },
    title: 'Sign up · Token tracker',
  },
  { path: '**', redirectTo: '' },
];
