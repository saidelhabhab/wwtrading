import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { Observable, catchError, concatMap, forkJoin, from, map, of, reduce, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { StatusBadgeComponent, StatusVariant } from '../../../shared/components/status-badge/status-badge.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { OrderApiService } from '../../orders/order-api.service';
import { OrderResponse } from '../../orders/order.models';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import { getQuotationStatusLabel, getServiceTypeLabel, Quotation } from '../../quotations/quotation.models';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { MaintenanceResponse } from '../../maintenance/maintenance.models';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { SupportApiService } from '../../support/support-api.service';

interface PageResult<T> {
  readonly content: readonly T[];
  readonly totalPages: number;
}

export type Activity =
  | { readonly kind: 'order'; readonly id: string; readonly date: string; readonly title: string; readonly status: string; readonly amount: number; readonly route: string[] }
  | { readonly kind: 'quotation'; readonly id: string; readonly date: string; readonly title: string; readonly status: string; readonly amount: number | null; readonly route: string[] };

@Component({
  selector: 'wwt-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatIconModule,
    DashboardLayoutComponent,
    PageHeaderComponent,
    StatCardComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    ErrorStateComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly orderApi = inject(OrderApiService);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly maintenanceApi = inject(MaintenanceApiService);
  private readonly notificationApi = inject(NotificationApiService);
  private readonly supportApi = inject(SupportApiService);

  protected readonly loading = signal(true);
  protected readonly ordersError = signal<string | null>(null);
  protected readonly quotationsError = signal<string | null>(null);
  protected readonly servicesError = signal<string | null>(null);
  protected readonly orders = signal<readonly OrderResponse[]>([]);
  protected readonly quotations = signal<readonly Quotation[]>([]);
  protected readonly maintenance = signal<readonly MaintenanceResponse[]>([]);
  protected readonly supportCount = signal<number | null>(null);
  protected readonly unreadNotifications = signal<number | null>(null);
  protected readonly displayName = computed(() => this.auth.user()?.displayName ?? 'Client');
  protected readonly pendingOrders = computed(() => this.orders().filter((order) => order.status === 'DRAFT').length);
  protected readonly pendingQuotations = computed(() => this.quotations().filter((quotation) => quotation.status === 'PENDING').length);
  protected readonly activeMaintenance = computed(() => this.maintenance().filter((item) => !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(item.status)).length);
  protected readonly hasError = computed(() => Boolean(this.ordersError() || this.quotationsError() || this.servicesError()));
  protected readonly hasActivity = computed(() => this.orders().length > 0 || this.quotations().length > 0);
  protected readonly recentActivity = computed<readonly Activity[]>(() => [
    ...this.orders().map((order): Activity => ({
      kind: 'order', id: order.id, date: order.createdAt,
      title: `Commande #${this.shortReference(order.id)}`,
      status: this.orderStatusLabel(order.status), amount: order.total,
      route: ['/client/orders', order.id]
    })),
    ...this.quotations().map((quotation): Activity => ({
      kind: 'quotation', id: quotation.id, date: quotation.createdAt,
      title: getServiceTypeLabel(quotation.serviceType),
      status: getQuotationStatusLabel(quotation.status), amount: quotation.proposedAmount,
      route: ['/devis/mes-demandes', quotation.id]
    }))
  ].sort((left, right) => Date.parse(right.date) - Date.parse(left.date)).slice(0, 6));

  ngOnInit(): void { this.loadDashboard(); }

  protected loadDashboard(): void {
    this.loading.set(true);
    this.ordersError.set(null);
    this.quotationsError.set(null);
    this.servicesError.set(null);
    forkJoin({
      orders: this.loadAllPages((page, size) => this.orderApi.getMyOrders(page, size)).pipe(
        map((data) => ({ data, error: null as string | null })),
        catchError(() => of({ data: [] as readonly OrderResponse[], error: 'Vos commandes ne peuvent pas être chargées.' }))
      ),
      quotations: this.loadAllPages((page, size) => this.quotationApi.getMyQuotations(page, size)).pipe(
        map((data) => ({ data, error: null as string | null })),
        catchError(() => of({ data: [] as readonly Quotation[], error: 'Vos demandes de devis ne peuvent pas être chargées.' }))
      ),
      maintenance: this.loadAllPages((page, size) => this.maintenanceApi.getMyRequests(page, size)).pipe(
        catchError(() => { this.servicesError.set('La maintenance, le support ou les notifications sont partiellement indisponibles.'); return of([] as readonly MaintenanceResponse[]); })
      ),
      support: this.supportApi.getMyConversations(0, 1).pipe(
        map((page) => page.totalElements),
        catchError(() => { this.servicesError.set('La maintenance, le support ou les notifications sont partiellement indisponibles.'); return of(null); })
      ),
      notifications: this.notificationApi.getUnreadCount().pipe(catchError(() => { this.servicesError.set('La maintenance, le support ou les notifications sont partiellement indisponibles.'); return of(null); }))
    }).subscribe(({ orders, quotations, maintenance, support, notifications }) => {
      this.orders.set(orders.data);
      this.quotations.set(quotations.data);
      this.maintenance.set(maintenance);
      this.supportCount.set(support);
      this.unreadNotifications.set(notifications);
      this.ordersError.set(orders.error);
      this.quotationsError.set(quotations.error);
      this.loading.set(false);
    });
  }

  protected activityIcon(activity: Activity): string { return activity.kind === 'order' ? 'shopping_bag' : 'request_quote'; }
  protected activityType(activity: Activity): string { return activity.kind === 'order' ? 'Commande' : 'Devis'; }
  
  protected statusVariant(activity: Activity): StatusVariant {
    const normalized = activity.status.toLowerCase();
    if (normalized.includes('annul') || normalized.includes('refus')) return 'danger';
    if (normalized.includes('accept') || normalized.includes('pay') || normalized.includes('termin') || normalized.includes('livr')) return 'success';
    if (normalized.includes('traitement') || normalized.includes('étude') || normalized.includes('confirm') || normalized.includes('cours')) return 'info';
    return 'warning';
  }

  private shortReference(id: string): string { return id.slice(0, 8).toUpperCase(); }
  
  protected orderStatusLabel(status: OrderResponse['status']): string {
    switch (status) {
      case 'DRAFT': return 'En attente';
      case 'CONFIRMED': return 'Confirmée';
      case 'PROCESSING': return 'En traitement';
      case 'READY': return 'Prête';
      case 'COMPLETED': return 'Terminée';
      case 'CANCELLED': return 'Annulée';
    }
  }

  private loadAllPages<T>(loadPage: (page: number, size: number) => Observable<PageResult<T>>): Observable<readonly T[]> {
    const pageSize = 50;
    return loadPage(0, pageSize).pipe(
      switchMap((firstPage) => {
        if (firstPage.totalPages <= 1) return of(firstPage.content);
        return from(Array.from({ length: firstPage.totalPages - 1 }, (_, index) => index + 1)).pipe(
          concatMap((page) => loadPage(page, pageSize)),
          map((page) => page.content),
          reduce((items, content) => [...items, ...content], [...firstPage.content])
        );
      })
    );
  }
}
