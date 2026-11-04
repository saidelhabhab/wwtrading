import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { authGuard } from './core/auth/auth.guard';
import { AuthenticatedUser, AuthService } from './core/auth/auth.service';
import { roleGuard } from './core/auth/role.guard';

describe('role-aware navigation', () => {
  const authenticated = signal(true);
  const user = signal<AuthenticatedUser | null>(null);
  let login: jasmine.Spy;
  let logout: jasmine.Spy;

  beforeEach(() => {
    authenticated.set(true);
    user.set(null);
    login = jasmine.createSpy('login').and.resolveTo();
    logout = jasmine.createSpy('logout').and.resolveTo();
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            authenticated: authenticated.asReadonly(),
            user: user.asReadonly(),
            login,
            logout
          }
        }
      ]
    });
  });

  it('keeps the home route public', () => {
    const home = routes.find(route => route.path === '');
    expect(home?.canActivate).toBeUndefined();
  });

  it('keeps the contact route public', () => {
    const contact = routes.find(route => route.path === 'contact');
    expect(contact?.canActivate).toBeUndefined();
  });

  it('protects each role-scoped account route with its own role only', () => {
    const expected: readonly [string, string][] = [
      ['client/mon-compte', 'CLIENT'],
      ['admin/mon-compte', 'ADMIN'],
      ['manager/mon-compte', 'MANAGER'],
      ['commercial/mon-compte', 'COMMERCIAL'],
      ['technician/mon-compte', 'TECHNICIAN']
    ];

    for (const [path, role] of expected) {
      const account = routes.find(route => route.path === path);
      expect(account).withContext(`${path} must exist`).toBeDefined();
      expect(account?.canActivate).withContext(path).toEqual([authGuard, roleGuard]);
      expect(account?.data?.['roles']).withContext(path).toEqual([role]);
    }
  });

  it('no longer exposes a generic mon-compte route', () => {
    expect(routes.find(route => route.path === 'mon-compte')).toBeUndefined();
  });

  it('keeps the Product catalog public for every user state without invoking Keycloak', () => {
    const product = routes.find(route => route.path === 'products');
    expect(product?.canActivate).toBeUndefined();
    expect(product?.data).toBeUndefined();

    for (const roles of [[], ['CLIENT'], ['ADMIN'], ['MANAGER'], ['COMMERCIAL']]) {
      authenticated.set(roles.length > 0);
      user.set(roles.length > 0 ? {
        subject: `user-${roles[0]}`,
        displayName: 'Product User',
        email: null,
        roles: roles as AuthenticatedUser['roles']
      } : null);
    }

    expect(login).not.toHaveBeenCalled();
  });

  it('keeps Product management routes protected for write roles', () => {
    for (const path of ['products/new', 'products/:id/edit']) {
      const route = routes.find(candidate => candidate.path === path);
      expect(route?.canActivate).toEqual([authGuard, roleGuard]);
      expect(route?.data?.['roles']).toEqual(['ADMIN', 'MANAGER', 'COMMERCIAL']);
    }
  });

  it('hides the protected link when the authenticated user has no allowed role', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Mon compte');
  });

  it('shows the protected link when the user has an allowed role', () => {
    user.set({ subject: 'user-1', displayName: 'User One', email: null, roles: ['CLIENT'] });
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mon compte');
  });

  it('shows only the login action when unauthenticated', () => {
    authenticated.set(false);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Connexion');
    expect(fixture.nativeElement.textContent).not.toContain('Déconnexion');
  });

  it('uses AuthService for the login action', async () => {
    authenticated.set(false);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('nav button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(login).toHaveBeenCalledOnceWith('/');
  });

  it('uses AuthService for the logout action', async () => {
    user.set({ subject: 'user-1', displayName: 'User One', email: null, roles: ['CLIENT'] });
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('nav button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(logout).toHaveBeenCalledOnceWith();
  });

  it('opens and closes the accessible mobile menu after navigation or Escape', () => {
    authenticated.set(false);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const toggle = fixture.nativeElement.querySelector('.menu-toggle') as HTMLButtonElement;
    const navigation = fixture.nativeElement.querySelector('#main-navigation') as HTMLElement;

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(navigation.classList).toContain('open');

    (navigation.querySelector('a') as HTMLAnchorElement).click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(navigation.classList).not.toContain('open');
  });
});
