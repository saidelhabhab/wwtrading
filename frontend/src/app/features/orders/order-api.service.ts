import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, map } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import {
  ClaimGuestOrdersResponse,
  CreateGuestOrderRequest,
  CreateOrderRequest,
  CreatePaymentBankPayload,
  DeliverySettings,
  OrderNotificationDeliveryResponse,
  OrderResponse,
  OrderPricingResponse,
  PageResponse,
  PaymentBank,
  PreviewGuestOrderPricingRequest,
  PreviewOrderPricingRequest,
  PublicOrderTrackingResponse,
  UpdateDeliverySettingsPayload,
  UpdatePaymentBankPayload
} from './order.models';
import { OrderAnalyticsResponse as OrderAnalytics } from './order-analytics.models';

@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly baseUrl = this.config.apiBaseUrl.replace(/\/$/, '');
  private readonly resourceUrl = `${this.baseUrl}/api/orders`;
  private readonly notificationsUrl = `${this.baseUrl}/api/notifications`;
  private readonly adminBanksUrl = `${this.baseUrl}/api/admin/payment-banks`;
  private readonly adminDeliverySettingsUrl = `${this.baseUrl}/api/admin/delivery-settings`;
  private readonly analyticsUrl = `${this.baseUrl}/api/analytics/orders`;

  /**
   * Order analytics for a half-open range. The backend includes the whole `to` day.
   * Amounts are order value / paid revenue / recognized revenue, kept distinct.
   */
  getOrderAnalytics(from: string, to: string): Observable<OrderAnalytics> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<OrderAnalytics>(this.analyticsUrl, { params });
  }

  createOrder(request: CreateOrderRequest, idempotencyKey?: string): Observable<OrderResponse> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<OrderResponse>(this.resourceUrl, request, { headers });
  }

  previewOrderPricing(request: PreviewOrderPricingRequest): Observable<OrderPricingResponse> {
    return this.http.post<OrderPricingResponse>(`${this.resourceUrl}/pricing`, request);
  }

  previewGuestOrderPricing(request: PreviewGuestOrderPricingRequest): Observable<OrderPricingResponse> {
    return this.http.post<OrderPricingResponse>(`${this.resourceUrl}/guest/pricing`, request);
  }

  createGuestOrder(request: CreateGuestOrderRequest, idempotencyKey?: string): Observable<OrderResponse> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<OrderResponse>(`${this.resourceUrl}/guest`, request, { headers });
  }

  trackGuestOrder(token: string): Observable<PublicOrderTrackingResponse> {
    const headers = new HttpHeaders({ 'X-Tracking-Token': token });
    const params = new HttpParams().set('token', token);
    return this.http.get<PublicOrderTrackingResponse>(`${this.resourceUrl}/guest/track`, { headers, params });
  }

  uploadGuestTransferProof(token: string, file: File, installment?: number): Observable<PublicOrderTrackingResponse> {
    const formData = new FormData();
    formData.append('file', file);
    const headers = new HttpHeaders({ 'X-Tracking-Token': token });
    let params = new HttpParams().set('token', token);
    if (installment) {
      params = params.set('installment', String(installment));
    }
    return this.http.post<PublicOrderTrackingResponse>(
      `${this.resourceUrl}/guest/track/transfer-proof`,
      formData,
      { headers, params }
    );
  }

  selectGuestSecondInstallmentMethod(token: string, method: 'BANK_TRANSFER' | 'CASH'): Observable<PublicOrderTrackingResponse> {
    const headers = new HttpHeaders({ 'X-Tracking-Token': token });
    let params = new HttpParams().set('token', token);
    return this.http.post<PublicOrderTrackingResponse>(
      `${this.resourceUrl}/guest/track/second-installment/method`,
      { method },
      { headers, params }
    );
  }

  claimGuestOrders(): Observable<ClaimGuestOrdersResponse> {
    return this.http.post<ClaimGuestOrdersResponse>(`${this.resourceUrl}/claim-guest-orders`, null);
  }

  revokeTrackingToken(orderId: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/tracking-token/revoke`, null);
  }

  regenerateTrackingToken(orderId: string): Observable<{ readonly trackingUrl: string }> {
    return this.http.post<{ readonly trackingUrl: string }>(
      `${this.resourceUrl}/${encodeURIComponent(orderId)}/tracking-token/regenerate`,
      null
    );
  }

  getOrderNotificationDeliveries(orderId: string): Observable<OrderNotificationDeliveryResponse> {
    return this.http.get<OrderNotificationDeliveryResponse>(
      `${this.notificationsUrl}/orders/${encodeURIComponent(orderId)}/deliveries`
    );
  }

  retryOrderNotificationDeliveries(
    orderId: string,
    channel?: 'ALL' | 'EMAIL' | 'WHATSAPP'
  ): Observable<OrderNotificationDeliveryResponse> {
    let params = new HttpParams();
    if (channel) {
      params = params.set('channel', channel);
    }
    return this.http.post<OrderNotificationDeliveryResponse>(
      `${this.notificationsUrl}/orders/${encodeURIComponent(orderId)}/retry`,
      null,
      { params }
    );
  }

  resolveBankLogo(logoUrl?: string | null, bankNameOrRef?: string | null): string {
    const name = (bankNameOrRef || '').toLowerCase();
    if (logoUrl) {
      const trimmed = logoUrl.trim();
      if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('data:')) {
        return trimmed;
      }
      if (trimmed.startsWith('/assets/') || trimmed.startsWith('assets/')) {
        return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
      }
      const cleanPath = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
      if (cleanPath.startsWith('/api/')) {
        return `${this.baseUrl}${cleanPath}`;
      }
      return `${this.baseUrl}/api/orders/payment-banks/logos/${cleanPath.replace(/^\//, '')}`;
    }

    if (name.includes('cih')) return '/assets/banks/cih.svg';
    if (name.includes('attijari') || name.includes('atw') || name.includes('wafa')) return '/assets/banks/attijariwafa.svg';
    if (name.includes('bmce') || name.includes('africa') || name.includes('boa')) return '/assets/banks/bmce.svg';
    if (name.includes('populaire') || name.includes('bcp') || name.includes('chaabi')) return '/assets/banks/bcp.svg';
    return '/assets/banks/generic.svg';
  }

  private normalizeBank(bank: PaymentBank): PaymentBank {
    return {
      ...bank,
      logoUrl: this.resolveBankLogo(bank.logoUrl, bank.name || bank.reference)
    };
  }

  getPaymentBanks(): Observable<readonly PaymentBank[]> {
    return this.http.get<readonly PaymentBank[]>(`${this.resourceUrl}/payment-banks`).pipe(
      map((banks) => banks.map((b) => this.normalizeBank(b)))
    );
  }

  getDeliverySettings(): Observable<DeliverySettings> {
    return this.http.get<DeliverySettings>(`${this.resourceUrl}/delivery-settings`);
  }

  uploadTransferProof(orderId: string, file: File, installment?: number): Observable<OrderResponse> {
    const formData = new FormData();
    formData.append('file', file);
    let params = new HttpParams();
    if (installment) {
      params = params.set('installment', String(installment));
    }
    return this.http.post<OrderResponse>(
      `${this.resourceUrl}/${encodeURIComponent(orderId)}/transfer-proof`,
      formData,
      { params }
    );
  }

  selectSecondInstallmentMethod(orderId: string, method: 'BANK_TRANSFER' | 'CASH'): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(
      `${this.resourceUrl}/${encodeURIComponent(orderId)}/second-installment/method`,
      { method }
    );
  }

  getTransferProof(orderId: string, download = false, installment?: number): Observable<Blob> {
    let params = new HttpParams();
    if (download) {
      params = params.set('download', 'true');
    }
    if (installment) {
      params = params.set('installment', String(installment));
    }
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(orderId)}/transfer-proof`, {
      params,
      responseType: 'blob'
    });
  }

  getGuestTransferProof(token: string, download = false, installment?: number): Observable<Blob> {
    let params = new HttpParams().set('token', token);
    if (download) {
      params = params.set('download', 'true');
    }
    if (installment) {
      params = params.set('installment', String(installment));
    }
    const headers = new HttpHeaders({ 'X-Tracking-Token': token });
    return this.http.get(`${this.resourceUrl}/guest/track/transfer-proof`, {
      params,
      headers,
      responseType: 'blob'
    });
  }

  getMyOrders(page = 0, size = 10): Observable<PageResponse<OrderResponse>> {
    return this.http.get<PageResponse<OrderResponse>>(`${this.resourceUrl}/my-orders`, {
      params: { page, size }
    });
  }

  getAllOrders(page = 0, size = 20): Observable<PageResponse<OrderResponse>> {
    return this.http.get<PageResponse<OrderResponse>>(this.resourceUrl, {
      params: { page, size }
    });
  }

  getOrder(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}`);
  }

  getOrderPdf(orderId: string): Observable<Blob> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(orderId)}/pdf`, { responseType: 'blob' });
  }

  cancelOrder(orderId: string): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/cancel`, null);
  }

  confirmOrder(orderId: string): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/confirm`, null);
  }

  processOrder(orderId: string): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/process`, null);
  }

  markOrderReady(orderId: string): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/ready`, null);
  }

  completeOrder(orderId: string): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/complete`, null);
  }

  confirmPayment(orderId: string, amount?: number): Observable<OrderResponse> {
    const body = amount !== undefined ? { amount } : null;
    return this.http.patch<OrderResponse>(`${this.resourceUrl}/${encodeURIComponent(orderId)}/payment/confirm`, body);
  }

  // Admin Delivery Settings Endpoints
  adminGetDeliverySettings(): Observable<DeliverySettings> {
    return this.http.get<DeliverySettings>(this.adminDeliverySettingsUrl).pipe(
      catchError(() => this.getDeliverySettings())
    );
  }

  adminUpdateDeliverySettings(payload: UpdateDeliverySettingsPayload): Observable<DeliverySettings> {
    return this.http.put<DeliverySettings>(this.adminDeliverySettingsUrl, payload).pipe(
      catchError(() => this.http.put<DeliverySettings>(`${this.resourceUrl}/delivery-settings`, payload))
    );
  }

  // Admin Payment Bank Endpoints
  adminGetAllPaymentBanks(): Observable<readonly PaymentBank[]> {
    return this.http.get<readonly PaymentBank[]>(this.adminBanksUrl).pipe(
      map((banks) => banks.map((b) => this.normalizeBank(b)))
    );
  }

  adminCreatePaymentBank(payload: CreatePaymentBankPayload, logoFile?: File | null): Observable<PaymentBank> {
    const request$ = logoFile
      ? (() => {
          const formData = new FormData();
          formData.append('reference', payload.reference);
          formData.append('bankName', payload.bankName);
          formData.append('rib', payload.rib);
          formData.append('beneficiary', payload.beneficiary);
          if (payload.active !== undefined) {
            formData.append('active', String(payload.active));
          }
          formData.append('logo', logoFile);
          return this.http.post<PaymentBank>(this.adminBanksUrl, formData);
        })()
      : this.http.post<PaymentBank>(this.adminBanksUrl, payload);
    return request$.pipe(map((bank) => this.normalizeBank(bank)));
  }

  adminUpdatePaymentBank(id: string, payload: UpdatePaymentBankPayload, logoFile?: File | null): Observable<PaymentBank> {
    const request$ = logoFile
      ? (() => {
          const formData = new FormData();
          formData.append('reference', payload.reference);
          formData.append('bankName', payload.bankName);
          formData.append('rib', payload.rib);
          formData.append('beneficiary', payload.beneficiary);
          if (payload.active !== undefined) {
            formData.append('active', String(payload.active));
          }
          formData.append('logo', logoFile);
          return this.http.put<PaymentBank>(`${this.adminBanksUrl}/${encodeURIComponent(id)}`, formData);
        })()
      : this.http.put<PaymentBank>(`${this.adminBanksUrl}/${encodeURIComponent(id)}`, payload);
    return request$.pipe(map((bank) => this.normalizeBank(bank)));
  }

  adminTogglePaymentBankActive(id: string, active: boolean): Observable<PaymentBank> {
    return this.http.patch<PaymentBank>(`${this.adminBanksUrl}/${encodeURIComponent(id)}/active`, { active });
  }
}
