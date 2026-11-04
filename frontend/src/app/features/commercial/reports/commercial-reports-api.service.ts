import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../../core/config/runtime-config';
import { ProspectStatus } from '../../prospects/prospect-api.service';

export interface CommercialReportPeriod {
  readonly from: string;
  readonly to: string;
}

export interface CommercialReportKpis {
  readonly prospects: number;
  readonly activeProspects: number;
  readonly wonProspects: number;
  readonly lostProspects: number;
}

export interface ProspectStatusCount {
  readonly status: ProspectStatus;
  readonly count: number;
}

export interface CommercialActivityPoint {
  readonly date: string;
  readonly newProspects: number;
}

export interface CommercialReportProspect {
  readonly id: string;
  readonly companyName: string;
  readonly contactName: string;
  readonly city: string | null;
  readonly activity: string | null;
  readonly status: ProspectStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CommercialReport {
  readonly period: CommercialReportPeriod;
  readonly kpis: CommercialReportKpis;
  readonly prospectsByStatus: readonly ProspectStatusCount[];
  readonly activityOverTime: readonly CommercialActivityPoint[];
  readonly recentProspects: readonly CommercialReportProspect[];
}

export interface CommercialReportProspectPage {
  readonly content: readonly CommercialReportProspect[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly number: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface AnalyticsStatusCount {
  readonly status: string;
  readonly count: number;
}

export interface AnalyticsDailyCount {
  readonly date: string;
  readonly count: number;
}

export interface OrderAnalytics {
  readonly totalOrders: number;
  /** Order value: sum of all non-CANCELLED orders. What was ordered, NOT revenue. */
  readonly totalValue: number;
  /** Paid revenue: paymentStatus = PAID and status <> CANCELLED. */
  readonly paidRevenue: number;
  /** Recognized revenue: paymentStatus = PAID and status = COMPLETED. */
  readonly recognizedRevenue: number;
  /** Order value divided by the number of non-CANCELLED orders. */
  readonly averageOrderValue: number;
  /** Denominator of averageOrderValue. */
  readonly orderValueOrders: number;
  readonly byStatus: readonly AnalyticsStatusCount[];
  readonly overTime: readonly AnalyticsDailyCount[];
  /** Paid and recognized revenue per day, plus the contributing order count. */
  readonly revenueEvolution: readonly RevenueEvolutionPoint[];
}

export interface RevenueEvolutionPoint {
  readonly date: string;
  readonly paidRevenue: number;
  readonly recognizedRevenue: number;
  readonly orders: number;
}

export interface QuotationAnalytics {
  readonly totalQuotations: number;
  readonly byStatus: readonly AnalyticsStatusCount[];
  readonly overTime: readonly AnalyticsDailyCount[];
}

export interface MaintenanceAnalytics {
  readonly totalRequests: number;
  readonly byStatus: readonly AnalyticsStatusCount[];
  readonly overTime: readonly AnalyticsDailyCount[];
}

export interface ClientAnalytics {
  readonly totalClients: number;
  readonly activeClients: number;
  readonly newClients: number;
}

@Injectable({ providedIn: 'root' })
export class CommercialReportsApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/reports/commercial`;

  getReport(from: string, to: string): Observable<CommercialReport> {
    return this.http.get<CommercialReport>(this.resourceUrl, {
      params: new HttpParams().set('from', from).set('to', to)
    });
  }

  getProspects(
    from: string,
    to: string,
    page: number,
    size: number,
    status?: ProspectStatus,
    search?: string
  ): Observable<CommercialReportProspectPage> {
    let params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('page', page)
      .set('size', size);
    if (status) params = params.set('status', status);
    if (search?.trim()) params = params.set('search', search.trim());
    return this.http.get<CommercialReportProspectPage>(`${this.resourceUrl}/prospects`, { params });
  }

  getOrders(from: string, to: string): Observable<OrderAnalytics> {
    return this.http.get<OrderAnalytics>(`${this.apiRoot}/api/analytics/orders`, { params: this.period(from, to) });
  }

  getQuotations(from: string, to: string): Observable<QuotationAnalytics> {
    return this.http.get<QuotationAnalytics>(`${this.apiRoot}/api/analytics/quotations`, { params: this.period(from, to) });
  }

  getMaintenance(from: string, to: string): Observable<MaintenanceAnalytics> {
    return this.http.get<MaintenanceAnalytics>(`${this.apiRoot}/api/analytics/maintenance`, { params: this.period(from, to) });
  }

  getClients(from: string, to: string): Observable<ClientAnalytics> {
    return this.http.get<ClientAnalytics>(`${this.apiRoot}/api/analytics/clients`, { params: this.period(from, to) });
  }

  private get apiRoot(): string { return this.config.apiBaseUrl.replace(/\/$/, ''); }
  private period(from: string, to: string): HttpParams { return new HttpParams().set('from', from).set('to', to); }
}
