import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { WWTRADING_ROLES } from './core/auth/auth.service';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'WWTRADING | Solutions d\u2019impression professionnelles',
    loadComponent: () => import('./features/home/home.component').then((module) => module.HomeComponent)
  },
  {
    path: 'contact',
    title: 'Contactez-nous | WWTRADING',
    loadComponent: () => import('./features/contact/contact.component').then((module) => module.ContactComponent)
  },
  {
    path: 'services/vente-machines',
    title: 'Vente de machines | WWTRADING',
    loadComponent: () => import('./features/services/vente-machines/vente-machines.component')
      .then((m) => m.VenteMachinesComponent)
  },
  {
    path: 'services/location',
    title: 'Location | WWTRADING',
    loadComponent: () => import('./features/services/location/location.component')
      .then((m) => m.LocationComponent)
  },
  {
    path: 'services/maintenance',
    title: 'Maintenance | WWTRADING',
    loadComponent: () => import('./features/services/maintenance/maintenance.component')
      .then((m) => m.MaintenanceComponent)
  },
  {
    path: 'services/toners-consommables',
    title: 'Toners & consommables | WWTRADING',
    loadComponent: () => import('./features/services/toners-consommables/toners-consommables.component')
      .then((m) => m.TonersConsommablesComponent)
  },
  {
    path: 'services/pieces-rechange',
    title: 'Pièces de rechange | WWTRADING',
    loadComponent: () => import('./features/services/pieces-rechange/pieces-rechange.component')
      .then((m) => m.PiecesRechangeComponent)
  },
  {
    path: 'services/support-technique',
    title: 'Support technique | WWTRADING',
    loadComponent: () => import('./features/services/support-technique/support-technique.component')
      .then((m) => m.SupportTechniqueComponent)
  },
  {
    path: 'devis/mes-demandes/:id',
    title: 'Détail de ma demande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/quotations/client-quotation-detail.component')
      .then((module) => module.ClientQuotationDetailComponent)
  },
  {
    path: 'devis/mes-demandes',
    title: 'Mes demandes de devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/quotations/client-quotation-history.component')
      .then((module) => module.ClientQuotationHistoryComponent)
  },
  {
    path: 'devis',
    title: 'Devis personnalisé | WWTRADING',
    loadComponent: () => import('./features/services/demande-devis/demande-devis.component')
      .then((m) => m.DemandeDevisComponent)
  },
  {
    path: 'login',
    title: 'Connexion | WWTRADING',
    loadComponent: () => import('./features/auth/login.component')
      .then((module) => module.LoginComponent)
  },
  {
    path: 'register',
    title: 'Création de compte | WWTRADING',
    loadComponent: () => import('./features/auth/register.component')
      .then((module) => module.RegisterComponent)
  },
  {
    path: 'forgot-password',
    title: 'Mot de passe oublié | WWTRADING',
    loadComponent: () => import('./features/auth/forgot-password.component')
      .then((module) => module.ForgotPasswordComponent)
  },
  {
    path: 'access-denied',
    title: 'Accès refusé | WWTRADING',
    loadComponent: () => import('./features/auth/access-denied.component')
      .then((module) => module.AccessDeniedComponent)
  },
  {
    path: 'admin/dashboard',
    title: 'Administration | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], role: 'ADMIN' },
    loadComponent: () => import('./features/admin/dashboard/dashboard.component')
      .then((module) => module.DashboardComponent)
  },
  {
    path: 'manager/dashboard',
    title: 'Management | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'], role: 'MANAGER' },
    loadComponent: () => import('./features/manager/dashboard/dashboard.component')
      .then((module) => module.DashboardComponent)
  },
  {
    path: 'admin/users',
    title: 'Gestion des utilisateurs | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/users/staff-user-management.component')
      .then((module) => module.StaffUserManagementComponent)
  },
  {
    path: 'manager/users',
    title: 'Gestion des utilisateurs | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/users/staff-user-management.component')
      .then((module) => module.StaffUserManagementComponent)
  },
  {
    path: 'admin/clients/:id',
    title: 'Détail du client | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/clients/client-detail.component')
      .then((module) => module.ClientDetailComponent)
  },
  {
    path: 'admin/clients',
    title: 'Annuaire des clients | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/clients/client-list.component')
      .then((module) => module.ClientListComponent)
  },
  {
    path: 'manager/clients/:id',
    title: 'Détail du client | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/clients/client-detail.component')
      .then((module) => module.ClientDetailComponent)
  },
  {
    path: 'manager/clients',
    title: 'Annuaire des clients | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/clients/client-list.component')
      .then((module) => module.ClientListComponent)
  },
  {
    path: 'commercial/clients/:id',
    title: 'Détail du client | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/clients/client-detail.component')
      .then((module) => module.ClientDetailComponent)
  },
  {
    path: 'commercial/clients',
    title: 'Annuaire des clients | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/clients/client-list.component')
      .then((module) => module.ClientListComponent)
  },
  {
    path: 'commercial/dashboard',
    title: 'Commercial | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'], role: 'COMMERCIAL' },
    loadComponent: () => import('./features/commercial/dashboard/dashboard.component')
      .then((module) => module.DashboardComponent)
  },
  // ── Prospects: commercial prospect portfolio ───────────────────────────────
  {
    path: 'commercial/prospects',
    title: 'Prospects | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'], role: 'COMMERCIAL' },
    loadComponent: () => import('./features/prospects/commercial-prospects.component')
      .then((module) => module.CommercialProspectsComponent)
  },
  {
    path: 'commercial/analytics',
    title: 'Analytics commercial | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'], role: 'COMMERCIAL' },
    loadComponent: () => import('./features/commercial/reports/commercial-reports.component')
      .then((module) => module.CommercialReportsComponent)
  },
  {
    path: 'commercial/reports',
    redirectTo: 'commercial/analytics',
    pathMatch: 'full'
  },
  {
    path: 'admin/analytics',
    title: 'Analytics | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], role: 'ADMIN' },
    loadComponent: () => import('./features/commercial/reports/commercial-reports.component')
      .then((module) => module.CommercialReportsComponent)
  },
  {
    path: 'manager/analytics',
    title: 'Analytics | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'], role: 'MANAGER' },
    loadComponent: () => import('./features/commercial/reports/commercial-reports.component')
      .then((module) => module.CommercialReportsComponent)
  },
  {
    path: 'client/analytics',
    title: 'Mon activité | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'], role: 'CLIENT' },
    loadComponent: () => import('./features/commercial/reports/commercial-reports.component')
      .then((module) => module.CommercialReportsComponent)
  },
  {
    path: 'technician/analytics',
    title: 'Mon activité | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'], role: 'TECHNICIAN' },
    loadComponent: () => import('./features/commercial/reports/commercial-reports.component')
      .then((module) => module.CommercialReportsComponent)
  },
  {
    path: 'manager/prospects',
    title: 'Prospects | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'], role: 'MANAGER' },
    loadComponent: () => import('./features/prospects/commercial-prospects.component')
      .then((module) => module.CommercialProspectsComponent)
  },
  {
    path: 'admin/prospects',
    title: 'Prospects | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], role: 'ADMIN' },
    loadComponent: () => import('./features/prospects/commercial-prospects.component')
      .then((module) => module.CommercialProspectsComponent)
  },
  {
    path: 'technician/dashboard',
    title: 'Technicien | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'], role: 'TECHNICIAN' },
    loadComponent: () => import('./features/technician/dashboard/dashboard.component')
      .then((module) => module.DashboardComponent)
  },
  {
    path: 'client/dashboard',
    title: 'Espace client | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'], role: 'CLIENT' },
    loadComponent: () => import('./features/client/dashboard/dashboard.component')
      .then((module) => module.DashboardComponent)
  },
  {
    path: 'client/machines',
    title: 'Mes machines | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/client/machines/client-machines.component')
      .then(({ ClientMachinesComponent }) => ClientMachinesComponent)
  },
  {
    path: 'client/documents',
    title: 'Mes documents | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/client/documents/client-documents.component')
      .then((module) => module.ClientDocumentsComponent)
  },
  {
    path: 'client/support',
    title: 'Support | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/support/support-workspace.component')
      .then((module) => module.SupportWorkspaceComponent)
  },
  {
    path: 'admin/support',
    title: 'Support clients | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/support/support-workspace.component')
      .then((module) => module.SupportWorkspaceComponent)
  },
  {
    path: 'manager/support',
    title: 'Support clients | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/support/support-workspace.component')
      .then((module) => module.SupportWorkspaceComponent)
  },
  {
    path: 'commercial/support',
    title: 'Support clients | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/support/support-workspace.component')
      .then((module) => module.SupportWorkspaceComponent)
  },
  {
    path: 'client/account',
    title: 'Mon compte | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'], title: 'Mon compte' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/account/account.component')
          .then((module) => module.AccountComponent)
      }
    ]
  },
  // ── Account: one role-scoped route per workspace, sharing AccountComponent ──
  {
    path: 'client/mon-compte',
    title: 'Mon compte | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'], title: 'Mon compte' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/account/account.component')
          .then((module) => module.AccountComponent)
      }
    ]
  },
  {
    path: 'admin/mon-compte',
    title: 'Mon compte | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], title: 'Mon compte' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/account/account.component')
          .then((module) => module.AccountComponent)
      }
    ]
  },
  {
    path: 'manager/mon-compte',
    title: 'Mon compte | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'], title: 'Mon compte' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/account/account.component')
          .then((module) => module.AccountComponent)
      }
    ]
  },
  {
    path: 'commercial/mon-compte',
    title: 'Mon compte | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'], title: 'Mon compte' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/account/account.component')
          .then((module) => module.AccountComponent)
      }
    ]
  },
  {
    path: 'technician/mon-compte',
    title: 'Mon compte | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'], title: 'Mon compte' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/account/account.component')
          .then((module) => module.AccountComponent)
      }
    ]
  },
  {
    path: 'notifications',
    title: 'Mes notifications | WWTRADING',
    canActivate: [authGuard],
    loadComponent: () => import('./features/notifications/notification-center.component')
      .then((m) => m.NotificationCenterComponent)
  },
  {
    path: 'wishlist',
    title: 'Ma liste de souhaits | WWTRADING',
    loadComponent: () => import('./features/wishlist/wishlist.component').then((module) => module.WishlistComponent)
  },
  {
    path: 'cart',
    title: 'Mon panier | WWTRADING',
    loadComponent: () => import('./features/cart/cart.component').then((module) => module.CartComponent)
  },
  {
    path: 'addresses',
    title: 'Mes adresses | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: WWTRADING_ROLES },
    loadComponent: () => import('./features/addresses/address.component').then((module) => module.AddressComponent)
  },
  {
    path: 'checkout',
    title: 'Finaliser ma commande | WWTRADING',
    loadComponent: () => import('./features/checkout/checkout.component').then((module) => module.CheckoutComponent)
  },
  {
    path: 'suivi-commande',
    title: 'Suivi de commande | WWTRADING',
    loadComponent: () => import('./features/orders/order-tracking.component').then((module) => module.OrderTrackingComponent)
  },
  {
    path: 'track-order',
    redirectTo: 'suivi-commande',
    pathMatch: 'full'
  },
  // ── Maintenance: Client workspace ──────────────────────────────────────────
  {
    path: 'client/maintenance/new',
    title: 'Nouvelle demande de maintenance | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/client/maintenance/client-maintenance-new.component')
      .then((m) => m.ClientMaintenanceNewComponent)
  },
  {
    path: 'client/maintenance/:id',
    title: 'Détail de la demande de maintenance | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/client/maintenance/client-maintenance-detail.component')
      .then((m) => m.ClientMaintenanceDetailComponent)
  },
  {
    path: 'client/maintenance',
    title: 'Mes interventions | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/client/maintenance/client-maintenance-list.component')
      .then((m) => m.ClientMaintenanceListComponent)
  },
  // ── Maintenance: Technician workspace ──────────────────────────────────────
  {
    path: 'technician/maintenance/:id',
    title: 'Détail de l\u2019intervention | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'] },
    loadComponent: () => import('./features/technician/maintenance/maintenance-detail.component')
      .then((m) => m.TechnicianMaintenanceDetailComponent)
  },
  {
    path: 'technician/maintenance',
    title: 'Mes interventions | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'] },
    loadComponent: () => import('./features/technician/maintenance/maintenance-list.component')
      .then((m) => m.TechnicianMaintenanceListComponent)
  },
  // ── Maintenance: Manager workspace ─────────────────────────────────────────
  {
    path: 'manager/maintenance/:id',
    title: 'Détail de la demande de maintenance | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER', 'ADMIN'] },
    loadComponent: () => import('./features/manager/maintenance/maintenance-detail.component')
      .then((m) => m.ManagerMaintenanceDetailComponent)
  },
  {
    path: 'manager/maintenance',
    title: 'Maintenance | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER', 'ADMIN'] },
    loadComponent: () => import('./features/manager/maintenance/maintenance-list.component')
      .then((m) => m.ManagerMaintenanceListComponent)
  },
  // ── Orders ─────────────────────────────────────────────────────────────────
  {
    path: 'admin/orders/:orderId',
    title: 'Détail de la commande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/orders/order-detail.component')
      .then((module) => module.OrderDetailComponent)
  },
  {
    path: 'manager/orders/:orderId',
    title: 'Détail de la commande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/orders/order-detail.component')
      .then((module) => module.OrderDetailComponent)
  },
  {
    path: 'commercial/orders/:orderId',
    title: 'Détail de la commande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/orders/order-detail.component')
      .then((module) => module.OrderDetailComponent)
  },
  {
    path: 'technician/orders/:orderId',
    title: 'Détail de la commande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'] },
    loadComponent: () => import('./features/orders/order-detail.component')
      .then((module) => module.OrderDetailComponent)
  },
  {
    path: 'client/orders/:orderId',
    title: 'Détail de la commande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/orders/order-detail.component')
      .then((module) => module.OrderDetailComponent)
  },
  {
    path: 'admin/orders',
    title: 'Commandes | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/orders/order-history.component')
      .then((module) => module.OrderHistoryComponent)
  },
  {
    path: 'manager/orders',
    title: 'Commandes | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/orders/order-history.component')
      .then((module) => module.OrderHistoryComponent)
  },
  {
    path: 'commercial/orders',
    title: 'Commandes | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/orders/order-history.component')
      .then((module) => module.OrderHistoryComponent)
  },
  {
    path: 'technician/orders',
    title: 'Commandes | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['TECHNICIAN'] },
    loadComponent: () => import('./features/orders/order-history.component')
      .then((module) => module.OrderHistoryComponent)
  },
  {
    path: 'client/orders',
    title: 'Mes commandes | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/orders/order-history.component')
      .then((module) => module.OrderHistoryComponent)
  },
  {
    path: 'orders/:orderId',
    title: 'Détail de la commande | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'COMMERCIAL', 'CLIENT'] },
    loadComponent: () => import('./features/orders/order-detail.component')
      .then((module) => module.OrderDetailComponent)
  },
  {
    path: 'orders',
    title: 'Mes commandes | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'COMMERCIAL', 'CLIENT'] },
    loadComponent: () => import('./features/orders/order-history.component')
      .then((module) => module.OrderHistoryComponent)
  },
  {
    path: 'admin/payment-banks',
    title: 'Gestion des banques | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/admin/payment-banks/admin-payment-banks.component')
      .then((module) => module.AdminPaymentBanksComponent)
  },
  {
    path: 'admin/settings',
    title: 'Paramètres de la plateforme | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/admin/settings/admin-settings.component')
      .then((module) => module.AdminSettingsComponent)
  },
  {
    path: 'admin/parametres',
    redirectTo: 'admin/settings',
    pathMatch: 'full'
  },
  // ── Stock management: rendered inside the shared dashboard shell ───────────
  {
    path: 'admin/stock',
    title: 'Gestion des stocks | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], title: 'Gestion des stocks' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        title: 'Gestion des stocks | WWTRADING',
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/stock/stock.component')
          .then((module) => module.StockComponent)
      }
    ]
  },
  {
    path: 'manager/stock',
    title: 'Gestion des stocks | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'], title: 'Gestion des stocks' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        title: 'Gestion des stocks | WWTRADING',
        data: { roles: ['MANAGER'] },
        loadComponent: () => import('./features/stock/stock.component')
          .then((module) => module.StockComponent)
      }
    ]
  },
  {
    path: 'commercial/stock',
    title: 'Stocks produits | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'], title: 'Stocks produits' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        title: 'Stocks produits | WWTRADING',
        data: { roles: ['COMMERCIAL'] },
        loadComponent: () => import('./features/stock/stock.component')
          .then((module) => module.StockComponent)
      }
    ]
  },
  // ── Product management: rendered inside the shared dashboard shell ─────────
  {
    path: 'admin/products',
    title: 'Gestion des produits | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'], title: 'Gestion des produits' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        title: 'Gestion des produits | WWTRADING',
        data: { roles: ['ADMIN'], mode: 'management', managementBaseRoute: '/admin/products' },
        loadComponent: () => import('./features/products/product-list.component')
          .then((module) => module.ProductListComponent)
      },
      {
        path: 'new',
        title: 'Nouveau produit | WWTRADING',
        data: { roles: ['ADMIN'], mode: 'create', managementBaseRoute: '/admin/products' },
        loadComponent: () => import('./features/products/product-form.component')
          .then((module) => module.ProductFormComponent)
      },
      {
        path: ':id/edit',
        title: 'Modifier le produit | WWTRADING',
        data: { roles: ['ADMIN'], mode: 'edit', managementBaseRoute: '/admin/products' },
        loadComponent: () => import('./features/products/product-form.component')
          .then((module) => module.ProductFormComponent)
      }
    ]
  },
  {
    path: 'manager/products',
    title: 'Gestion des produits | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'], title: 'Gestion des produits' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        title: 'Gestion des produits | WWTRADING',
        data: { roles: ['MANAGER'], mode: 'management', managementBaseRoute: '/manager/products' },
        loadComponent: () => import('./features/products/product-list.component')
          .then((module) => module.ProductListComponent)
      },
      {
        path: 'new',
        title: 'Nouveau produit | WWTRADING',
        data: { roles: ['MANAGER'], mode: 'create', managementBaseRoute: '/manager/products' },
        loadComponent: () => import('./features/products/product-form.component')
          .then((module) => module.ProductFormComponent)
      },
      {
        path: ':id/edit',
        title: 'Modifier le produit | WWTRADING',
        data: { roles: ['MANAGER'], mode: 'edit', managementBaseRoute: '/manager/products' },
        loadComponent: () => import('./features/products/product-form.component')
          .then((module) => module.ProductFormComponent)
      }
    ]
  },
  {
    path: 'commercial/products',
    title: 'Gestion des produits | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'], title: 'Gestion des produits' },
    loadComponent: () => import('./shared/components/dashboard-layout/dashboard-layout.component')
      .then((module) => module.DashboardLayoutComponent),
    children: [
      {
        path: '',
        title: 'Gestion des produits | WWTRADING',
        data: { roles: ['COMMERCIAL'], mode: 'management', managementBaseRoute: '/commercial/products' },
        loadComponent: () => import('./features/products/product-list.component')
          .then((module) => module.ProductListComponent)
      },
      {
        path: 'new',
        title: 'Nouveau produit | WWTRADING',
        data: { roles: ['COMMERCIAL'], mode: 'create', managementBaseRoute: '/commercial/products' },
        loadComponent: () => import('./features/products/product-form.component')
          .then((module) => module.ProductFormComponent)
      },
      {
        path: ':id/edit',
        title: 'Modifier le produit | WWTRADING',
        data: { roles: ['COMMERCIAL'], mode: 'edit', managementBaseRoute: '/commercial/products' },
        loadComponent: () => import('./features/products/product-form.component')
          .then((module) => module.ProductFormComponent)
      }
    ]
  },
  {
    path: 'products/:id',
    title: 'Détail du produit | WWTRADING',
    loadComponent: () => import('./features/products/product-detail.component')
      .then((module) => module.ProductDetailComponent)
  },
  {
    path: 'products',
    title: 'Produits | WWTRADING',
    loadComponent: () => import('./features/products/product-list.component')
      .then((module) => module.ProductListComponent)
  },
  {
    path: 'devis/nouveau',
    title: 'Nouveau devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'COMMERCIAL'] },
    loadComponent: () => import('./features/quotations/quotation-create.component')
      .then((module) => module.QuotationCreateComponent)
  },
  {
    path: 'devis/:id/modifier',
    title: 'Modifier le devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'COMMERCIAL'] },
    loadComponent: () => import('./features/quotations/quotation-edit.component')
      .then((module) => module.QuotationEditComponent)
  },
  {
    path: 'devis/:id',
    title: 'Détail du devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'COMMERCIAL'] },
    loadComponent: () => import('./features/quotations/quotation-detail.component')
      .then((module) => module.QuotationDetailComponent)
  },
  {
    path: 'admin/devis/:id',
    title: 'Détail du devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/quotations/quotation-detail.component')
      .then((module) => module.QuotationDetailComponent)
  },
  {
    path: 'manager/devis/:id',
    title: 'Détail du devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/quotations/quotation-detail.component')
      .then((module) => module.QuotationDetailComponent)
  },
  {
    path: 'commercial/devis/:id',
    title: 'Détail du devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/quotations/quotation-detail.component')
      .then((module) => module.QuotationDetailComponent)
  },
  {
    path: 'admin/devis',
    title: 'Gestion des devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import('./features/quotations/quotation-list.component')
      .then((module) => module.QuotationListComponent)
  },
  {
    path: 'manager/devis',
    title: 'Gestion des devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadComponent: () => import('./features/quotations/quotation-list.component')
      .then((module) => module.QuotationListComponent)
  },
  {
    path: 'commercial/devis',
    title: 'Gestion des devis | WWTRADING',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMMERCIAL'] },
    loadComponent: () => import('./features/quotations/quotation-list.component')
      .then((module) => module.QuotationListComponent)
  },
  { path: 'stock', redirectTo: '/admin/stock', pathMatch: 'full' },
  {
    path: '**',
    title: 'Page introuvable | WWTRADING',
    loadComponent: () => import('./features/not-found/not-found.component')
      .then((module) => module.NotFoundComponent)
  }
];
