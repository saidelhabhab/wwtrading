import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { QuotationApiService } from './quotation-api.service';
import { getQuotationStatusLabel, getServiceTypeLabel, PageResponse, Quotation, QuotationStatus } from './quotation.models';

@Component({
  selector: 'wwt-quotation-list',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quotation-list.component.html',
  styleUrl: './quotation-list.component.scss'
})
export class QuotationListComponent implements OnInit {
  private readonly quotationApi = inject(QuotationApiService);
  private readonly auth = inject(AuthService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal<PageResponse<Quotation> | null>(null);
  readonly currentPageIndex = signal(0);
  readonly search = signal('');
  readonly statusFilter = signal<string>('ALL');

  readonly userRole = computed<WwtradingRole>(() => this.auth.roles()[0] ?? 'COMMERCIAL');

  readonly detailBasePath = computed(() => {
    const role = this.userRole();
    switch (role) {
      case 'ADMIN': return '/admin/devis';
      case 'MANAGER': return '/manager/devis';
      case 'COMMERCIAL': return '/commercial/devis';
      default: return '/commercial/devis';
    }
  });

  readonly skeletonRows = Array.from({ length: 5 }, (_, i) => i);

  readonly filteredQuotations = computed(() => {
    const raw = this.page()?.content ?? [];
    const q = this.search().trim().toLowerCase();
    if (!q) return raw;
    return raw.filter(item =>
      (item.fullName && item.fullName.toLowerCase().includes(q)) ||
      (item.company && item.company.toLowerCase().includes(q)) ||
      (item.email && item.email.toLowerCase().includes(q)) ||
      (item.phone && item.phone.toLowerCase().includes(q)) ||
      (item.serviceType && item.serviceType.toLowerCase().includes(q)) ||
      (item.requestedEquipment && item.requestedEquipment.toLowerCase().includes(q)) ||
      (item.city && item.city.toLowerCase().includes(q))
    );
  });

  // KPI counters computed from current loaded items
  readonly kpis = computed(() => {
    const items = this.page()?.content ?? [];
    return {
      total: items.length,
      pending: items.filter(i => i.status === 'PENDING').length,
      inReview: items.filter(i => i.status === 'IN_REVIEW').length,
      quoted: items.filter(i => i.status === 'QUOTED').length,
      accepted: items.filter(i => i.status === 'ACCEPTED').length,
      rejected: items.filter(i => i.status === 'REJECTED').length,
      cancelled: items.filter(i => i.status === 'CANCELLED').length
    };
  });

  readonly hasActiveFilters = computed(() => this.search().trim().length > 0 || this.statusFilter() !== 'ALL');

  ngOnInit(): void {
    this.load();
  }

  load(pageIndex = this.currentPageIndex()): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentPageIndex.set(pageIndex);

    const statusArg = this.statusFilter() !== 'ALL' ? this.statusFilter() : undefined;

    this.quotationApi.getAllQuotations(pageIndex, 20, statusArg)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => this.page.set(res),
        error: (err: HttpErrorResponse) => {
          if (err.status === 403) {
            this.error.set('Accès non autorisé pour consulter les devis.');
          } else {
            this.error.set('Impossible de charger les demandes de devis. Veuillez réessayer.');
          }
        }
      });
  }

  onStatusFilterChange(newStatus: string): void {
    this.statusFilter.set(newStatus);
    this.load(0);
  }

  resetFilters(): void {
    this.search.set('');
    this.statusFilter.set('ALL');
    this.load(0);
  }

  getStatusLabel(status: QuotationStatus | string): string {
    return getQuotationStatusLabel(status);
  }

  getServiceLabel(serviceType: string): string {
    return getServiceTypeLabel(serviceType);
  }

  getStatusBadgeClass(status: QuotationStatus | string): string {
    switch (status) {
      case 'PENDING': return 'badge--pending';
      case 'IN_REVIEW': return 'badge--in-review';
      case 'QUOTED': return 'badge--quoted';
      case 'ACCEPTED': return 'badge--accepted';
      case 'REJECTED': return 'badge--rejected';
      case 'CANCELLED': return 'badge--cancelled';
      default: return '';
    }
  }
}
