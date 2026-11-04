import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';

import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { RUNTIME_CONFIG, RuntimeConfig } from '../config/runtime-config';
import { HttpClient } from '@angular/common/http';

describe('authInterceptor', () => {
  const config: RuntimeConfig = {
    apiBaseUrl: 'https://api.example.test/api',
    keycloak: { url: 'https://identity.example.test', realm: 'wwtrading', clientId: 'wwtrading-web' }
  };
  let http: HttpClient;
  let controller: HttpTestingController;
  let getValidAccessToken: jasmine.Spy;

  beforeEach(() => {
    getValidAccessToken = jasmine.createSpy('getValidAccessToken').and.resolveTo('access-token');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: config },
        { provide: AuthService, useValue: { getValidAccessToken } }
      ]
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds a bearer token to a configured API request', fakeAsync(() => {
    http.get('https://api.example.test/api/users/me').subscribe();
    flushMicrotasks();

    const request = controller.expectOne('https://api.example.test/api/users/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    request.flush({});
  }));

  it('preserves the request when no token is available', fakeAsync(() => {
    getValidAccessToken.and.resolveTo(null);
    http.get('https://api.example.test/api/users/me').subscribe();
    flushMicrotasks();

    const request = controller.expectOne('https://api.example.test/api/users/me');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  }));

  it('does not attach a token to an untrusted URL', () => {
    http.get('https://api.example.test.evil.test/api/users/me').subscribe();

    const request = controller.expectOne('https://api.example.test.evil.test/api/users/me');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    expect(getValidAccessToken).not.toHaveBeenCalled();
    request.flush({});
  });

  it('preserves an existing Authorization header', () => {
    http.get('https://api.example.test/api/users/me', {
      headers: { Authorization: 'Existing credential' }
    }).subscribe();

    const request = controller.expectOne('https://api.example.test/api/users/me');
    expect(request.request.headers.get('Authorization')).toBe('Existing credential');
    expect(getValidAccessToken).not.toHaveBeenCalled();
    request.flush({});
  });
});
