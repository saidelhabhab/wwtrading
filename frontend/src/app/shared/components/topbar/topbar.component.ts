import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnInit,
  Output,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { NotificationApiService } from '../../../features/notifications/notification-api.service';
import {
  isSafeActionUrl,
  safeNavigateActionUrl,
} from '../../../features/notifications/notification-navigation.util';
import {
  Notification,
  NOTIFICATION_TYPE_ICONS,
} from '../../../features/notifications/notification.models';

@Component({
  selector: 'wwt-topbar',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
})
export class TopbarComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly notificationSvc = inject(NotificationApiService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly elRef = inject(ElementRef);

  protected readonly dashboardRoute = computed(() =>
    AuthService.resolveDashboardRoute(this.auth.user()?.roles)
  );
  protected readonly accountRoute = computed(() =>
    AuthService.resolveAccountRoute(this.auth.user()?.roles)
  );
  protected readonly searchPlaceholder = computed(() => {
    const role = this.auth.roles()[0];
    if (role === 'TECHNICIAN') return 'Rechercher une machine, une intervention...';
    if (role === 'COMMERCIAL') return 'Rechercher un client, une commande, un devis...';
    if (role === 'CLIENT') return 'Rechercher une commande, une machine...';
    return 'Rechercher un client, une commande, un produit...';
  });

  @Input() title = 'Tableau de bord';
  @Output() menuToggled = new EventEmitter<void>();
  @Output() logoutRequested = new EventEmitter<void>();

  protected userMenuOpen = false;
  protected notificationsOpen = false;
  protected previewItems: Notification[] = [];
  protected previewLoading = false;
  protected markingAll = false;
  protected readonly typeIcons = NOTIFICATION_TYPE_ICONS;

  /** Shared unread count from the centralized signal. */
  protected readonly unreadCount = this.notificationSvc.unreadCount;
  protected readonly badgeLabel = computed(() =>
    NotificationApiService.badgeLabel(this.unreadCount())
  );

  ngOnInit(): void {
    // Eagerly hydrate the unread count so the badge is correct from the first render.
    firstValueFrom(this.notificationSvc.refreshUnreadCount()).catch(() => {/* ignore */});
  }

  protected toggleUserMenu(): void {
    this.userMenuOpen = !this.userMenuOpen;
    if (this.userMenuOpen) this.notificationsOpen = false;
  }

  protected toggleNotifications(): void {
    this.notificationsOpen = !this.notificationsOpen;
    if (this.userMenuOpen) this.userMenuOpen = false;
    if (this.notificationsOpen) this.loadPreview();
  }

  protected closeUserMenu(): void { this.userMenuOpen = false; }

  protected closeNotifications(): void { this.notificationsOpen = false; }

  protected logout(): void {
    this.closeUserMenu();
    this.logoutRequested.emit();
    void this.auth.logout();
  }

  protected onNotificationClick(item: Notification): void {
    if (!item.readAt) {
      this.markAsRead(item);
    }
    this.closeNotifications();
    if (isSafeActionUrl(item.actionUrl)) {
      void safeNavigateActionUrl(this.router, item.actionUrl);
    }
  }

  protected markAsRead(item: Notification): void {
    if (item.readAt) return;
    firstValueFrom(this.notificationSvc.markAsRead(item.id)).then(() => {
      this.notificationSvc.decrementUnreadCount();
      // Mutate preview in place so the item shows as read immediately.
      const idx = this.previewItems.findIndex((n) => n.id === item.id);
      if (idx !== -1) {
        this.previewItems = [
          ...this.previewItems.slice(0, idx),
          { ...item, readAt: new Date().toISOString() },
          ...this.previewItems.slice(idx + 1),
        ];
        this.cdr.markForCheck();
      }
    }).catch(() => {/* ignore */});
  }

  protected markAllAsRead(): void {
    if (this.markingAll) return;
    this.markingAll = true;
    firstValueFrom(this.notificationSvc.markAllAsRead()).then(() => {
      this.notificationSvc.markAllRead();
      this.previewItems = this.previewItems.map((n) => ({
        ...n,
        readAt: n.readAt ?? new Date().toISOString(),
      }));
      this.markingAll = false;
      this.cdr.markForCheck();
    }).catch(() => { this.markingAll = false; this.cdr.markForCheck(); });
  }

  /** Close dropdowns when the user clicks outside this host element. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!(this.elRef.nativeElement as HTMLElement).contains(event.target as Node)) {
      if (this.notificationsOpen) { this.notificationsOpen = false; this.cdr.markForCheck(); }
      if (this.userMenuOpen) { this.userMenuOpen = false; this.cdr.markForCheck(); }
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.notificationsOpen) { this.notificationsOpen = false; this.cdr.markForCheck(); }
    if (this.userMenuOpen) { this.userMenuOpen = false; this.cdr.markForCheck(); }
  }

  private loadPreview(): void {
    this.previewLoading = true;
    this.cdr.markForCheck();
    firstValueFrom(this.notificationSvc.getNotifications(0, 5)).then((page) => {
      this.previewItems = [...page.content];
      this.previewLoading = false;
      this.cdr.markForCheck();
    }).catch(() => {
      this.previewLoading = false;
      this.cdr.markForCheck();
    });
  }
}
