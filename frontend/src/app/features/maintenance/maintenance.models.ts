export type MaintenancePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type MaintenanceStatus =
  | 'OPEN'
  | 'SUBMITTED'
  | 'ASSIGNED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'IN_PROGRESS'
  | 'WAITING_FOR_PART'
  | 'COMPLETION_PENDING_CONFIRMATION'
  | 'COMPLETED'
  | 'CANCELLED';

export type PartRequestStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';

export interface MaintenanceResponse {
  readonly id: string;
  readonly customerReference: string;
  readonly clientReference: string | null;
  readonly productReference: string | null;
  readonly serialNumber: string | null;
  readonly priority: MaintenancePriority;
  readonly technicianId: string | null;
  readonly subject: string;
  readonly description: string;
  readonly diagnosis: string | null;
  readonly interventionNotes: string | null;
  readonly completionSummary: string | null;
  readonly status: MaintenanceStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly assignedAt: string | null;
  readonly acceptedAt: string | null;
  readonly rejectedAt: string | null;
  readonly startedAt: string | null;
  readonly completedAt: string | null;
  readonly clientConfirmedAt: string | null;
}

export interface MaintenanceAttachmentResponse {
  readonly id: string;
  readonly filename: string;
  readonly contentType: string;
  readonly size: number;
  readonly contentUrl: string;
  readonly createdAt: string;
}

export interface MaintenancePartRequestResponse {
  readonly id: string;
  readonly maintenanceId: string;
  readonly partName: string;
  readonly partReference: string | null;
  readonly quantity: number;
  readonly reason: string | null;
  readonly status: PartRequestStatus;
  readonly requestedBy: string;
  readonly requestedAt: string;
  readonly reviewedBy: string | null;
  readonly reviewedAt: string | null;
}

export interface CreateMaintenanceRequest {
  readonly customerReference?: string | null;
  readonly clientReference?: string | null;
  readonly productReference?: string | null;
  readonly serialNumber?: string | null;
  readonly priority?: MaintenancePriority;
  readonly subject: string;
  readonly description: string;
}

export interface UpdateInterventionPayload {
  readonly diagnosis?: string | null;
  readonly interventionNotes?: string | null;
}

export interface CreatePartRequestPayload {
  readonly partName: string;
  readonly partReference?: string | null;
  readonly quantity: number;
  readonly reason?: string | null;
}

export interface SubmitCompletionPayload {
  readonly completionSummary?: string | null;
  readonly interventionNotes?: string | null;
}

export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly number: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export const PRIORITY_LABELS: Record<MaintenancePriority, string> = {
  LOW: 'Basse',
  MEDIUM: 'Normale',
  HIGH: 'Haute',
  CRITICAL: 'Critique',
};

export const STATUS_LABELS: Record<MaintenanceStatus, string> = {
  OPEN: 'Ouvert',
  SUBMITTED: 'Soumis',
  ASSIGNED: 'Assigné',
  ACCEPTED: 'Accepté',
  REJECTED: 'Refusé',
  IN_PROGRESS: 'En cours',
  WAITING_FOR_PART: 'En attente de pièce',
  COMPLETION_PENDING_CONFIRMATION: 'En attente de confirmation client',
  COMPLETED: 'Terminé',
  CANCELLED: 'Annulé',
};

export const PART_REQUEST_STATUS_LABELS: Record<PartRequestStatus, string> = {
  REQUESTED: 'En attente',
  APPROVED: 'Approuvé',
  REJECTED: 'Refusé',
};

export interface TechnicianDirectoryUser {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
}
