import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, Observable, of } from 'rxjs';

import { OrderApiService } from '../../orders/order-api.service';
import { OrderAnalyticsResponse } from '../../orders/order-analytics.models';
import { OrderResponse, PageResponse as OrderPage } from '../../orders/order.models';
import { ProductApiService } from '../../products/product-api.service';
import { Product, ProductPage } from '../../products/product.models';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import {
  getQuotationStatusLabel,
  PageResponse as QuotationPage,
  Quotation,
  QuotationStatus
} from '../../quotations/quotation.models';
import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { FinancialOverviewComponent } from '../../../shared/components/financial-overview/financial-overview.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { CommercialReportsApiService, ClientAnalytics, MaintenanceAnalytics } from '../../commercial/reports/commercial-reports-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { SupportApiService } from '../../support/support-api.service';

/** Local-date ISO (YYYY-MM-DD) for analytics query parameters, without UTC shifting. */
function formatIsoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

const QUOTATION_PIPELINE = [
  'PENDING',
  'IN_REVIEW',
  'QUOTED',
  'ACCEPTED',
  'REJECTED',
  'CANCELLED'
] as const;

type DashboardSource = 'orders' | 'quotations' | 'products' | 'analytics' | 'clients' | 'maintenance' | 'support' | 'notifications';

interface DashboardActivity {
  readonly id: string;
  readonly icon: string;
  readonly kind: string;
  readonly title: string;
  readonly detail: string;
  readonly date: string;
  readonly route: string[] | null;
}

