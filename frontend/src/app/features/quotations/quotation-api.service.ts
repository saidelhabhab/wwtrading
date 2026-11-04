import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import {
  CreateQuotationRequest,
  PageResponse,
  Quotation,
  UpdateQuotationRequest,
  UpdateQuotationStatusPayload
} from './quotation.models';

@Injectable({ providedIn: 'root' })
export class QuotationApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/quotations`;

  getAllQuotations(page = 0, size = 20, status?: string): Observable<PageResponse<Quotation>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status && status !== 'ALL') params = params.set('status', status);
    return this.http.get<PageResponse<Quotation>>(this.resourceUrl, { params });
  }

  list(page = 0, size = 20): Observable<PageResponse<Quotation>> {
    return this.getAllQuotations(page, size);
  }

  getQuotation(id: string): Observable<Quotation> {
    return this.http.get<Quotation>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  getQuotationPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' });
  }

  getById(id: string): Observable<Quotation> {
    return this.getQuotation(id);
  }

  updateQuotationStatus(id: string, payload: UpdateQuotationStatusPayload): Observable<Quotation> {
    return this.http.put<Quotation>(`${this.resourceUrl}/${encodeURIComponent(id)}/status`, payload);
  }

  getMyQuotations(page = 0, size = 20): Observable<PageResponse<Quotation>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Quotation>>(`${this.resourceUrl}/my-quotations`, { params });
  }

  cancelQuotation(id: string): Observable<Quotation> {
    return this.http.put<Quotation>(`${this.resourceUrl}/${encodeURIComponent(id)}/cancel`, {});
  }

  decide(id: string, status: 'ACCEPTED' | 'REJECTED'): Observable<Quotation> {
    return this.http.put<Quotation>(`${this.resourceUrl}/${encodeURIComponent(id)}/decision`, { status });
  }

  create(request: CreateQuotationRequest): Observable<Quotation> {
    let headers: { [header: string]: string } = {};
    if (request.idempotencyKey) {
      headers['Idempotency-Key'] = request.idempotencyKey;
    }
    return this.http.post<Quotation>(this.resourceUrl, request, { headers });
  }

  createQuotation(request: CreateQuotationRequest): Observable<Quotation> {
    return this.create(request);
  }

  update(id: string, request: UpdateQuotationRequest): Observable<Quotation> {
    return this.http.put<Quotation>(`${this.resourceUrl}/${encodeURIComponent(id)}`, request);
  }
}
