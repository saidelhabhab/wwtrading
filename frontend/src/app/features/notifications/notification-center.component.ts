import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize, firstValueFrom } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { NotificationApiService } from './notification-api.service';
import {
  isSafeActionUrl,
  safeNavigateActionUrl,
} from './notification-navigation.util';
import { Notification, NOTIFICATION_TYPE_ICONS, NotificationPage } from './notification.models';

export type NotificationFilter = 'all' | 'unread';

@Component({
  selector: 'wwt-notification-center',
  standalone: true,
  imports: [RouterLink, DatePipe, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notification-center.component.html',
  styleUrl: './notification-center.component.scss',
})
export class NotificationCenterComponent implements OnInit {
  private readonly notificationSvc = inject(NotificationApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal<NotificationPage | null>(null);
  readonly currentPageIndex = signal(0);
  readonly filter = signal<NotificationFilter>('all');
  readonly markingAll = signal(false);
  readonly typeIcons = NOTIFICATION_TYPE_ICONS;

  readonly unreadCount = this.notificationSvc.unreadCount;

  readonly rawNotifications = computed<readonly Notification[]>(() => this.page()?.content ?? []);

  /**
   * Client-side filtering of current page results without inventing unsupported backend params.
   */
  readonly filteredNotifications = computed<readonly Notification[]>(() => {
    const list = this.rawNotifications();
    if (this.filter() === 'unread') {
      return list.filter((n) => !n.readAt);
    }
    return list;
  });

  readonly breadcrumb = computed(() => {
    const role = this.auth.roles()[0];
    switch (role) {
      case 'ADMIN': return 'Administration / Notifications';
      case 'MANAGER': return 'Management / Notifications';
      case 'COMMERCIAL': return 'Commercial / Notifications';
      case 'TECHNICIAN': return 'Technicien / Notifications';
      default: return 'Notifications';
    }
  });

  ngOnInit(): void {
    this.load(0);
  }

  load(pageIndex = this.currentPageIndex()): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentPageIndex.set(pageIndex);

    this.notificationSvc
      .getNotifications(pageIndex, 20)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => {
          this.page.set(res);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 401) {
            this.error.set('Votre session a expiré. Veuillez vous reconnecter.');
          } else if (err.status === 403) {
            this.error.set('Vous n’êtes pas autorisé à consulter ces notifications.');
          } else {
            this.error.set('Impossible de charger vos notifications. Veuillez réessayer.');
          }
        },
      });
  }

  setFilter(f: NotificationFilter): void {
    this.filter.set(f);
  }

  onNotificationClick(item: Notification): void {
    if (!item.readAt) {
      this.markAsRead(item);
    }
    if (isSafeActionUrl(item.actionUrl)) {
      void safeNavigateActionUrl(this.router, item.actionUrl);
    }
  }

  markAsRead(item: Notification): void {
    if (item.readAt) return;
    this.notificationSvc.markAsRead(item.id).subscribe({
      next: () => {
        this.notificationSvc.decrementUnreadCount();
        const p = this.page();
        if (p) {
          const updatedContent = p.content.map((n) =>
            n.id === item.id ? { ...n, readAt: new Date().toISOString() } : n
          );
          this.page.set({ ...p, content: updatedContent });
        }
      },
      error: () => {/* Silent fail or snackbar */},
    });
  }

  markAllAsRead(): void {
    if (this.markingAll() || this.unreadCount() === 0) return;
    this.markingAll.set(true);

    this.notificationSvc
      .markAllAsRead()
      .pipe(finalize(() => this.markingAll.set(false)))
      .subscribe({
        next: () => {
          this.notificationSvc.markAllRead();
          const p = this.page();
          if (p) {
            const updatedContent = p.content.map((n) => ({
              ...n,
              readAt: n.readAt ?? new Date().toISOString(),
            }));
            this.page.set({ ...p, content: updatedContent });
          }
        },
        error: () => {/* Silent fail */},
      });
  }

  goToPage(pageIndex: number): void {
    this.load(pageIndex);
  }
}
