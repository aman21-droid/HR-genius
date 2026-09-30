import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

/**
 * Feature routes are lazy-loaded. Phase 1 ships login + shell + a dashboard placeholder;
 * later phases add employee, leave, payroll, etc. as lazy children under the shell.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
