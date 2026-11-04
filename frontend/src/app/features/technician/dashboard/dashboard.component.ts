import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, Observable, of } from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { CommercialReportsApiService, MaintenanceAnalytics } from '../../commercial/reports/commercial-reports-api.service';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { MaintenanceResponse, MaintenanceStatus, PRIORITY_LABELS, STATUS_LABELS } from '../../maintenance/maintenance.models';
import { NotificationApiService } from '../../notifications/notification-api.service';

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
  private readonly maintenanceApi = inject(MaintenanceApiService);
  private readonly notifications = inject(NotificationApiService);

  protected readonly loading = signal(true);
  protected readonly failed = signal<readonly string[]>([]);
  protected readonly analytics = signal<MaintenanceAnalytics | null>(null);
  protected readonly assignments = signal<readonly MaintenanceResponse[]>([]);
  protected readonly unreadNotifications = signal<number | null>(null);
  protected readonly activeAssignments = computed(() => this.countMany(['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'WAITING_FOR_PART', 'COMPLETION_PENDING_CONFIRMATION']));
  protected readonly recentAssignments = computed(() => [...this.assignments()].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt)).slice(0, 6));

  ngOnInit(): void { this.loadDashboard(); }

  protected loadDashboard(): void {
    this.loading.set(true);
    this.failed.set([]);
    const today = new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - 29);
    forkJoin({
      analytics: this.safe('maintenance', this.reports.getMaintenance(iso(from), iso(today))),
      assignments: this.safe('affectations', this.maintenanceApi.getMyAssignments(0, 50)),
      notifications: this.safe('notifications', this.notifications.getUnreadCount())
    }).subscribe(({ analytics, assignments, notifications }) => {
      this.analytics.set(analytics);
      this.assignments.set(assignments?.content ?? []);
      this.unreadNotifications.set(notifications);
      this.loading.set(false);
    });
  }

  protected count(status: MaintenanceStatus): number {
    return this.analytics()?.byStatus.find(item => item.status === status)?.count ?? 0;
  }
  protected statusLabel(status: string): string { return STATUS_LABELS[status as MaintenanceStatus] ?? status; }
  protected priorityLabel(item: MaintenanceResponse): string { return PRIORITY_LABELS[item.priority]; }
  protected statusClass(status: MaintenanceStatus): string { return `status status--${status.toLowerCase()}`; }
  protected priorityClass(item: MaintenanceResponse): string { return `priority priority--${item.priority.toLowerCase()}`; }

  private countMany(statuses: readonly MaintenanceStatus[]): number {
    return statuses.reduce((total, status) => total + this.count(status), 0);
  }

  private safe<T>(name: string, source: Observable<T>): Observable<T | null> {
    return source.pipe(catchError(() => {
      this.failed.update(values => values.includes(name) ? values : [...values, name]);
      return of(null);
    }));
  }
}
