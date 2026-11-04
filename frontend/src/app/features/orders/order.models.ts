export type DeliveryMethod = 'DELIVERY' | 'PICKUP';
export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'PROCESSING' | 'READY' | 'COMPLETED' | 'CANCELLED';

export type PaymentMethod = 'CASH_ON_DELIVERY' | 'BANK_TRANSFER' | 'SPLIT_50_50' | 'ONLINE_PAYMENT' | 'PAY_ON_PICKUP';
export type CheckoutPaymentMethod = 'BANK_TRANSFER' | 'SPLIT_50_50' | 'PAY_ON_PICKUP';
export type PaymentPlan = 'FULL_BANK_TRANSFER' | 'SPLIT_50_50' | 'CASH_ON_DELIVERY' | 'PAY_ON_PICKUP';
export type PaymentStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID';

export interface CreateOrderRequest {
  readonly addressId?: string | null;
  readonly deliveryMethod: DeliveryMethod;
  readonly paymentMethod: CheckoutPaymentMethod;
  readonly bankReference?: string | null;
  readonly quotationId?: string;
  readonly idempotencyKey?: string | null;
}

export interface GuestOrderItemRequest {
  readonly productReference: string;
  readonly quantity: number;
}

export interface PreviewGuestOrderPricingRequest {
  readonly items: readonly GuestOrderItemRequest[];
  readonly deliveryMethod: DeliveryMethod;
  readonly city?: string | null;
}

export interface CreateGuestOrderRequest {
  readonly fullName: string;
  readonly email: string;
  readonly phone: string;
  readonly company?: string | null;
  readonly addressLine1?: string | null;
  readonly addressLine2?: string | null;
  readonly city?: string | null;
  readonly postalCode?: string | null;
  readonly country?: string | null;
  readonly deliveryMethod: DeliveryMethod;
  readonly paymentMethod: CheckoutPaymentMethod;
  readonly bankReference?: string | null;
  readonly items: readonly GuestOrderItemRequest[];
  readonly idempotencyKey?: string | null;
}

export interface PublicOrderTrackingItem {
  readonly productReference: string;
  readonly productName?: string | null;
  readonly quantity: number;
  readonly unitPrice: number;
  readonly subtotal: number;
}

export interface PublicOrderTrackingResponse {
  readonly orderNumber: string;
  readonly status: OrderStatus;
  readonly paymentStatus: PaymentStatus;
  readonly paymentMethod: PaymentMethod;
  readonly paymentPlan?: PaymentPlan | null;
  readonly deliveryMethod: DeliveryMethod;
  readonly deliveryArea?: string | null;
  readonly deliveryFeeWaivedReason?: string | null;
  readonly subtotal: number;
  readonly discount: number;
  readonly deliveryFee: number;
  readonly total: number;
  readonly amountPaid: number;
  readonly upfrontPaymentAmount: number;
  readonly remainingDeliveryAmount: number;
  readonly outstandingBalance: number;
  readonly paymentBankName?: string | null;
  readonly paymentBankRib?: string | null;
  readonly paymentBankBeneficiary?: string | null;
  readonly paymentBankLogoUrl?: string | null;
  readonly transferProofSubmitted: boolean;
  readonly secondTransferProofSubmitted?: boolean;
  readonly secondInstallmentPaymentMethod?: 'BANK_TRANSFER' | 'CASH' | null;
  readonly firstInstallmentConfirmedAt?: string | null;
  readonly secondInstallmentConfirmedAt?: string | null;
  readonly firstTransferProofSubmittedAt?: string | null;
  readonly secondTransferProofSubmittedAt?: string | null;
  readonly shippingCity?: string | null;
  readonly recipientFirstName?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly items: readonly PublicOrderTrackingItem[];
}

export interface ClaimGuestOrdersResponse {
  readonly linkedCount: number;
  readonly linkedOrderIds: readonly string[];
  readonly linkedOrderNumbers: readonly string[];
}

