import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, Observable, of } from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { CommercialReport, CommercialReportsApiService } from '../reports/commercial-reports-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { SupportApiService } from '../../support/support-api.service';
import { PROSPECT_STATUS_LABELS, ProspectStatus } from '../../prospects/prospect-api.service';

function iso(value: Date): string {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'wwt-dashboard',
  standalone: true,
  imports: [DatePipe, RouterLink, MatIconModule, DashboardLayoutComponent, PageHeaderComponent, StatCardComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly reports = inject(CommercialReportsApiService);
  private readonly notifications = inject(NotificationApiService);
  private readonly support = inject(SupportApiService);

  protected readonly loading = signal(true);
  protected readonly failed = signal<readonly string[]>([]);
  protected readonly report = signal<CommercialReport | null>(null);
  protected readonly unreadNotifications = signal<number | null>(null);
  protected readonly openSupport = signal<number | null>(null);
  protected readonly pipeline = computed(() => this.report()?.prospectsByStatus ?? []);
  protected readonly maxPipeline = computed(() => Math.max(1, ...this.pipeline().map(item => item.count)));

  ngOnInit(): void { this.loadDashboard(); }

  protected loadDashboard(): void {
    this.loading.set(true);
    this.failed.set([]);
    const today = new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - 29);
    forkJoin({
      report: this.safe('prospects', this.reports.getReport(iso(from), iso(today))),
      notifications: this.safe('notifications', this.notifications.getUnreadCount()),
      support: this.safe('support', this.support.getAgentConversations(0, 1, 'OPEN'))
    }).subscribe(({ report, notifications, support }) => {
      this.report.set(report);
      this.unreadNotifications.set(notifications);
      this.openSupport.set(support?.totalElements ?? null);
      this.loading.set(false);
    });
  }

  protected count(status: ProspectStatus): number {
    return this.pipeline().find(item => item.status === status)?.count ?? 0;
  }

  protected statusLabel(status: ProspectStatus): string { return PROSPECT_STATUS_LABELS[status]; }
  protected pipelineWidth(value: number): number { return value === 0 ? 0 : Math.round(value / this.maxPipeline() * 100); }

  private safe<T>(name: string, source: Observable<T>): Observable<T | null> {
    return source.pipe(catchError(() => {
      this.failed.update(values => values.includes(name) ? values : [...values, name]);
      return of(null);
    }));
  }
}
