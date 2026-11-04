import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import {
  AdminResetPasswordPayload,
  AdminResetPasswordResponse,
  CreateStaffUserRequest,
  StaffUser,
  StaffUserResponse
} from './staff-user.models';

@Injectable({ providedIn: 'root' })
export class StaffUserApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/v1/users`;

  createStaffUser(request: CreateStaffUserRequest): Observable<StaffUserResponse> {
    return this.http.post<StaffUserResponse>(this.resourceUrl, request);
  }

  getStaffUsers(): Observable<readonly StaffUser[]> {
    return this.http.get<readonly StaffUser[]>(this.resourceUrl);
  }

  resetUserPassword(userId: string, payload: AdminResetPasswordPayload): Observable<AdminResetPasswordResponse> {
    return this.http.post<AdminResetPasswordResponse>(
      `${this.resourceUrl}/${encodeURIComponent(userId)}/reset-password`,
      payload
    );
  }
}
