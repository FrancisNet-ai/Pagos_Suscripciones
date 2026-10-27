import { Routes } from '@angular/router';
import {
  authGuard,
  guestGuard,
  ownerGuard,
  ownerMatch,
  permissionGuard,
  roleGuard,
} from './core/guards/auth.guards';

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
    path: 'invitacion/:token',
    title: 'Únete al equipo',
    loadComponent: () => import('./features/staff/accept-invitation.page').then((m) => m.AcceptInvitationPage),
  },
  {
    path: 'panel',
    canActivate: [authGuard, roleGuard('BUSINESS_OWNER', 'BUSINESS_STAFF')],
    loadComponent: () => import('./features/dashboard/dashboard-shell.component').then((m) => m.DashboardShellComponent),
    children: [
      {
        path: '',
        title: 'Inicio',
        canMatch: [ownerMatch],
        loadComponent: () => import('./features/dashboard/dashboard-home.page').then((m) => m.DashboardHomePage),
      },
      {
        path: '',
        title: 'Inicio',
        loadComponent: () => import('./features/dashboard/staff-home.page').then((m) => m.StaffHomePage),
      },
      {
        path: 'productos',
        canActivate: [permissionGuard('PRODUCTS')],
        title: 'Productos',
        loadComponent: () => import('./features/products/products-list.page').then((m) => m.ProductsListPage),
      },
      {
        path: 'productos/nuevo',
        canActivate: [permissionGuard('PRODUCTS')],
        title: 'Nuevo producto',
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'productos/:id',
        canActivate: [permissionGuard('PRODUCTS')],
        title: 'Producto',
        loadComponent: () => import('./features/products/product-form.page').then((m) => m.ProductFormPage),
      },
      {
        path: 'ventas',
        canActivate: [permissionGuard('ORDERS')],
        title: 'Pedidos',
        loadComponent: () => import('./features/orders/orders-list.page').then((m) => m.OrdersListPage),
      },
      {
        path: 'ventas/:id',
        canActivate: [permissionGuard('ORDERS')],
        title: 'Pedido',
        loadComponent: () => import('./features/orders/order-detail.page').then((m) => m.OrderDetailPage),
      },
      {
        path: 'reportes',
        canActivate: [permissionGuard('REPORTS')],
        title: 'Reportes',
        loadComponent: () => import('./features/reports/reports.page').then((m) => m.ReportsPage),
      },
      {
        path: 'clientes',
        canActivate: [permissionGuard('CUSTOMERS')],
        title: 'Clientes',
        loadComponent: () => import('./features/customers/customers-list.page').then((m) => m.CustomersListPage),
      },
      {
        path: 'clientes/nuevo',
        canActivate: [permissionGuard('CUSTOMERS')],
        title: 'Registrar cliente',
        loadComponent: () => import('./features/customers/customer-new.page').then((m) => m.CustomerNewPage),
      },
      {
        path: 'clientes/importar',
        canActivate: [permissionGuard('CUSTOMERS')],
        title: 'Importar clientes',
        loadComponent: () => import('./features/imports/customer-import.page').then((m) => m.CustomerImportPage),
      },
      {
        path: 'clientes/:id',
        canActivate: [permissionGuard('CUSTOMERS')],
        title: 'Cliente',
        loadComponent: () => import('./features/customers/customer-detail.page').then((m) => m.CustomerDetailPage),
      },
      {
        path: 'renovaciones',
        canActivate: [permissionGuard('RENEWALS')],
        title: 'Renovaciones',
        loadComponent: () => import('./features/renewals/renewals.page').then((m) => m.RenewalsPage),
      },
      {
        path: 'suscripciones',
        canActivate: [permissionGuard('CUSTOMERS')],
        title: 'Suscripciones',
        loadComponent: () => import('./features/subscriptions/subscriptions.page').then((m) => m.SubscriptionsPage),
      },
      {
        path: 'plan',
        canActivate: [ownerGuard],
        title: 'Mi plan',
        loadComponent: () => import('./features/billing/billing.page').then((m) => m.BillingPage),
      },
      {
        path: 'configuracion',
        canActivate: [ownerGuard],
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
            path: 'equipo',
            title: 'Equipo',
            loadComponent: () => import('./features/staff/team.page').then((m) => m.TeamPage),
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
