import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import {
  CreateMaintenanceRequest,
  CreatePartRequestPayload,
  MaintenanceAttachmentResponse,
  MaintenancePartRequestResponse,
  MaintenanceResponse,
  PageResponse,
  SubmitCompletionPayload,
  TechnicianDirectoryUser,
  UpdateInterventionPayload,
} from './maintenance.models';

@Injectable({ providedIn: 'root' })
export class MaintenanceApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly baseUrl = this.config.apiBaseUrl.replace(/\/$/, '');
  private readonly resourceUrl = `${this.baseUrl}/api/maintenance`;

  /** CLIENT: create a maintenance request. */
  create(request: CreateMaintenanceRequest): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(this.resourceUrl, request);
  }

  uploadAttachments(id: string, files: readonly File[]): Observable<readonly MaintenanceAttachmentResponse[]> {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    return this.http.post<readonly MaintenanceAttachmentResponse[]>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/attachments`,
      formData
    );
  }

  getAttachments(id: string): Observable<readonly MaintenanceAttachmentResponse[]> {
    return this.http.get<readonly MaintenanceAttachmentResponse[]>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/attachments`
    );
  }

  getAttachmentContent(maintenanceId: string, attachmentId: string): Observable<Blob> {
    return this.http.get(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/attachments/${encodeURIComponent(attachmentId)}/content`,
      { responseType: 'blob' }
    );
  }

  /** CLIENT: list own maintenance requests. */
  getMyRequests(page = 0, size = 20): Observable<PageResponse<MaintenanceResponse>> {
    return this.http.get<PageResponse<MaintenanceResponse>>(
      `${this.resourceUrl}/my-requests`,
      { params: { page, size } }
    );
  }

  /**
   * TECHNICIAN: list only maintenance requests assigned to the authenticated technician.
   * Server-side filtering by JWT subject.
   */
  getMyAssignments(page = 0, size = 20): Observable<PageResponse<MaintenanceResponse>> {
    return this.http.get<PageResponse<MaintenanceResponse>>(
      `${this.resourceUrl}/my-assignments`,
      { params: { page, size } }
    );
  }

  /** ADMIN / MANAGER / COMMERCIAL / TECHNICIAN: list all maintenance requests. */
  getAllRequests(page = 0, size = 20): Observable<PageResponse<MaintenanceResponse>> {
    return this.http.get<PageResponse<MaintenanceResponse>>(this.resourceUrl, {
      params: { page, size },
    });
  }

  /** Get a single maintenance request by ID. */
  getById(id: string): Observable<MaintenanceResponse> {
    return this.http.get<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}`
    );
  }

  /** TECHNICIAN: accept an assigned maintenance request (ASSIGNED → ACCEPTED). */
  accept(id: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/accept`,
      {}
    );
  }

  /** TECHNICIAN: reject an assigned maintenance request (ASSIGNED → REJECTED). */
  reject(id: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/reject`,
      {}
    );
  }

  /** TECHNICIAN: start intervention (ACCEPTED → IN_PROGRESS). */
  start(id: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/start`,
      {}
    );
  }

  /** TECHNICIAN: update diagnosis and intervention notes during intervention (status: IN_PROGRESS). */
  updateIntervention(id: string, payload: UpdateInterventionPayload): Observable<MaintenanceResponse> {
    return this.http.patch<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/intervention`,
      payload
    );
  }

  // ── Phase T4: Spare Part Requests ──────────────────────────────────────────

  /** TECHNICIAN: create a spare part request for maintenance. */
  createPartRequest(maintenanceId: string, payload: CreatePartRequestPayload): Observable<MaintenancePartRequestResponse> {
    return this.http.post<MaintenancePartRequestResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/parts`,
      payload
    );
  }

  /** List spare part requests for a maintenance request. */
  getPartRequests(maintenanceId: string): Observable<readonly MaintenancePartRequestResponse[]> {
    return this.http.get<readonly MaintenancePartRequestResponse[]>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/parts`
    );
  }

  /** MANAGER / ADMIN: approve a spare part request. */
  approvePartRequest(maintenanceId: string, partRequestId: string): Observable<MaintenancePartRequestResponse> {
    return this.http.post<MaintenancePartRequestResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/parts/${encodeURIComponent(partRequestId)}/approve`,
      {}
    );
  }

  /** MANAGER / ADMIN: reject a spare part request. */
  rejectPartRequest(maintenanceId: string, partRequestId: string): Observable<MaintenancePartRequestResponse> {
    return this.http.post<MaintenancePartRequestResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/parts/${encodeURIComponent(partRequestId)}/reject`,
      {}
    );
  }

  /** TECHNICIAN: place maintenance in WAITING_FOR_PART for an approved part request. */
  waitOnPart(maintenanceId: string, partRequestId: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/parts/${encodeURIComponent(partRequestId)}/wait`,
      {}
    );
  }

  /** TECHNICIAN: resume maintenance intervention (WAITING_FOR_PART → IN_PROGRESS). */
  resumeFromPartWait(maintenanceId: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/resume`,
      {}
    );
  }

  // ── Phase T5: Completion & Client Confirmation ─────────────────────────────

  /** TECHNICIAN: submit completion (IN_PROGRESS → COMPLETION_PENDING_CONFIRMATION). */
  submitCompletion(id: string, payload?: SubmitCompletionPayload): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/submit-completion`,
      payload ?? {}
    );
  }

  /** CLIENT / MANAGER / ADMIN: confirm completion (COMPLETION_PENDING_CONFIRMATION → COMPLETED). */
  confirmCompletion(id: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/confirm-completion`,
      {}
    );
  }

  // ───────────────────────────────────────────────────────────────────────────

  /** MANAGER / ADMIN: assign or reassign a technician. */
  assignTechnician(maintenanceId: string, technicianId: string): Observable<MaintenanceResponse> {
    return this.http.put<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/technician/${encodeURIComponent(technicianId)}`,
      {}
    );
  }

  /** MANAGER / ADMIN: remove assigned technician. */
  unassignTechnician(maintenanceId: string): Observable<MaintenanceResponse> {
    return this.http.delete<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(maintenanceId)}/technician`
    );
  }

  /** MANAGER / ADMIN: cancel a maintenance request. */
  cancel(id: string): Observable<MaintenanceResponse> {
    return this.http.post<MaintenanceResponse>(
      `${this.resourceUrl}/${encodeURIComponent(id)}/cancel`,
      {}
    );
  }

  /** MANAGER / ADMIN: list technicians for maintenance assignment. */
  getTechnicians(): Observable<readonly TechnicianDirectoryUser[]> {
    return this.http.get<readonly TechnicianDirectoryUser[]>(
      `${this.baseUrl}/api/v1/users`,
      { params: { role: 'TECHNICIAN' } }
    );
  }
}