export interface NotificationDeliveryItem {
  readonly notificationId: string;
  readonly type: string;
  readonly title: string;
  readonly recipientEmail?: string | null;
  readonly recipientPhone?: string | null;
  readonly createdAt: string;
  readonly emailStatus?: string | null;
  readonly emailAttempts: number;
  readonly emailSentAt?: string | null;
  readonly emailLastError?: string | null;
  readonly whatsAppStatus?: string | null;
  readonly whatsAppAttempts: number;
  readonly whatsAppProviderMessageId?: string | null;
  readonly whatsAppSentAt?: string | null;
  readonly whatsAppLastError?: string | null;
}

export interface OrderNotificationDeliveryResponse {
  readonly orderId: string;
  readonly overallEmailStatus: string;
  readonly overallWhatsAppStatus: string;
  readonly lastEmailError?: string | null;
  readonly lastWhatsAppError?: string | null;
  readonly deliveries: readonly NotificationDeliveryItem[];
}

export interface PreviewOrderPricingRequest {
  readonly addressId?: string | null;
  readonly deliveryMethod: DeliveryMethod;
  readonly quotationId?: string;
}

export interface OrderPricingResponse {
  readonly subtotal: number;
  readonly deliveryFee: number;
  readonly total: number;
  readonly upfrontPaymentAmount?: number;
  readonly remainingDeliveryAmount?: number;
  readonly freeDeliveryThreshold?: number;
  readonly deliveryFeesEnabled?: boolean;
  readonly deliveryFeeWaivedReason?: string | null;
  readonly deliveryArea?: string | null;
}

export interface DeliverySettings {
  readonly feesEnabled: boolean;
  readonly kenitraAreaFee: number;
  readonly casablancaFee: number;
  readonly otherAreasFee: number;
  readonly freeDeliveryThreshold: number;
  readonly kenitraSurroundingAreas?: readonly string[];
  readonly updatedAt?: string;
}

export interface UpdateDeliverySettingsPayload {
  readonly feesEnabled: boolean;
  readonly kenitraAreaFee?: number;
  readonly casablancaFee?: number;
  readonly otherAreasFee?: number;
  readonly freeDeliveryThreshold?: number;
}

export interface PaymentBank {
  readonly id?: string;
  readonly reference: string;
  readonly name: string;
  readonly rib: string;
  readonly beneficiary: string | null;
  readonly logoUrl: string | null;
  readonly active?: boolean;
  readonly createdAt?: string;
  readonly updatedAt?: string;
}

export interface CreatePaymentBankPayload {
  readonly reference: string;
  readonly bankName: string;
  readonly rib: string;
  readonly beneficiary: string;
  readonly logoUrl?: string | null;
  readonly active?: boolean;
}

export interface UpdatePaymentBankPayload {
  readonly reference: string;
  readonly bankName: string;
  readonly rib: string;
  readonly beneficiary: string;
  readonly logoUrl?: string | null;
  readonly active?: boolean;
}

export interface ShippingAddressSnapshot {
  readonly firstName: string;
  readonly lastName: string;
  readonly company: string | null;
  readonly phone: string | null;
  readonly addressLine1: string;
  readonly addressLine2: string | null;
  readonly city: string;
  readonly postalCode: string | null;
  readonly country: string;
}

