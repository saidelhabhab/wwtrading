import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { from, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { RUNTIME_CONFIG } from '../config/runtime-config';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const config = inject(RUNTIME_CONFIG);
  if (!isConfiguredApiUrl(request.url, config.apiBaseUrl) || request.headers.has('Authorization')) {
    return next(request);
  }

  return from(auth.getValidAccessToken()).pipe(
    switchMap((token) => token
      ? next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
      : next(request))
  );
};

function isConfiguredApiUrl(requestUrl: string, apiBaseUrl: string): boolean {
  try {
    const api = new URL(apiBaseUrl, location.origin);
    const request = new URL(requestUrl, location.origin);
    const basePath = api.pathname.replace(/\/$/, '');
    return request.origin === api.origin
      && (basePath === '' || request.pathname === basePath || request.pathname.startsWith(`${basePath}/`));
  } catch {
    return false;
  }
}
