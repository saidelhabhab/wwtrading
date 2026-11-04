import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  let authenticated: boolean;
  let login: jasmine.Spy;

  beforeEach(() => {
    authenticated = false;
    login = jasmine.createSpy('login').and.resolveTo();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            authenticated: () => authenticated,
            login
          }
        }
      ]
    });
  });

  it('allows an authenticated user', async () => {
    authenticated = true;

    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(result).toBeTrue();
    expect(login).not.toHaveBeenCalled();
  });

  it('starts the existing login flow for an unauthenticated user', async () => {
    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));

    expect(result).toBeFalse();
    expect(login).toHaveBeenCalledOnceWith();
  });
});
