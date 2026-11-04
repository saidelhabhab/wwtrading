import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';

import { AuthService, WwtradingRole } from '../../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import {
  PROSPECT_STATUS_LABELS,
  PROSPECT_STATUSES,
  ProspectStatus
} from '../../prospects/prospect-api.service';
import {
  CommercialReport,
  CommercialReportProspectPage,
  CommercialReportsApiService,
  ClientAnalytics,
  MaintenanceAnalytics,
  OrderAnalytics,
  QuotationAnalytics
} from './commercial-reports-api.service';
import { FinancialOverviewComponent } from '../../../shared/components/financial-overview/financial-overview.component';
import { OrderAnalyticsResponse } from '../../orders/order-analytics.models';

/**
 * Maps the analytics API payload onto the shared financial overview contract.
 * Field names match the backend response one-for-one; no value is recomputed.
 */
function toFinancialOverview(orders: OrderAnalytics): OrderAnalyticsResponse {
  return {
    totalOrders: orders.totalOrders,
    totalValue: orders.totalValue,
    paidRevenue: orders.paidRevenue,
    recognizedRevenue: orders.recognizedRevenue,
    averageOrderValue: orders.averageOrderValue,
    orderValueOrders: orders.orderValueOrders,
    byStatus: [],
    overTime: [],
    revenueEvolution: orders.revenueEvolution
  };
}

type PeriodPreset = 'TODAY' | '7D' | '30D' | '90D' | 'YEAR' | 'CUSTOM';

