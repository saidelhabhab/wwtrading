import { computed, inject, Injectable, signal } from '@angular/core';
import { KEYCLOAK } from './keycloak.token';

export interface AuthenticatedUser {
  readonly subject: string;
  readonly displayName: string;
  readonly email: string | null;
  readonly roles: readonly WwtradingRole[];
}

export const WWTRADING_ROLES = ['ADMIN', 'MANAGER', 'COMMERCIAL', 'TECHNICIAN', 'CLIENT'] as const;
export type WwtradingRole = typeof WWTRADING_ROLES[number];

const ROLE_TO_DASHBOARD: Record<WwtradingRole, string> = {
  ADMIN: '/admin/dashboard',
  MANAGER: '/manager/dashboard',
  COMMERCIAL: '/commercial/dashboard',
  TECHNICIAN: '/technician/dashboard',
  CLIENT: '/client/dashboard'
};

/**
 * Role-scoped account route. The profile UI is shared, but each role reaches it
 * inside its own workspace namespace.
 */
const ROLE_TO_ACCOUNT: Record<WwtradingRole, string> = {
  ADMIN: '/admin/mon-compte',
  MANAGER: '/manager/mon-compte',
  COMMERCIAL: '/commercial/mon-compte',
  TECHNICIAN: '/technician/mon-compte',
  CLIENT: '/client/mon-compte'
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = inject(KEYCLOAK);
  private refreshInFlight: Promise<boolean> | null = null;
  private readonly authenticatedState = signal(this.keycloak.authenticated === true);
  private readonly tokenAvailableState = signal(Boolean(this.keycloak.token));

  readonly authenticated = this.authenticatedState.asReadonly();
  readonly tokenAvailable = this.tokenAvailableState.asReadonly();
  readonly roles = computed<readonly WwtradingRole[]>(() => {
    if (!this.authenticatedState()) return [];
    const token = this.keycloak.tokenParsed;
    return resolveWwtradingRoles([
      ...(this.keycloak.realmAccess?.roles ?? []),
      ...(token?.realm_access?.roles ?? [])
    ]);
  });
  readonly user = computed<AuthenticatedUser | null>(() => {
    if (!this.authenticatedState()) return null;
    const token = this.keycloak.tokenParsed;
    if (!token?.sub) return null;
    let displayName = 'Utilisateur';
    if (typeof token['name'] === 'string') {
      displayName = token['name'];
    } else if (typeof token['preferred_username'] === 'string') {
      displayName = token['preferred_username'];
    }
    return {
      subject: token.sub,
      displayName,
      email: typeof token['email'] === 'string' ? token['email'] : null,
      roles: this.roles()
    };
  });

  static resolveDashboardRoute(roles: readonly string[] | null | undefined): string | null {
    const role = resolveWwtradingRole(roles);
    return role ? ROLE_TO_DASHBOARD[role] : null;
  }

  /**
   * Resolves the role-scoped account route. Returns null for an unknown or
   * unsupported role instead of falling back to another role's namespace.
   */
  static resolveAccountRoute(roles: readonly string[] | null | undefined): string | null {
    const role = resolveWwtradingRole(roles);
    return role ? ROLE_TO_ACCOUNT[role] : null;
  }

  constructor() {
    this.keycloak.onAuthSuccess = () => this.syncState(true);
    this.keycloak.onAuthLogout = () => this.syncState(false);
    this.keycloak.onAuthRefreshSuccess = () => this.syncState(true);
    this.keycloak.onAuthRefreshError = () => this.syncState(false);
    this.keycloak.onTokenExpired = () => void this.refreshToken();
  }

  login(returnUrl?: string | null): Promise<void> {
    const safeReturnUrl = resolveSafeReturnUrl(returnUrl);
    const destination = safeReturnUrl
      ? `${location.origin}/login?returnUrl=${encodeURIComponent(safeReturnUrl)}`
      : `${location.origin}/login`;
    return this.keycloak.login({ redirectUri: destination });
  }

  logout(): Promise<void> {
    this.keycloak.clearToken();
    this.syncState(false);
    return this.keycloak.logout({ redirectUri: location.origin });
  }

  async refreshToken(minValiditySeconds = 30): Promise<boolean> {
    if (!this.authenticatedState()) return false;
    if (!this.keycloak.isTokenExpired(minValiditySeconds)) {
      this.syncState(true);
      return true;
    }
    if (this.refreshInFlight) return this.refreshInFlight;

    this.refreshInFlight = this.refreshTokenOnce(minValiditySeconds);
    try {
      return await this.refreshInFlight;
    } finally {
      this.refreshInFlight = null;
    }
  }

  private async refreshTokenOnce(minValiditySeconds: number): Promise<boolean> {
    try {
      const refreshed = await this.keycloak.updateToken(minValiditySeconds);
      this.syncState(true);
      return refreshed;
    } catch {
      this.keycloak.clearToken();
      this.syncState(false);
      return false;
    }
  }

  async getValidAccessToken(minValiditySeconds = 30): Promise<string | null> {
    if (this.keycloak.authenticated !== this.authenticatedState()) {
      this.syncState(this.keycloak.authenticated === true);
    }
    if (!this.authenticatedState()) return null;
    if (!this.keycloak.token) {
      try {
        await this.keycloak.updateToken(0);
      } catch {
        this.keycloak.clearToken();
        this.syncState(false);
        return null;
      }
      this.syncState(true);
    }
    await this.refreshToken(minValiditySeconds);
    return this.authenticatedState() ? this.keycloak.token ?? null : null;
  }

  private syncState(authenticated: boolean): void {
    this.authenticatedState.set(authenticated);
    this.tokenAvailableState.set(authenticated && Boolean(this.keycloak.token));
  }

}

const BUSINESS_ROLES = new Set<string>(WWTRADING_ROLES);

export function isWwtradingRole(role: unknown): role is WwtradingRole {
  return typeof role === 'string' && BUSINESS_ROLES.has(role);
}

export function normalizeWwtradingRole(role: unknown): WwtradingRole | null {
  if (typeof role !== 'string') return null;
  const normalized = role.trim().toUpperCase();
  return isWwtradingRole(normalized) ? normalized : null;
}

export function resolveWwtradingRoles(roles: readonly unknown[] | null | undefined): WwtradingRole[] {
  const normalizedRoles = new Set(
    (roles ?? []).map(normalizeWwtradingRole).filter((role): role is WwtradingRole => role !== null)
  );
  return WWTRADING_ROLES.filter((role) => normalizedRoles.has(role));
}

export function resolveWwtradingRole(roles: readonly unknown[] | null | undefined): WwtradingRole | null {
  return resolveWwtradingRoles(roles)[0] ?? null;
}

export function resolveSafeReturnUrl(candidate: string | null | undefined): string | null {
  if (candidate == null) return null;

  const normalized = candidate.trim();
  if (!normalized || normalized === '/' || normalized.startsWith('//') || normalized.includes('://') || !normalized.startsWith('/')) {
    return normalized === '/' ? '/' : null;
  }

  const sanitized = normalized.replaceAll('\\', '/');
  if (sanitized.startsWith('//') || sanitized.includes('://')) {
    return null;
  }

  return sanitized;
}
