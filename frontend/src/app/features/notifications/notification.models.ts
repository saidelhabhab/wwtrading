/**
 * Frontend mirror of the notification-service Phase 3 API contracts
 * (backend/services/notification-service …/api/NotificationResponse.java).
 *
 * Ownership is resolved server-side from the JWT subject: the frontend never
 * sends recipientSubject/userId.
 */

export enum NotificationType {
  WELCOME = "WELCOME",
  LOGIN = "LOGIN",
  ANNOUNCEMENT = "ANNOUNCEMENT",
  PRODUCT_CREATED = "PRODUCT_CREATED",
  ORDER_CREATED = "ORDER_CREATED",
  ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED",
  PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED",
  QUOTATION_CREATED = "QUOTATION_CREATED",
  QUOTATION_STATUS_CHANGED = "QUOTATION_STATUS_CHANGED",
  MAINTENANCE_CREATED = "MAINTENANCE_CREATED",
  MAINTENANCE_ASSIGNED = "MAINTENANCE_ASSIGNED",
  MAINTENANCE_STATUS_CHANGED = "MAINTENANCE_STATUS_CHANGED",
  MAINTENANCE_COMPLETED = "MAINTENANCE_COMPLETED",
  SUPPORT_MESSAGE = "SUPPORT_MESSAGE",
  SUPPORT_REPLY = "SUPPORT_REPLY",
  SYSTEM = "SYSTEM",
}

export enum NotificationChannel {
  IN_APP = "IN_APP",
  EMAIL = "EMAIL",
  BOTH = "BOTH",
}

export interface Notification {
  readonly id: string;
  readonly recipientSubject: string;
  readonly recipientEmail?: string;
  readonly type: NotificationType;
  readonly channel: NotificationChannel;
  readonly title: string;
  readonly message: string;
  readonly actionUrl: string | null;
  readonly entityType: string | null;
  readonly entityId: string | null;
  readonly readAt: string | null;
  readonly createdAt: string;
  readonly expiresAt?: string | null;
  readonly metadata?: Record<string, unknown> | null;
}

/** Spring Data page shape, identical to the other feature PageResponse models. */
export interface NotificationPage {
  readonly content: readonly Notification[];
  readonly number: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface UnreadCountResponse {
  readonly unreadCount: number;
}

/** Material icon (existing icon library) per notification type. */
export const NOTIFICATION_TYPE_ICONS: Record<NotificationType, string> = {
  [NotificationType.WELCOME]: "person_add",
  [NotificationType.LOGIN]: "login",
  [NotificationType.ANNOUNCEMENT]: "campaign",
  [NotificationType.PRODUCT_CREATED]: "inventory_2",
  [NotificationType.ORDER_CREATED]: "shopping_cart",
  [NotificationType.ORDER_STATUS_CHANGED]: "local_shipping",
  [NotificationType.PAYMENT_STATUS_CHANGED]: "payments",
  [NotificationType.QUOTATION_CREATED]: "request_quote",
  [NotificationType.QUOTATION_STATUS_CHANGED]: "description",
  [NotificationType.MAINTENANCE_CREATED]: "build",
  [NotificationType.MAINTENANCE_ASSIGNED]: "engineering",
  [NotificationType.MAINTENANCE_STATUS_CHANGED]: "build_circle",
  [NotificationType.MAINTENANCE_COMPLETED]: "task_alt",
  [NotificationType.SUPPORT_MESSAGE]: "support_agent",
  [NotificationType.SUPPORT_REPLY]: "forum",
  [NotificationType.SYSTEM]: "notifications",
};
