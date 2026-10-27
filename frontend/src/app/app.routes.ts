import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Vigencia — Suscripciones digitales en orden',
    loadComponent: () => import('./features/landing/landing.page').then((m) => m.LandingPage),
  },
  {
    path: 'ingresar',
    title: 'Iniciar sesión',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'registro',
    title: 'Crear cuenta',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'recuperar',
    title: 'Recuperar contraseña',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/forgot-password.page').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'restablecer',
    title: 'Nueva contraseña',
    loadComponent: () => import('./features/auth/reset-password.page').then((m) => m.ResetPasswordPage),
  },
  {
    path: 'legal/:documento',
    title: 'Información legal',
    loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
  },
  {
    path: 'panel',
    canActivate: [authGuard, roleGuard('BUSINESS_OWNER')],
    loadComponent: () => import('./features/dashboard/dashboard-shell.component').then((m) => m.DashboardShellComponent),
    children: [
      {
        path: '',
        title: 'Inicio · Panel',
        loadComponent: () => import('./features/dashboard/dashboard-home.page').then((m) => m.DashboardHomePage),
      },
    ],
  },
  {
    path: 'admin',
    title: 'Superadministración',
    canActivate: [authGuard, roleGuard('SUPER_ADMIN')],
    loadComponent: () => import('./features/super-admin/super-admin-home.page').then((m) => m.SuperAdminHomePage),
  },
  {
    path: '**',
    title: 'Página no encontrada',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
