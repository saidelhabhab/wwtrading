import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RUNTIME_CONFIG } from '../config/runtime-config';

export interface RegisterPayload {
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly phone: string;
  readonly password: string;
  readonly confirmPassword: string;
}

export interface ForgotPasswordPayload {
  readonly email: string;
}

export interface ChangePasswordPayload {
  readonly currentPassword: string;
  readonly newPassword: string;
  readonly confirmPassword: string;
}

export interface AuthApiResponse {
  readonly success: boolean;
  readonly message: string;
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);

  register(payload: RegisterPayload): Observable<AuthApiResponse> {
    return this.http.post<AuthApiResponse>(
      `${this.config.apiBaseUrl}/api/v1/auth/register`,
      payload
    );
  }

  forgotPassword(payload: ForgotPasswordPayload): Observable<AuthApiResponse> {
    return this.http.post<AuthApiResponse>(
      `${this.config.apiBaseUrl}/api/v1/auth/forgot-password`,
      payload
    );
  }

  changePassword(payload: ChangePasswordPayload): Observable<AuthApiResponse> {
    return this.http.post<AuthApiResponse>(
      `${this.config.apiBaseUrl}/api/v1/auth/change-password`,
      payload
    );
  }
}
