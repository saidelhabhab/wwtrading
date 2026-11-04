import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { AuthService, resolveWwtradingRole, resolveWwtradingRoles } from './auth.service';
import { KEYCLOAK } from './keycloak.token';

describe('AuthService', () => {
  let keycloak: Keycloak;
  let login: jasmine.Spy;
  let logout: jasmine.Spy;
  let updateToken: jasmine.Spy;
  let isTokenExpired: jasmine.Spy;

  beforeEach(() => {
    login = jasmine.createSpy('login').and.resolveTo();
    logout = jasmine.createSpy('logout').and.resolveTo();
    updateToken = jasmine.createSpy('updateToken').and.resolveTo(false);
    isTokenExpired = jasmine.createSpy('isTokenExpired').and.returnValue(true);
    keycloak = {
      authenticated: false,
      login,
      logout,
      updateToken,
      isTokenExpired,
      clearToken: jasmine.createSpy('clearToken'),
      realmAccess: undefined,
      tokenParsed: undefined
    } as unknown as Keycloak;

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: KEYCLOAK, useValue: keycloak }
      ]
    });
  });

  it('delegates login and logout to Keycloak', async () => {
    const service = TestBed.inject(AuthService);

    await service.login();
    await service.logout();

    expect(login).toHaveBeenCalledWith(jasmine.objectContaining({ redirectUri: `${location.origin}/login` }));
    expect(logout).toHaveBeenCalledWith({ redirectUri: location.origin });
  });

  it('updates the authentication state after a Keycloak success callback', () => {
    const service = TestBed.inject(AuthService);

    keycloak.onAuthSuccess?.();

    expect(service.authenticated()).toBeTrue();
  });

  it('exposes the authenticated user and only business roles', () => {
    keycloak.authenticated = true;
    keycloak.token = 'in-memory-token';
    keycloak.tokenParsed = {
      sub: 'user-1',
      name: 'User One',
      email: 'user@example.test',
      realm_access: { roles: ['client', 'offline_access'] }
    };

    const service = TestBed.inject(AuthService);

    expect(service.user()).toEqual({
      subject: 'user-1',
      displayName: 'User One',
      email: 'user@example.test',
      roles: ['CLIENT']
    });
    expect(service.tokenAvailable()).toBeTrue();
  });

  it('refreshes before returning the in-memory access token', async () => {
    keycloak.authenticated = true;
    keycloak.token = 'in-memory-token';
    const service = TestBed.inject(AuthService);

    expect(await service.getValidAccessToken()).toBe('in-memory-token');
    expect(updateToken).toHaveBeenCalledWith(30);
  });

  it('uses a valid in-memory token without refreshing', async () => {
    keycloak.authenticated = true;
    keycloak.token = 'in-memory-token';
    isTokenExpired.and.returnValue(false);
    const service = TestBed.inject(AuthService);

    expect(await service.getValidAccessToken()).toBe('in-memory-token');
    expect(updateToken).not.toHaveBeenCalled();
  });

  it('obtains a token when authentication is ready before the token is available', async () => {
    keycloak.authenticated = true;
    updateToken.and.callFake(async () => {
      keycloak.token = 'token-after-login';
      return true;
    });
    isTokenExpired.and.returnValue(false);
    const service = TestBed.inject(AuthService);

    expect(await service.getValidAccessToken()).toBe('token-after-login');
    expect(updateToken).toHaveBeenCalledWith(0);
  });

  it('coalesces concurrent token refreshes', async () => {
    keycloak.authenticated = true;
    keycloak.token = 'in-memory-token';
    let resolveRefresh: (value: boolean) => void = () => undefined;
    updateToken.and.returnValue(new Promise<boolean>((resolve) => { resolveRefresh = resolve; }));
    const service = TestBed.inject(AuthService);

    const first = service.getValidAccessToken();
    const second = service.getValidAccessToken();
    resolveRefresh(false);

    expect(await first).toBe('in-memory-token');
    expect(await second).toBe('in-memory-token');
    expect(updateToken).toHaveBeenCalledTimes(1);
  });

  it('clears local auth state before redirecting to logout', async () => {
    keycloak.authenticated = true;
    keycloak.token = 'in-memory-token';
    const service = TestBed.inject(AuthService);

    await service.logout();

    expect(keycloak.clearToken).toHaveBeenCalled();
    expect(service.authenticated()).toBeFalse();
    expect(service.tokenAvailable()).toBeFalse();
    expect(logout).toHaveBeenCalledWith({ redirectUri: location.origin });
  });

  it('routes every supported WWTRADING role to the expected dashboard', () => {
    expect(AuthService.resolveDashboardRoute(['ADMIN'])).toBe('/admin/dashboard');
    expect(AuthService.resolveDashboardRoute(['MANAGER'])).toBe('/manager/dashboard');
    expect(AuthService.resolveDashboardRoute(['COMMERCIAL'])).toBe('/commercial/dashboard');
    expect(AuthService.resolveDashboardRoute(['TECHNICIAN'])).toBe('/technician/dashboard');
    expect(AuthService.resolveDashboardRoute(['CLIENT'])).toBe('/client/dashboard');
  });

  it('prefers the highest-priority valid role when multiple roles are present', () => {
    expect(AuthService.resolveDashboardRoute(['CLIENT', 'ADMIN', 'TECHNICIAN'])).toBe('/admin/dashboard');
    expect(AuthService.resolveDashboardRoute(['CLIENT', 'MANAGER'])).toBe('/manager/dashboard');
  });

  it('returns null for unknown or missing roles instead of redirecting to admin', () => {
    expect(AuthService.resolveDashboardRoute([])).toBeNull();
    expect(AuthService.resolveDashboardRoute(['OTHER'])).toBeNull();
  });

  it('resolves all supported realm roles regardless of casing', () => {
    expect(resolveWwtradingRoles(['admin', 'manager', 'commercial', 'technician', 'client']))
      .toEqual(['ADMIN', 'MANAGER', 'COMMERCIAL', 'TECHNICIAN', 'CLIENT']);
  });

  it('ignores Keycloak default and unknown roles', () => {
    expect(resolveWwtradingRoles(['default-roles-wwtrading', 'offline_access', 'unknown'])).toEqual([]);
    expect(resolveWwtradingRole([])).toBeNull();
  });

  it('uses deterministic application-role priority', () => {
    expect(resolveWwtradingRole(['client', 'technician', 'manager'])).toBe('MANAGER');
  });
});
