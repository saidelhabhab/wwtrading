export type SupportStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED';
export type SupportSenderRole = 'ADMIN' | 'MANAGER' | 'COMMERCIAL' | 'CLIENT';

export interface SupportConversation {
  readonly id: string;
  readonly subject: string;
  readonly status: SupportStatus;
  readonly clientKeycloakSubject: string;
  readonly assignedAgentKeycloakSubject: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly closedAt: string | null;
}

export interface SupportAttachment {
  readonly id: string;
  readonly messageId: string;
  readonly originalFilename: string;
  readonly contentType: string;
  readonly size: number;
  readonly contentUrl: string;
  readonly createdAt: string;
}

export interface SupportMessage {
  readonly id: string;
  readonly conversationId: string;
  readonly senderKeycloakSubject: string;
  readonly senderRole: SupportSenderRole;
  readonly content: string;
  readonly createdAt: string;
  readonly attachments: readonly SupportAttachment[];
}

export interface SupportPage<T> {
  readonly content: readonly T[];
  readonly number: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface CreateSupportConversationRequest {
  readonly subject: string;
  readonly message: string;
}

export interface SendSupportMessageRequest {
  readonly content: string;
}

export const SUPPORT_STATUS_LABELS: Readonly<Record<SupportStatus, string>> = {
  OPEN: 'Ouverte',
  IN_PROGRESS: 'En cours',
  CLOSED: 'Fermée'
};
