import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
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
    data: { storefront: true },
    loadComponent: () => import('./features/storefront/storefront.page').then((m) => m.StorefrontPage),
  },
  {
    path: 'tienda/:slug/comprar/:productId',
    data: { storefront: true },
    title: 'Completar pedido',
    loadComponent: () => import('./features/checkout/checkout.page').then((m) => m.CheckoutPage),
  },
  {
    path: 'pedido/:token',
    data: { storefront: true },
    title: 'Tu pedido',
    loadComponent: () => import('./features/order-status/order-status.page').then((m) => m.OrderStatusPage),
  },
  {
    path: 'renovar/:token',
    data: { storefront: true },
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
        title: 'Inicio',
        loadComponent: () => import('./features/dashboard/dashboard-home.page').then((m) => m.DashboardHomePage),
      },
      {
        path: 'productos',
        title: 'Productos',
        loadComponent: () => import('./features/products/products-list.page').then((m) => m.ProductsListPage),
      },
      {
        path: 'productos/nuevo',
        title: 'Nuevo producto',
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'productos/:id',
        title: 'Producto',
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'ventas',
        title: 'Pedidos',
        loadComponent: () => import('./features/orders/orders-list.page').then((m) => m.OrdersListPage),
      },
      {
        path: 'ventas/:id',
        title: 'Pedido',
        loadComponent: () => import('./features/orders/order-detail.page').then((m) => m.OrderDetailPage),
      },
      {
        path: 'reportes',
        title: 'Reportes',
        loadComponent: () => import('./features/reports/reports.page').then((m) => m.ReportsPage),
      },
      {
        path: 'clientes',
        title: 'Clientes',
        loadComponent: () => import('./features/customers/customers-list.page').then((m) => m.CustomersListPage),
      },
      {
        path: 'clientes/nuevo',
        title: 'Registrar cliente',
        loadComponent: () => import('./features/customers/customer-new.page').then((m) => m.CustomerNewPage),
      },
      {
        path: 'clientes/importar',
        title: 'Importar clientes',
        loadComponent: () => import('./features/imports/customer-import.page').then((m) => m.CustomerImportPage),
      },
      {
        path: 'clientes/:id',
        title: 'Cliente',
        loadComponent: () => import('./features/customers/customer-detail.page').then((m) => m.CustomerDetailPage),
      },
      {
        path: 'renovaciones',
        title: 'Renovaciones',
        loadComponent: () => import('./features/renewals/renewals.page').then((m) => m.RenewalsPage),
      },
      {
        path: 'suscripciones',
        title: 'Suscripciones',
        loadComponent: () => import('./features/subscriptions/subscriptions.page').then((m) => m.SubscriptionsPage),
      },
      {
        path: 'plan',
        title: 'Mi plan',
        loadComponent: () => import('./features/billing/billing.page').then((m) => m.BillingPage),
      },
      {
        path: 'configuracion',
        loadComponent: () => import('./features/settings/settings-layout.component').then((m) => m.SettingsLayoutComponent),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'negocio' },
          {
            path: 'negocio',
            title: 'Configuración',
            loadComponent: () => import('./features/settings/business-settings.page').then((m) => m.BusinessSettingsPage),
          },
          {
            path: 'enlace',
            title: 'Enlace público',
            loadComponent: () => import('./features/settings/store-link.page').then((m) => m.StoreLinkPage),
          },
          {
            path: 'renovaciones',
            title: 'Avisos de renovación',
            loadComponent: () => import('./features/settings/renewal-settings.page').then((m) => m.RenewalSettingsPage),
          },
          {
            path: 'whatsapp',
            title: 'WhatsApp Business',
            loadComponent: () => import('./features/whatsapp/whatsapp-settings.page').then((m) => m.WhatsAppSettingsPage),
          },
          {
            path: 'cobros',
            title: 'Yape y Plin',
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
        title: 'Resumen',
        loadComponent: () => import('./features/admin/admin-overview.page').then((m) => m.AdminOverviewPage),
      },
      {
        path: 'pagos',
        title: 'Pagos SaaS',
        loadComponent: () => import('./features/admin/admin-payments.page').then((m) => m.AdminPaymentsPage),
      },
      {
        path: 'negocios',
        title: 'Negocios',
        loadComponent: () => import('./features/admin/admin-businesses.page').then((m) => m.AdminBusinessesPage),
      },
      {
        path: 'negocios/:id',
        title: 'Negocio',
        loadComponent: () => import('./features/admin/admin-business-detail.page').then((m) => m.AdminBusinessDetailPage),
      },
      {
        path: 'planes',
        title: 'Planes',
        loadComponent: () => import('./features/admin/admin-plans.page').then((m) => m.AdminPlansPage),
      },
      {
        path: 'configuracion',
        title: 'Configuración',
        loadComponent: () => import('./features/admin/admin-settings.page').then((m) => m.AdminSettingsPage),
      },
      {
        path: 'auditoria',
        title: 'Auditoría',
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
