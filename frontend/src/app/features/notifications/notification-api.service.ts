import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { Notification, NotificationPage, UnreadCountResponse } from './notification.models';

/**
 * Centralized notification state + Phase 3 notification-service API client.
 *
 * - Ownership is resolved by the backend from the JWT subject (interceptor);
 *   no recipientSubject/userId is ever sent.
 * - The unread count is a shared signal so the topbar badge and the
 *   notification center stay synchronized without polling.
 */
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/notifications`;

  /** Shared unread count (0 when unknown/not loaded). */
  private readonly unreadCountState = signal(0);
  readonly unreadCount = this.unreadCountState.asReadonly();

  getNotifications(page = 0, size = 20, sort = 'createdAt,desc'): Observable<NotificationPage> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<NotificationPage>(this.resourceUrl, { params });
  }

  getUnreadCount(): Observable<number> {
    return this.http.get<UnreadCountResponse>(`${this.resourceUrl}/unread-count`).pipe(
      map((response) => response.unreadCount),
      tap((count) => this.setUnreadCount(count)),
      catchError(() => of(this.unreadCount()))
    );
  }

  markAsRead(id: string): Observable<Notification> {
    return this.http.patch<Notification>(`${this.resourceUrl}/${encodeURIComponent(id)}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.resourceUrl}/read-all`, {});
  }

  /** Local-state helpers so every screen updates the same count. */
  setUnreadCount(count: number): void {
    this.unreadCountState.set(Math.max(0, count));
  }

  incrementUnreadCount(): void {
    this.unreadCountState.update((count) => count + 1);
  }

  decrementUnreadCount(): void {
    this.unreadCountState.update((count) => Math.max(0, count - 1));
  }

  /** Call after the backend confirms mark-all-read. */
  markAllRead(): void {
    this.unreadCountState.set(0);
  }

  /** Re-fetch the count from the backend (on login, on tab focus, etc.). */
  refreshUnreadCount(): Observable<number> {
    return this.getUnreadCount();
  }

  /** Formats the badge: 0 hidden, 1–99 exact, 100+ → "99+". */
  static badgeLabel(count: number): string {
    if (count <= 0) return '';
    return count > 99 ? '99+' : String(count);
  }
}
