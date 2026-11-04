import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, isWwtradingRole, resolveSafeReturnUrl, WwtradingRole } from './auth.service';

export const roleGuard: CanActivateFn = async (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router, { optional: true });

  if (!auth.authenticated()) {
    const returnUrl = resolveSafeReturnUrl(state?.url ?? '/');
    if (returnUrl) {
      await auth.login(returnUrl);
    } else {
      await auth.login();
    }
    return false;
  }

  const configuredRoles: unknown = route.data['roles'];
  if (configuredRoles === undefined) return true;
  if (!Array.isArray(configuredRoles) || configuredRoles.length === 0) return false;

  const allowedRoles = configuredRoles.filter(isWwtradingRole);
  if (allowedRoles.length !== configuredRoles.length) return false;

  const userRoles = auth.roles();
  const hasAccess = allowedRoles.some((role: WwtradingRole) => userRoles.includes(role));
  if (!hasAccess) {
    if (router) {
      await router.navigate(['/access-denied'], { replaceUrl: true });
    }
    return false;
  }

  return true;
};
