import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Route, Router, Routes } from '@angular/router';

import {
  AuthService,
  isWwtradingRole,
  resolveSafeReturnUrl,
  WwtradingRole
} from '../../core/auth/auth.service';
import { OrderApiService } from '../orders/order-api.service';

@Component({
  selector: 'wwt-login-redirect',
  standalone: true,
  template: '<p class="sr-only">Redirection vers votre tableau de bord…</p>'
})
export class LoginRedirectComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly orderApi = inject(OrderApiService);

  ngOnInit(): void {
    const returnUrl = resolveSafeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
    if (this.auth.authenticated()) {
      const roles = this.auth.roles();
      if (roles.includes('CLIENT')) {
        this.orderApi.claimGuestOrders().subscribe({ error: () => {} });
      }
      const destination = resolvePostLoginRoute(returnUrl, roles, this.router.config);
      void this.router.navigateByUrl(destination, { replaceUrl: true });
      return;
    }

    void this.auth.login(returnUrl ?? undefined);
  }
}

export function resolvePostLoginRoute(
  returnUrl: string | null | undefined,
  roles: readonly WwtradingRole[],
  routeConfig: Routes
): string {
  const dashboard = AuthService.resolveDashboardRoute(roles);
  if (dashboard === null) return '/access-denied';

  const safeReturnUrl = resolveSafeReturnUrl(returnUrl);
  if (safeReturnUrl === null || routePath(safeReturnUrl) === 'login') return dashboard;

  const target = routeConfig.find((route) => matchesRoute(route, routePath(safeReturnUrl)));
  if (target === undefined) return dashboard;

  const configuredRoles: unknown = target.data?.['roles'];
  if (configuredRoles === undefined) return safeReturnUrl;
  if (!Array.isArray(configuredRoles) || configuredRoles.length === 0) return dashboard;

  const allowedRoles = configuredRoles.filter(isWwtradingRole);
  const configurationIsValid = allowedRoles.length === configuredRoles.length;
  return configurationIsValid && allowedRoles.some((role) => roles.includes(role))
    ? safeReturnUrl
    : dashboard;
}

function routePath(url: string): string {
  let path = url.split(/[?#]/, 1)[0];
  while (path.startsWith('/')) path = path.slice(1);
  while (path.endsWith('/')) path = path.slice(0, -1);
  return path;
}

function matchesRoute(route: Route, path: string): boolean {
  if (route.path === undefined || route.path === '**' || route.redirectTo !== undefined) return false;

  const routeSegments = route.path.split('/').filter(Boolean);
  const urlSegments = path.split('/').filter(Boolean);
  return routeSegments.length === urlSegments.length
    && routeSegments.every((segment, index) => segment.startsWith(':') || segment === urlSegments[index]);
}
