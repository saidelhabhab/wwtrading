export const QUOTATION_STATUSES = [
  'PENDING',
  'IN_REVIEW',
  'QUOTED',
  'ACCEPTED',
  'REJECTED',
  'CANCELLED',
  'DRAFT',
  'SENT',
  'EXPIRED'
] as const;

export type QuotationStatus = typeof QUOTATION_STATUSES[number];

export interface Quotation {
  readonly id: string;
  readonly customerReference: string;
  readonly serviceType: string;
  readonly requestedEquipment: string | null;
  readonly brandModel: string | null;
  readonly quantity: number;
  readonly description: string;
  readonly fullName: string;
  readonly company: string | null;
  readonly phone: string;
  readonly email: string;
  readonly city: string;
  readonly desiredTimeline: string | null;
  readonly estimatedBudget: number | null;
  readonly additionalMessage: string | null;
  readonly status: QuotationStatus;
  readonly proposedAmount: number | null;
  readonly commercialNotes: string | null;
  readonly subject: string;
  readonly requestDescription: string;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly lines?: readonly QuotationLine[];
  readonly subtotal?: number | null;
  readonly discount?: number | null;
  readonly deliveryFee?: number | null;
  readonly finalTotal?: number | null;
  readonly validUntil?: string | null;
}

export interface QuotationLine {
  readonly productId: string;
  readonly productReference: string;
  readonly productName: string;
  readonly catalogUnitPrice: number;
  readonly requestedQuantity: number;
  readonly proposedQuantity: number | null;
  readonly proposedUnitPrice: number | null;
  readonly proposedLineTotal: number | null;
}

export interface CreateQuotationRequest {
  readonly requestedLines?: readonly { readonly productId: string; readonly quantity: number }[];
  readonly serviceType?: string;
  readonly requestedEquipment?: string | null;
  readonly brandModel?: string | null;
  readonly quantity?: number;
  readonly description?: string;
  readonly fullName?: string;
  readonly company?: string | null;
  readonly phone?: string;
  readonly email?: string;
  readonly city?: string;
  readonly desiredTimeline?: string | null;
  readonly estimatedBudget?: number | null;
  readonly additionalMessage?: string | null;
  readonly customerReference?: string;
  readonly subject?: string;
  readonly requestDescription?: string;
  readonly notes?: string | null;
  readonly idempotencyKey?: string | null;
}

export interface UpdateQuotationRequest {
  readonly subject: string;
  readonly requestDescription: string;
  readonly notes?: string | null;
}

export interface UpdateQuotationStatusPayload {
  readonly status: QuotationStatus;
  readonly proposedAmount?: number | null;
  readonly commercialNotes?: string | null;
  readonly proposedLines?: readonly {
    readonly productId: string;
    readonly quantity: number;
    readonly proposedUnitPrice: number;
  }[];
  readonly discount?: number | null;
  readonly deliveryFee?: number | null;
  readonly validUntil?: string | null;
}

export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly number: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly empty: boolean;
}

export function getQuotationStatusLabel(status: QuotationStatus | string): string {
  switch (status) {
    case 'PENDING': return 'En attente';
    case 'IN_REVIEW': return "En cours d'étude";
    case 'QUOTED': return 'Devis proposé';
    case 'ACCEPTED': return 'Accepté';
    case 'REJECTED': return 'Refusé';
    case 'CANCELLED': return 'Annulé';
    case 'DRAFT': return 'Brouillon';
    case 'SENT': return 'Envoyé';
    case 'EXPIRED': return 'Expiré';
    default: return status;
  }
}

export function getServiceTypeLabel(serviceType: string): string {
  switch (serviceType) {
    case 'VENTE': return 'Vente de machines';
    case 'LOCATION': return 'Location';
    case 'MAINTENANCE': return 'Maintenance';
    case 'TONERS': return 'Toners & consommables';
    case 'CONSOMMABLES': return 'Consommables';
    case 'PIECES': return 'Pièces de rechange';
    case 'SUPPORT': return 'Support technique';
    default: return serviceType || 'Général';
  }
}
