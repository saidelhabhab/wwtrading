import { Router } from '@angular/router';

/**
 * Validates whether a given actionUrl string is safe for internal Angular Router navigation.
 * Accepts internal relative routes (e.g., '/products/123', '/client/orders/456?from=notification').
 * Rejects unsafe protocols (javascript:, data:, file:, vbscript:) and external URLs.
 */
export function isSafeActionUrl(url?: string | null): boolean {
  if (!url || typeof url !== 'string') return false;
  const trimmed = url.trim();
  if (!trimmed) return false;

  const lower = trimmed.toLowerCase();

  // Reject unsafe URI schemes & external links
  if (
    lower.startsWith('javascript:') ||
    lower.startsWith('data:') ||
    lower.startsWith('file:') ||
    lower.startsWith('vbscript:') ||
    lower.startsWith('http://') ||
    lower.startsWith('https://') ||
    lower.startsWith('//')
  ) {
    return false;
  }

  // Must be a relative path starting with '/' or containing no protocol scheme
  return trimmed.startsWith('/') || !trimmed.includes(':');
}

/**
 * Safely navigates to an internal actionUrl using Angular Router.
 * Returns a Promise resolving to true if navigation succeeded, false otherwise.
 */
export function safeNavigateActionUrl(router: Router, url?: string | null): Promise<boolean> {
  if (!isSafeActionUrl(url)) {
    return Promise.resolve(false);
  }
  const targetUrl = url!.trim();
  return router.navigateByUrl(targetUrl).catch(() => false);
}
