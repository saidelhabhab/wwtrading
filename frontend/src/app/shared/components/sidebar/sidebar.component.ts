import { ChangeDetectionStrategy, Component, EventEmitter, Output, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, WwtradingRole } from '../../../core/auth/auth.service';

export interface DashboardNavItem {
  readonly label: string;
  readonly description: string;
  readonly icon: string;
  readonly route: string | null;
  readonly available: boolean;
}

type NavDefinition = readonly [string, string, string, string | null, boolean];

const NAVIGATION: Record<WwtradingRole, readonly NavDefinition[]> = {
  ADMIN: [
    ['Dashboard', 'Vue d\u2019ensemble', 'dashboard', '', true],
    ['Banques', 'Banques de virement', 'account_balance', '/admin/payment-banks', true],
    ['Utilisateurs', 'Comptes et rôles', 'group', '/admin/users', true],
    ['Clients', 'Clients professionnels', 'business', '/admin/clients', true],
    ['Prospects', 'Prospection commerciale', 'person_search', '/admin/prospects', true],
    ['Produits', 'Catalogue et pièces', 'inventory_2', '/admin/products', true],
    ['Stock', 'Disponibilités et mouvements', 'warehouse', '/admin/stock', true],
    ['Commandes', 'Commandes clients', 'shopping_cart', '/admin/orders', true],
    ['Devis', 'Demandes de devis', 'description', '/admin/devis', true],
    ['Maintenance', 'Suivi des interventions', 'engineering', '/manager/maintenance', true],
    ['Support', 'Conversations clients', 'support_agent', '/admin/support', true],
    ['Analytics', 'Vue globale de l’activité', 'bar_chart', '/admin/analytics', true],
    ['Notifications', 'Alertes et événements', 'notifications', '/notifications', true],
    ['Paramètres', 'Configuration plateforme', 'settings', '/admin/settings', true],
    ['Mon compte', 'Profil administrateur', 'account_circle', '/admin/mon-compte', true]
  ],
  MANAGER: [
    ['Dashboard', 'Vue d\u2019ensemble', 'dashboard', '', true],
    ['Utilisateurs', 'Comptes et rôles', 'group', '/manager/users', true],
    ['Clients', 'Clients professionnels', 'business', '/manager/clients', true],
    ['Prospects', 'Prospection commerciale', 'person_search', '/manager/prospects', true],
    ['Produits', 'Catalogue et pièces', 'inventory_2', '/manager/products', true],
    ['Stock', 'Disponibilités et mouvements', 'warehouse', '/manager/stock', true],
    ['Devis', 'Demandes de devis', 'description', '/manager/devis', true],
    ['Commandes', 'Commandes clients', 'shopping_cart', '/manager/orders', true],
    ['Maintenance', 'Demandes et interventions', 'engineering', '/manager/maintenance', true],
    ['Support', 'Conversations clients', 'support_agent', '/manager/support', true],
    ['Machines', 'Parc équipements', 'print', null, false],
    ['Analytics', 'Pilotage de l’activité', 'bar_chart', '/manager/analytics', true],
    ['Notifications', 'Alertes et événements', 'notifications', '/notifications', true],
    ['Mon compte', 'Profil manager', 'account_circle', '/manager/mon-compte', true]
  ],
  COMMERCIAL: [
    ['Dashboard', 'Vue d\u2019ensemble', 'dashboard', '', true],
    ['Clients', 'Clients professionnels', 'business', '/commercial/clients', true],
    ['Produits', 'Catalogue et pièces', 'inventory_2', '/commercial/products', true],
    ['Stock', 'Disponibilités et mouvements', 'warehouse', '/commercial/stock', true],
    ['Prospects', 'Prospection commerciale', 'person_search', '/commercial/prospects', true],
    ['Analytics', 'Activité commerciale', 'bar_chart', '/commercial/analytics', true],
    ['Commandes', 'Commandes clients', 'shopping_cart', '/commercial/orders', true],
    ['Devis', 'Demandes de devis', 'description', '/commercial/devis', true],
    ['Suivi commercial', 'Relances et opportunités', 'trending_up', null, false],
    ['Support', 'Conversations assignées', 'support_agent', '/commercial/support', true],
    ['Notifications', 'Alertes et événements', 'notifications', '/notifications', true],
    ['Mon compte', 'Profil commercial', 'account_circle', '/commercial/mon-compte', true]
  ],
  TECHNICIAN: [
    ['Dashboard', 'Vue d\u2019ensemble', 'dashboard', '', true],
    ['Mon activité', 'Suivi des interventions', 'bar_chart', '/technician/analytics', true],
    ['Commandes', 'Commandes clients', 'shopping_cart', '/technician/orders', true],
    ['Mes interventions', 'Interventions affectées', 'engineering', '/technician/maintenance', true],
    ['Machines', 'Parc équipements', 'print', null, false],
    ['Historique', 'Historique maintenance', 'history', null, false],
    ['Notifications', 'Alertes et événements', 'notifications', '/notifications', true],
    ['Mon compte', 'Profil technicien', 'account_circle', '/technician/mon-compte', true]
  ],
  CLIENT: [
    ['Dashboard', 'Vue d\u2019ensemble', 'dashboard', '', true],
    ['Mon activité', 'Commandes, devis et maintenance', 'bar_chart', '/client/analytics', true],
    ['Mes commandes', 'Historique des achats', 'shopping_cart', '/client/orders', true],
    ['Mes devis', 'Demandes de devis', 'description', '/devis/mes-demandes', true],
    ['Mes machines', 'Équipements enregistrés', 'print', '/client/machines', true],
    ['Mes interventions', 'Demandes de maintenance', 'engineering', '/client/maintenance', true],
    ['Documents', 'Factures et documents', 'folder', '/client/documents', true],
    ['Support', 'Assistance WWTRADING', 'support_agent', '/client/support', true],
    ['Notifications', 'Suivi de vos demandes', 'notifications', '/notifications', true],
    ['Mon compte', 'Profil et adresses', 'account_circle', '/client/mon-compte', true]
  ]
};

@Component({
  selector: 'wwt-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  private readonly auth = inject(AuthService);
  @Output() navigationSelected = new EventEmitter<void>();
  protected readonly dashboardRole = computed<WwtradingRole | null>(() => this.auth.roles()[0] ?? null);
  protected readonly dashboardRoute = computed(() => AuthService.resolveDashboardRoute(this.auth.roles()));
  protected readonly navItems = computed<readonly DashboardNavItem[]>(() => {
    const role = this.dashboardRole();
    const definitions = NAVIGATION[role ?? 'CLIENT'];
    return definitions.map(([label, description, icon, route, available]) => ({
      label,
      description,
      icon,
      route,
      available
    }));
  });

  protected selectNavigation(): void { this.navigationSelected.emit(); }
  protected logout(): void { this.selectNavigation(); void this.auth.logout(); }
}