@Component({
  selector: 'wwt-commercial-reports',
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule, MatIconModule, RouterLink, DashboardLayoutComponent, PageHeaderComponent, FinancialOverviewComponent],
  templateUrl: './commercial-reports.component.html',
  styleUrl: './commercial-reports.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CommercialReportsComponent implements OnInit {
  private readonly api = inject(CommercialReportsApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly pageSize = 12;

  protected readonly statusLabels = PROSPECT_STATUS_LABELS;
  protected readonly statuses = PROSPECT_STATUSES;
  protected readonly presets: readonly { value: PeriodPreset; label: string }[] = [
    { value: 'TODAY', label: "Aujourd'hui" },
    { value: '7D', label: '7 derniers jours' },
    { value: '30D', label: '30 derniers jours' },
    { value: '90D', label: '90 derniers jours' },
    { value: 'YEAR', label: 'Cette année' }
  ];

  protected readonly report = signal<CommercialReport | null>(null);
  protected readonly orderAnalytics = signal<OrderAnalytics | null>(null);
  protected readonly quotationAnalytics = signal<QuotationAnalytics | null>(null);
  protected readonly maintenanceAnalytics = signal<MaintenanceAnalytics | null>(null);
  protected readonly clientAnalytics = signal<ClientAnalytics | null>(null);
  protected readonly prospects = signal<CommercialReportProspectPage | null>(null);
  protected readonly loading = signal(true);
  protected readonly listLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly listError = signal(false);
  protected readonly partialError = signal(false);
  protected readonly preset = signal<PeriodPreset>('30D');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly page = signal(0);
  protected readonly statusFilter = signal<'ALL' | ProspectStatus>('ALL');
  protected readonly search = signal('');
  protected readonly role = computed<WwtradingRole>(() =>
    (this.route.snapshot.data['role'] as WwtradingRole | undefined) ?? this.auth.roles()[0] ?? 'CLIENT');
  protected readonly canSeeProspects = computed(() => ['ADMIN', 'MANAGER', 'COMMERCIAL'].includes(this.role()));
  /**
   * Only ADMIN and MANAGER see global financial analytics. CLIENT has its own order
   * figures but must never see global revenue, and COMMERCIAL/TECHNICIAN are excluded
   * because no reliable commercial-to-order relationship exists in order-service.
   */
  protected readonly canSeeGlobalFinance = computed(() => ['ADMIN', 'MANAGER'].includes(this.role()));
  /**
   * Financial payload for the section. Deliberately null for non-management roles so a
   * CLIENT can never render global financial figures even if the source returned them.
   */
  protected readonly financialData = computed<OrderAnalyticsResponse | null>(() => {
    if (!this.canSeeGlobalFinance()) return null;
    const orders = this.orderAnalytics();
    // null (source failed) is preserved so the UI shows Unavailable, never 0 DH.
    return orders ? toFinancialOverview(orders) : null;
  });
  protected readonly periodLabel = computed(() =>
    this.from() && this.to() ? `${this.from()} \u2192 ${this.to()}` : '');
  protected readonly pageTitle = computed(() => this.role() === 'CLIENT' || this.role() === 'TECHNICIAN' ? 'Mon activité' : 'Analytics');
  protected readonly pageSubtitle = computed(() => ({
    ADMIN: 'Vue globale de l’activité opérationnelle et commerciale',
    MANAGER: 'Pilotage de l’activité métier',
    COMMERCIAL: 'Analyse de votre activité commerciale',
    CLIENT: 'Suivi de vos commandes, devis et demandes',
    TECHNICIAN: 'Suivi de vos interventions assignées'
  })[this.role()]);

  protected readonly maxDaily = computed(() => Math.max(
    1,
    ...(this.report()?.activityOverTime.map(point => point.newProspects) ?? [0])
  ));
  protected readonly maxPipeline = computed(() => Math.max(
    1,
    ...(this.report()?.prospectsByStatus.map(item => item.count) ?? [0])
  ));

  ngOnInit(): void {
    this.selectPreset('30D');
  }

  protected selectPreset(value: PeriodPreset): void {
    this.preset.set(value);
    if (value === 'CUSTOM') return;
    const today = this.startOfDay(new Date());
    const from = new Date(today);
    if (value === '7D') from.setDate(from.getDate() - 6);
    if (value === '30D') from.setDate(from.getDate() - 29);
    if (value === '90D') from.setDate(from.getDate() - 89);
    if (value === 'YEAR') from.setMonth(0, 1);
    this.from.set(this.toDateValue(from));
    this.to.set(this.toDateValue(today));
    this.page.set(0);
    this.load();
  }

  protected applyCustomPeriod(): void {
    if (!this.from() || !this.to() || this.from() > this.to()) return;
    this.preset.set('CUSTOM');
    this.page.set(0);
    this.load();
  }

  protected applyTableFilters(): void {
    this.page.set(0);
    this.loadProspects();
  }

  protected clearFilters(): void {
    this.search.set('');
    this.statusFilter.set('ALL');
    this.applyTableFilters();
  }

  protected goToPage(page: number): void {
    const totalPages = this.prospects()?.totalPages ?? 0;
    if (page < 0 || page >= totalPages || page === this.page()) return;
    this.page.set(page);
    this.loadProspects();
  }

  protected reload(): void {
    this.load();
  }

  protected activityHeight(value: number): number {
    return Math.max(5, Math.round((value / this.maxDaily()) * 100));
  }

  protected pipelineWidth(value: number): number {
    return value === 0 ? 0 : Math.max(4, Math.round((value / this.maxPipeline()) * 100));
  }

  protected share(value: number): number {
    const total = this.report()?.kpis.prospects ?? 0;
    return total === 0 ? 0 : Math.round((value / total) * 100);
  }

  protected statusClass(status: ProspectStatus): string {
    return `status status--${status.toLowerCase().replace('_', '-')}`;
  }

  protected businessStatusLabel(status: string): string {
    return ({
      DRAFT: 'En attente', CONFIRMED: 'Confirmée', PROCESSING: 'En traitement', READY: 'Prête', COMPLETED: 'Terminée', CANCELLED: 'Annulée',
      PENDING: 'En attente', IN_REVIEW: 'En étude', QUOTED: 'Proposée', ACCEPTED: 'Acceptée', REJECTED: 'Rejetée', SENT: 'Envoyée', EXPIRED: 'Expirée',
      OPEN: 'Ouverte', SUBMITTED: 'Soumise', ASSIGNED: 'Assignée', IN_PROGRESS: 'En cours', WAITING_FOR_PART: 'Attente de pièce', COMPLETION_PENDING_CONFIRMATION: 'Confirmation client',
      NEW: 'Nouveau', CONTACTED: 'Contacté', INTERESTED: 'Intéressé', QUOTE_SENT: 'Devis envoyé', WON: 'Gagné', LOST: 'Perdu'
    } as Record<string, string>)[status] ?? status;
  }

  protected domainBarHeight(points: readonly { count: number }[], value: number): number {
    const maximum = Math.max(1, ...points.map(point => point.count));
    return Math.max(8, Math.round((value / maximum) * 100));
  }

  private load(): void {
    if (!this.from() || !this.to()) return;
    this.loading.set(true);
    this.error.set(false);
    this.partialError.set(false);
    const role = this.role();
    const prospectRequest = ['ADMIN', 'MANAGER', 'COMMERCIAL'].includes(role)
      ? this.api.getReport(this.from(), this.to()).pipe(catchError(() => { this.partialError.set(true); return of(null); }))
      : of(null);
    const orderRequest = ['ADMIN', 'MANAGER', 'CLIENT'].includes(role)
      ? this.api.getOrders(this.from(), this.to()).pipe(catchError(() => { this.partialError.set(true); return of(null); }))
      : of(null);
    const quotationRequest = ['ADMIN', 'MANAGER', 'CLIENT'].includes(role)
      ? this.api.getQuotations(this.from(), this.to()).pipe(catchError(() => { this.partialError.set(true); return of(null); }))
      : of(null);
    const maintenanceRequest = ['ADMIN', 'MANAGER', 'CLIENT', 'TECHNICIAN'].includes(role)
      ? this.api.getMaintenance(this.from(), this.to()).pipe(catchError(() => { this.partialError.set(true); return of(null); }))
      : of(null);
    const clientRequest = ['ADMIN', 'MANAGER'].includes(role)
      ? this.api.getClients(this.from(), this.to()).pipe(catchError(() => { this.partialError.set(true); return of(null); }))
      : of(null);

    forkJoin({ prospects: prospectRequest, orders: orderRequest, quotations: quotationRequest, maintenance: maintenanceRequest, clients: clientRequest })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: result => {
          this.report.set(result.prospects);
          this.orderAnalytics.set(result.orders);
          this.quotationAnalytics.set(result.quotations);
          this.maintenanceAnalytics.set(result.maintenance);
          this.clientAnalytics.set(result.clients);
          this.error.set(!result.prospects && !result.orders && !result.quotations && !result.maintenance && !result.clients);
        },
        error: () => {
          this.report.set(null);
          this.orderAnalytics.set(null);
          this.quotationAnalytics.set(null);
          this.maintenanceAnalytics.set(null);
          this.clientAnalytics.set(null);
          this.error.set(true);
        }
      });
    if (this.canSeeProspects()) this.loadProspects();
    else {
      this.prospects.set(null);
      this.listLoading.set(false);
    }
  }

  private loadProspects(): void {
    if (!this.from() || !this.to()) return;
    this.listLoading.set(true);
    this.listError.set(false);
    // Read the filter once so the 'ALL' sentinel can be narrowed to undefined before the call.
    const filter = this.statusFilter();
    const status = filter === 'ALL' ? undefined : filter;
    this.api.getProspects(this.from(), this.to(), this.page(), this.pageSize, status, this.search())
      .pipe(finalize(() => this.listLoading.set(false)))
      .subscribe({
        next: page => this.prospects.set(page),
        error: () => {
          this.prospects.set(null);
          this.listError.set(true);
        }
      });
  }

  private startOfDay(value: Date): Date {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate());
  }

  private toDateValue(value: Date): string {
    const year = value.getFullYear();
    const month = `${value.getMonth() + 1}`.padStart(2, '0');
    const day = `${value.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
