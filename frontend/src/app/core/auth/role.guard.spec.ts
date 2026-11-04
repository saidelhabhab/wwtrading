import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';

import { AuthenticatedUser, AuthService, WwtradingRole } from './auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  const state = {} as RouterStateSnapshot;
  let authenticated: boolean;
  let roles: WwtradingRole[];
  let login: jasmine.Spy;

  beforeEach(() => {
    authenticated = true;
    roles = [];
    login = jasmine.createSpy('login').and.resolveTo();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            authenticated: () => authenticated,
            user: (): AuthenticatedUser => ({
              subject: 'user-1',
              displayName: 'User One',
              email: null,
              roles
            }),
            roles: () => roles,
            login
          }
        }
      ]
    });
  });

  it('allows an authenticated user with an allowed role', async () => {
    roles = ['ADMIN'];
    expect(await execute(['ADMIN'])).toBeTrue();
  });

  it('denies an authenticated user without a required role', async () => {
    roles = ['CLIENT'];
    TestBed.overrideProvider(Router, { useValue: { navigate: jasmine.createSpy('navigate') } });
    expect(await execute(['ADMIN'])).toBeFalse();
  });

  it('redirects an authenticated user to access denied when they lack the route role', async () => {
    roles = ['CLIENT'];
    const navigate = jasmine.createSpy('navigate');
    TestBed.overrideProvider(Router, { useValue: { navigate } });

    expect(await execute(['ADMIN'])).toBeFalse();
    expect(navigate).toHaveBeenCalledWith(['/access-denied'], jasmine.objectContaining({ replaceUrl: true }));
  });

  it('allows any matching role from multiple configured roles', async () => {
    roles = ['MANAGER'];
    expect(await execute(['ADMIN', 'MANAGER'])).toBeTrue();
  });

  it('starts the existing login flow for an unauthenticated user', async () => {
    authenticated = false;
    expect(await execute(['CLIENT'])).toBeFalse();
    expect(login).toHaveBeenCalledOnceWith('/');
  });

  it('preserves normal authenticated behavior without a role requirement', async () => {
    expect(await execute(undefined)).toBeTrue();
  });

  it('denies an unknown configured role', async () => {
    roles = ['ADMIN'];
    expect(await execute(['ARBITRARY_ROLE'])).toBeFalse();
  });

  function execute(configuredRoles: unknown): Promise<boolean> {
    const route = { data: configuredRoles === undefined ? {} : { roles: configuredRoles } } as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(async () => Boolean(await roleGuard(route, state)));
  }
});
