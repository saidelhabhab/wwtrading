import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, Observable, of } from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { FinancialOverviewComponent } from '../../../shared/components/financial-overview/financial-overview.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { CommercialReport, CommercialReportsApiService, ClientAnalytics, MaintenanceAnalytics, OrderAnalytics, QuotationAnalytics } from '../../commercial/reports/commercial-reports-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { OrderAnalyticsResponse } from '../../orders/order-analytics.models';
import { OrderStatus } from '../../orders/order.models';
import { SupportApiService } from '../../support/support-api.service';

function iso(value: Date): string {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'wwt-dashboard',
  standalone: true,
  imports: [RouterLink, MatIconModule, DashboardLayoutComponent, PageHeaderComponent, StatCardComponent, FinancialOverviewComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly reports = inject(CommercialReportsApiService);
  private readonly notifications = inject(NotificationApiService);
  private readonly support = inject(SupportApiService);

  protected readonly loading = signal(true);
  protected readonly failed = signal<readonly string[]>([]);
  protected readonly orders = signal<OrderAnalytics | null>(null);
  protected readonly quotations = signal<QuotationAnalytics | null>(null);
  protected readonly maintenance = signal<MaintenanceAnalytics | null>(null);
  protected readonly clients = signal<ClientAnalytics | null>(null);
  protected readonly commercial = signal<CommercialReport | null>(null);
  protected readonly unreadNotifications = signal<number | null>(null);
  protected readonly openSupport = signal<number | null>(null);
  protected readonly analytics = computed<OrderAnalyticsResponse | null>(() => {
    const value = this.orders();
    return value ? {
      ...value,
      byStatus: value.byStatus.map(item => ({ status: item.status as OrderStatus, count: item.count }))
    } : null;
  });
  protected readonly pipeline = computed(() => this.commercial()?.prospectsByStatus ?? []);
  protected readonly maxPipeline = computed(() => Math.max(1, ...this.pipeline().map(item => item.count)));
  protected periodLabel = '';

  ngOnInit(): void { this.loadDashboard(); }

  protected loadDashboard(): void {
    this.loading.set(true);
    this.failed.set([]);
    const today = new Date();
    const fromValue = iso(new Date(today.getFullYear(), today.getMonth(), 1));
    const toValue = iso(today);
    this.periodLabel = `${fromValue} → ${toValue}`;

    forkJoin({
      orders: this.safe('commandes', this.reports.getOrders(fromValue, toValue)),
      quotations: this.safe('devis', this.reports.getQuotations(fromValue, toValue)),
      maintenance: this.safe('maintenance', this.reports.getMaintenance(fromValue, toValue)),
      clients: this.safe('clients', this.reports.getClients(fromValue, toValue)),
      commercial: this.safe('prospects', this.reports.getReport(fromValue, toValue)),
      notifications: this.safe('notifications', this.notifications.getUnreadCount()),
      support: this.safe('support', this.support.getAgentConversations(0, 1, 'OPEN'))
    }).subscribe(({ orders, quotations, maintenance, clients, commercial, notifications, support }) => {
      this.orders.set(orders);
      this.quotations.set(quotations);
      this.maintenance.set(maintenance);
      this.clients.set(clients);
      this.commercial.set(commercial);
      this.unreadNotifications.set(notifications);
      this.openSupport.set(support?.totalElements ?? null);
      this.loading.set(false);
    });
  }

  protected statusLabel(status: string): string {
    return ({ NEW: 'Nouveaux', CONTACTED: 'Contactés', INTERESTED: 'Intéressés', QUOTE_SENT: 'Devis envoyés', WON: 'Gagnés', LOST: 'Perdus' } as Record<string, string>)[status] ?? status;
  }

  protected pipelineWidth(value: number): number {
    return value === 0 ? 0 : Math.round(value / this.maxPipeline() * 100);
  }

  private safe<T>(name: string, source: Observable<T>): Observable<T | null> {
    return source.pipe(catchError(() => {
      this.failed.update(values => values.includes(name) ? values : [...values, name]);
      return of(null);
    }));
  }
}