@Component({
  selector: 'wwt-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatIconModule,
    DashboardLayoutComponent,
    PageHeaderComponent,
    StatCardComponent,
    FinancialOverviewComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly orderApi = inject(OrderApiService);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly productApi = inject(ProductApiService);
  private readonly reportsApi = inject(CommercialReportsApiService);
  private readonly notificationApi = inject(NotificationApiService);
  private readonly supportApi = inject(SupportApiService);

  protected readonly loading = signal(true);
  protected readonly failedSources = signal<readonly DashboardSource[]>([]);
  /** Undefined when the analytics source failed; the section then shows Unavailable. */
  protected readonly analytics = signal<OrderAnalyticsResponse | null | undefined>(null);
  protected readonly analyticsLoading = signal(false);
  protected readonly analyticsFailed = signal(false);
  protected analyticsRange = '';
  protected readonly orders = signal<OrderPage<OrderResponse> | null>(null);
  protected readonly quotations = signal<QuotationPage<Quotation> | null>(null);
  protected readonly products = signal<ProductPage | null>(null);
  protected readonly quotationCounts = signal<Readonly<Record<string, number>>>({});
  protected readonly clientAnalytics = signal<ClientAnalytics | null>(null);
  protected readonly maintenanceAnalytics = signal<MaintenanceAnalytics | null>(null);
  protected readonly openSupport = signal<number | null>(null);
  protected readonly unreadNotifications = signal<number | null>(null);

  protected readonly recentProducts = computed(() => this.products()?.content.slice(0, 5) ?? []);
  protected readonly recentActivity = computed<readonly DashboardActivity[]>(() => {
    const orderActivity = (this.orders()?.content ?? []).map((order) => ({
      id: `order-${order.id}`,
      icon: 'shopping_cart',
      kind: 'Commande',
      title: `Commande #${this.shortReference(order.id)}`,
      detail: `${this.orderStatusLabel(order.status)} · ${order.total.toLocaleString('fr-MA', { minimumFractionDigits: 2 })} DH`,
      date: order.createdAt,
      route: ['/admin/orders', order.id]
    }));
    const quotationActivity = (this.quotations()?.content ?? []).map((quotation) => ({
      id: `quotation-${quotation.id}`,
      icon: 'description',
      kind: 'Devis',
      title: `Demande #${this.shortReference(quotation.id)}`,
      detail: getQuotationStatusLabel(quotation.status),
      date: quotation.createdAt,
      route: ['/admin/devis', quotation.id]
    }));
    return [...orderActivity, ...quotationActivity]
      .sort((left, right) => Date.parse(right.date) - Date.parse(left.date))
      .slice(0, 7);
  });

  protected readonly availableSources = computed(() => 8 - this.failedSources().length);
  protected readonly quotationPipeline = QUOTATION_PIPELINE;

  ngOnInit(): void {
    this.loadDashboard();
  }

  protected loadDashboard(): void {
    this.loading.set(true);
    this.failedSources.set([]);
    this.loadAnalytics();
    const today = new Date();
    const fromValue = formatIsoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    const toValue = formatIsoDate(today);

    forkJoin({
      orders: this.safe('orders', this.orderApi.getAllOrders(0, 5)),
      quotations: this.safe('quotations', this.quotationApi.getAllQuotations(0, 5)),
      quotationCounts: this.loadQuotationCounts(),
      products: this.safe('products', this.productApi.list(0, 5, 'createdAt,desc')),
      clients: this.safe('clients', this.reportsApi.getClients(fromValue, toValue)),
      maintenance: this.safe('maintenance', this.reportsApi.getMaintenance(fromValue, toValue)),
      support: this.safe('support', this.supportApi.getAgentConversations(0, 1, 'OPEN')),
      notifications: this.safe('notifications', this.notificationApi.getUnreadCount())
    }).subscribe({
      next: ({ orders, quotations, quotationCounts, products, clients, maintenance, support, notifications }) => {
        this.orders.set(orders);
        this.quotations.set(quotations);
        this.quotationCounts.set(quotationCounts ?? {});
        this.products.set(products);
        this.clientAnalytics.set(clients);
        this.maintenanceAnalytics.set(maintenance);
        this.openSupport.set(support?.totalElements ?? null);
        this.unreadNotifications.set(notifications);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected quotationCount(status: QuotationStatus): number | null {
    return this.quotationCounts()[status] ?? null;
  }

  protected quotationPercent(status: QuotationStatus): number {
    const total = this.quotations()?.totalElements ?? 0;
    const count = this.quotationCount(status) ?? 0;
    return total > 0 ? Math.round((count / total) * 100) : 0;
  }

  protected quotationLabel(status: QuotationStatus): string {
    return getQuotationStatusLabel(status);
  }

  protected shortReference(id: string): string {
    return id.slice(0, 8).toUpperCase();
  }

  protected productName(product: Product): string {
    return [product.brand, product.model || product.name].filter(Boolean).join(' ') || product.name;
  }

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

  private loadQuotationCounts(): Observable<Readonly<Record<string, number>> | null> {
    const requests = QUOTATION_PIPELINE.map((status) =>
      this.quotationApi.getAllQuotations(0, 1, status).pipe(
        map((page) => [status, page.totalElements] as const)
      )
    );
    return this.safe('quotations', forkJoin(requests).pipe(
      map((entries) => Object.fromEntries(entries))
    ));
  }

  /**
   * Loads the financial aggregates for the current month. A failure is recorded as a
   * failed source and leaves the payload undefined, so the UI shows Unavailable
   * instead of a misleading 0 DH.
   */
  private loadAnalytics(): void {
    const today = new Date();
    const from = new Date(today.getFullYear(), today.getMonth(), 1);
    const to = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    this.analyticsRange = `${formatIsoDate(from)} → ${formatIsoDate(to)}`;
    this.analyticsLoading.set(true);
    this.analyticsFailed.set(false);

    this.orderApi.getOrderAnalytics(formatIsoDate(from), formatIsoDate(to)).pipe(
      catchError(() => {
        this.analyticsFailed.set(true);
        this.analytics.set(undefined);
        this.failedSources.update((sources) => sources.includes('analytics') ? sources : [...sources, 'analytics']);
        return of(undefined);
      }),
      finalize(() => this.analyticsLoading.set(false))
    ).subscribe((analytics) => {
      if (analytics) this.analytics.set(analytics);
    });
  }

  private safe<T>(source: DashboardSource, request: Observable<T>): Observable<T | null> {
    return request.pipe(catchError(() => {
      this.failedSources.update((sources) => sources.includes(source) ? sources : [...sources, source]);
      return of(null);
    }));
  }
}
