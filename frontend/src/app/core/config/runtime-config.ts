import { InjectionToken } from '@angular/core';

export interface RuntimeConfig {
  readonly apiBaseUrl: string;
  /** Public canonical origin, for example https://example.com. Empty uses the browser origin. */
  readonly siteUrl?: string;
  /** Public base URL for guest tracking links. Empty uses current browser origin. */
  readonly trackingBaseUrl?: string;
  /** Optional Google Search Console HTML-tag verification token. */
  readonly googleSiteVerification?: string;
  /** Optional GA4 measurement ID. Analytics remains disabled while this is empty. */
  readonly googleAnalyticsMeasurementId?: string;
  readonly keycloak: {
    readonly url: string;
    readonly realm: string;
    readonly clientId: string;
  };
}

export const RUNTIME_CONFIG = new InjectionToken<RuntimeConfig>('WWTRADING_RUNTIME_CONFIG');

export async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  const response = await fetch('/config/runtime-config.json', { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`Runtime configuration could not be loaded (${response.status})`);
  }
  const config: unknown = await response.json();
  if (!isRuntimeConfig(config)) {
    throw new Error('Runtime configuration is invalid');
  }
  return config;
}

function isRuntimeConfig(value: unknown): value is RuntimeConfig {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  const keycloak = candidate['keycloak'];
  if (typeof keycloak !== 'object' || keycloak === null) return false;
  const identity = keycloak as Record<string, unknown>;
  return typeof candidate['apiBaseUrl'] === 'string'
    && (candidate['siteUrl'] === undefined || typeof candidate['siteUrl'] === 'string')
    && (candidate['trackingBaseUrl'] === undefined || typeof candidate['trackingBaseUrl'] === 'string')
    && (candidate['googleSiteVerification'] === undefined || typeof candidate['googleSiteVerification'] === 'string')
    && (candidate['googleAnalyticsMeasurementId'] === undefined || typeof candidate['googleAnalyticsMeasurementId'] === 'string')
    && typeof identity['url'] === 'string'
    && typeof identity['realm'] === 'string'
    && typeof identity['clientId'] === 'string';
}
