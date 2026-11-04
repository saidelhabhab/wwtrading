import { Type } from '@angular/core';

import { routes } from '../../app.routes';
import { WwtradingRole } from '../../core/auth/auth.service';
import { DashboardComponent as AdminDashboardComponent } from '../admin/dashboard/dashboard.component';
import { DashboardComponent as ClientDashboardComponent } from '../client/dashboard/dashboard.component';
import { DashboardComponent as CommercialDashboardComponent } from '../commercial/dashboard/dashboard.component';
import { DashboardComponent as ManagerDashboardComponent } from '../manager/dashboard/dashboard.component';
import { DashboardComponent as TechnicianDashboardComponent } from '../technician/dashboard/dashboard.component';
import { resolvePostLoginRoute } from './login-redirect.component';

describe('role dashboard routing', () => {
  const mapping: ReadonlyArray<readonly [WwtradingRole, string, Type<unknown>]> = [
    ['ADMIN', '/admin/dashboard', AdminDashboardComponent],
    ['MANAGER', '/manager/dashboard', ManagerDashboardComponent],
    ['COMMERCIAL', '/commercial/dashboard', CommercialDashboardComponent],
    ['TECHNICIAN', '/technician/dashboard', TechnicianDashboardComponent],
    ['CLIENT', '/client/dashboard', ClientDashboardComponent]
  ];

  it('resolves every supported role to its isolated dashboard', () => {
    for (const [role, dashboard] of mapping) {
      expect(resolvePostLoginRoute(null, [role], routes)).toBe(dashboard);
    }
  });

  it('rejects an incompatible privileged return URL in favor of the role dashboard', () => {
    for (const [role, dashboard] of mapping.filter(([role]) => role !== 'ADMIN')) {
      expect(resolvePostLoginRoute('/admin/dashboard', [role], routes)).toBe(dashboard);
    }
  });

  it('preserves authorized internal and public return URLs', () => {
    expect(resolvePostLoginRoute('/technician/dashboard', ['TECHNICIAN'], routes))
      .toBe('/technician/dashboard');
    expect(resolvePostLoginRoute('/products', ['CLIENT'], routes)).toBe('/products');
  });

  it('rejects external URLs and safely handles unsupported roles', () => {
    expect(resolvePostLoginRoute('https://evil-site.example', ['CLIENT'], routes))
      .toBe('/client/dashboard');
    expect(resolvePostLoginRoute('/products', [], routes)).toBe('/access-denied');
  });

  it('keeps all dashboard routes protected and loads their real components', async () => {
    for (const [role, dashboard, expectedComponent] of mapping) {
      const route = routes.find((candidate) => candidate.path === dashboard.slice(1));
      expect(route?.data?.['roles']).toEqual([role]);
      expect(route?.canActivate?.length).toBe(2);
      const component: unknown = await route?.loadComponent?.();
      expect(component).toBe(expectedComponent);
    }
  });

  it('keeps the public product catalog unguarded', () => {
    const productRoute = routes.find((route) => route.path === 'products');
    expect(productRoute?.canActivate).toBeUndefined();
  });
});