export interface OrderResponse {
  readonly id: string;
  readonly orderNumber?: string | null;
  readonly customerReference: string;
  readonly customerSubject?: string | null;
  readonly guestOrder?: boolean;
  readonly contactFullName?: string | null;
  readonly contactEmail?: string | null;
  readonly contactPhone?: string | null;
  readonly linkedAt?: string | null;
  readonly shippingAddress: ShippingAddressSnapshot | null;
  readonly deliveryMethod: DeliveryMethod;
  readonly deliveryArea?: string | null;
  readonly deliveryFeeWaivedReason?: string | null;
  readonly paymentMethod: PaymentMethod;
  readonly paymentPlan?: PaymentPlan;
  readonly paymentStatus: PaymentStatus;
  readonly items: readonly OrderItem[];
  readonly total: number;
  readonly subtotal: number;
  readonly deliveryFee: number;
  readonly amountPaid?: number;
  readonly outstandingBalance?: number;
  readonly upfrontPaymentAmount?: number;
  readonly remainingDeliveryAmount?: number;
  readonly firstInstallmentConfirmedAt?: string | null;
  readonly secondInstallmentConfirmedAt?: string | null;
  readonly paidAt?: string | null;
  readonly discount?: number;
  readonly quotationId?: string | null;
  readonly selectedBankReference: string | null;
  readonly paymentBankId?: string | null;
  readonly paymentBankName?: string | null;
  readonly paymentBankRib?: string | null;
  readonly paymentBankBeneficiary?: string | null;
  readonly paymentBankLogoUrl?: string | null;
  readonly transferProofReference: string | null;
  readonly secondTransferProofReference?: string | null;
  readonly secondInstallmentPaymentMethod?: 'BANK_TRANSFER' | 'CASH' | null;
  readonly secondInstallmentConfirmedBy?: string | null;
  readonly firstTransferProofSubmittedAt?: string | null;
  readonly secondTransferProofSubmittedAt?: string | null;
  readonly status: OrderStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly trackingToken?: string | null;
  readonly trackingUrl?: string | null;
}

export function orderStatusLabel(status: OrderStatus, deliveryMethod: DeliveryMethod): string {
  switch (status) {
    case 'DRAFT': return 'En attente';
    case 'CONFIRMED': return 'Confirmée';
    case 'PROCESSING': return 'En traitement';
    case 'READY': return deliveryMethod === 'DELIVERY' ? 'Prête pour livraison' : 'Prête pour retrait';
    case 'COMPLETED': return deliveryMethod === 'DELIVERY' ? 'Livrée' : 'Retirée';
    case 'CANCELLED': return 'Annulée';
  }
}

export function paymentStatusLabel(status: PaymentStatus): string {
  switch (status) {
    case 'PAID': return 'Payé';
    case 'PARTIALLY_PAID': return 'Partiellement payé (50 %)';
    case 'UNPAID':
    default: return 'Non payé';
  }
}

export function paymentPlanLabel(
  paymentPlan: PaymentPlan | null | undefined,
  paymentMethod: PaymentMethod,
  deliveryMethod: DeliveryMethod
): string {
  if (paymentPlan === 'SPLIT_50_50' || paymentMethod === 'SPLIT_50_50') {
    return deliveryMethod === 'PICKUP'
      ? 'Acompte de 50 % par virement bancaire'
      : '50 % à la commande (virement) + 50 % à la livraison';
  }
  if (paymentPlan === 'FULL_BANK_TRANSFER' || paymentMethod === 'BANK_TRANSFER') {
    return 'Virement bancaire intégral (100 %)';
  }
  if (paymentPlan === 'PAY_ON_PICKUP' || paymentMethod === 'PAY_ON_PICKUP') {
    return 'Paiement sur place';
  }
  return deliveryMethod === 'PICKUP' ? 'Paiement sur place' : 'Paiement à la livraison';
}

export function deliveryAreaLabel(area?: string | null): string | null {
  switch (area) {
    case 'KENITRA_AREA': return 'Kénitra et environs';
    case 'CASABLANCA': return 'Casablanca';
    case 'OTHER': return 'Autres villes et régions';
    case 'PICKUP': return 'Retrait sur place';
    default: return null;
  }
}

export function deliveryFeeWaivedLabel(reason?: string | null, threshold?: number | null): string | null {
  switch (reason) {
    case 'ADMIN_DISABLED': return 'Frais de livraison désactivés par l’administrateur';
    case 'FREE_THRESHOLD':
      return threshold !== undefined && threshold !== null
        ? `Livraison offerte (commande > ${threshold} DH)`
        : 'Livraison offerte (seuil de gratuité atteint)';
    case 'PICKUP': return 'Retrait sur place sans frais';
    default: return null;
  }
}

export interface OrderItem {
  readonly id: string;
  readonly productReference: string;
  readonly productName?: string | null;
  readonly quantity: number;
  readonly unitPrice: number;
  readonly subtotal: number;
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
