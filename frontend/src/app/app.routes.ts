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
    path: 'tienda/:slug',
    loadComponent: () => import('./features/storefront/storefront.page').then((m) => m.StorefrontPage),
  },
  {
    path: 'tienda/:slug/comprar/:productId',
    title: 'Completar pedido',
    loadComponent: () => import('./features/checkout/checkout.page').then((m) => m.CheckoutPage),
  },
  {
    path: 'pedido/:token',
    title: 'Tu pedido',
    loadComponent: () => import('./features/order-status/order-status.page').then((m) => m.OrderStatusPage),
  },
  {
    path: 'renovar/:token',
    title: 'Renovar suscripción',
    loadComponent: () => import('./features/renewals/renewal-public.page').then((m) => m.RenewalPublicPage),
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
      {
        path: 'productos',
        title: 'Productos · Panel',
        loadComponent: () => import('./features/products/products-list.page').then((m) => m.ProductsListPage),
      },
      {
        path: 'productos/nuevo',
        title: 'Nuevo producto · Panel',
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'productos/:id',
        title: 'Producto · Panel',
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'ventas',
        title: 'Pedidos · Panel',
        loadComponent: () => import('./features/orders/orders-list.page').then((m) => m.OrdersListPage),
      },
      {
        path: 'ventas/:id',
        title: 'Pedido · Panel',
        loadComponent: () => import('./features/orders/order-detail.page').then((m) => m.OrderDetailPage),
      },
      {
        path: 'reportes',
        title: 'Reportes · Panel',
        loadComponent: () => import('./features/reports/reports.page').then((m) => m.ReportsPage),
      },
      {
        path: 'clientes',
        title: 'Clientes · Panel',
        loadComponent: () => import('./features/customers/customers-list.page').then((m) => m.CustomersListPage),
      },
      {
        path: 'clientes/nuevo',
        title: 'Registrar cliente · Panel',
        loadComponent: () => import('./features/customers/customer-new.page').then((m) => m.CustomerNewPage),
      },
      {
        path: 'clientes/importar',
        title: 'Importar clientes · Panel',
        loadComponent: () => import('./features/imports/customer-import.page').then((m) => m.CustomerImportPage),
      },
      {
        path: 'clientes/:id',
        title: 'Cliente · Panel',
        loadComponent: () => import('./features/customers/customer-detail.page').then((m) => m.CustomerDetailPage),
      },
      {
        path: 'renovaciones',
        title: 'Renovaciones · Panel',
        loadComponent: () => import('./features/renewals/renewals.page').then((m) => m.RenewalsPage),
      },
      {
        path: 'suscripciones',
        title: 'Suscripciones · Panel',
        loadComponent: () => import('./features/subscriptions/subscriptions.page').then((m) => m.SubscriptionsPage),
      },
      {
        path: 'plan',
        title: 'Mi plan · Panel',
        loadComponent: () => import('./features/billing/billing.page').then((m) => m.BillingPage),
      },
      {
        path: 'configuracion',
        loadComponent: () => import('./features/settings/settings-layout.component').then((m) => m.SettingsLayoutComponent),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'negocio' },
          {
            path: 'negocio',
            title: 'Configuración · Panel',
            loadComponent: () => import('./features/settings/business-settings.page').then((m) => m.BusinessSettingsPage),
          },
          {
            path: 'enlace',
            title: 'Enlace público · Panel',
            loadComponent: () => import('./features/settings/store-link.page').then((m) => m.StoreLinkPage),
          },
          {
            path: 'renovaciones',
            title: 'Avisos de renovación · Panel',
            loadComponent: () => import('./features/settings/renewal-settings.page').then((m) => m.RenewalSettingsPage),
          },
          {
            path: 'whatsapp',
            title: 'WhatsApp Business · Panel',
            loadComponent: () => import('./features/whatsapp/whatsapp-settings.page').then((m) => m.WhatsAppSettingsPage),
          },
          {
            path: 'cobros',
            title: 'Yape y Plin · Panel',
            loadComponent: () => import('./features/settings/payment-methods.page').then((m) => m.PaymentMethodsPage),
          },
        ],
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard('SUPER_ADMIN')],
    loadComponent: () => import('./features/admin/admin-shell.component').then((m) => m.AdminShellComponent),
    children: [
      {
        path: '',
        title: 'Resumen · Superadministración',
        loadComponent: () => import('./features/admin/admin-overview.page').then((m) => m.AdminOverviewPage),
      },
      {
        path: 'pagos',
        title: 'Pagos SaaS · Superadministración',
        loadComponent: () => import('./features/admin/admin-payments.page').then((m) => m.AdminPaymentsPage),
      },
      {
        path: 'negocios',
        title: 'Negocios · Superadministración',
        loadComponent: () => import('./features/admin/admin-businesses.page').then((m) => m.AdminBusinessesPage),
      },
      {
        path: 'negocios/:id',
        title: 'Negocio · Superadministración',
        loadComponent: () => import('./features/admin/admin-business-detail.page').then((m) => m.AdminBusinessDetailPage),
      },
      {
        path: 'planes',
        title: 'Planes · Superadministración',
        loadComponent: () => import('./features/admin/admin-plans.page').then((m) => m.AdminPlansPage),
      },
      {
        path: 'configuracion',
        title: 'Configuración · Superadministración',
        loadComponent: () => import('./features/admin/admin-settings.page').then((m) => m.AdminSettingsPage),
      },
      {
        path: 'auditoria',
        title: 'Auditoría · Superadministración',
        loadComponent: () => import('./features/admin/admin-audit.page').then((m) => m.AdminAuditPage),
      },
    ],
  },
  {
    path: '**',
    title: 'Página no encontrada',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
