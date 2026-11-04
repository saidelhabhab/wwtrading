import { inject } from '@angular/core';
import { CanActivateFn, RouterStateSnapshot } from '@angular/router';
import { AuthService, resolveSafeReturnUrl } from './auth.service';

export const authGuard: CanActivateFn = async (_, state: RouterStateSnapshot) => {
  const auth = inject(AuthService);
  if (auth.authenticated()) return true;

  const returnUrl = resolveSafeReturnUrl(state?.url);
  if (returnUrl) {
    await auth.login(returnUrl);
  } else {
    await auth.login();
  }
  return false;
};
