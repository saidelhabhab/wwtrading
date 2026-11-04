import { Router } from '@angular/router';
import { isSafeActionUrl, safeNavigateActionUrl } from './notification-navigation.util';

describe('NotificationNavigationUtil', () => {
  describe('isSafeActionUrl', () => {
    it('should return false for null, undefined, or empty values', () => {
      expect(isSafeActionUrl(null)).toBeFalse();
      expect(isSafeActionUrl(undefined)).toBeFalse();
      expect(isSafeActionUrl('')).toBeFalse();
      expect(isSafeActionUrl('   ')).toBeFalse();
    });

    it('should return false for unsafe protocols', () => {
      expect(isSafeActionUrl('javascript:alert(1)')).toBeFalse();
      expect(isSafeActionUrl('data:text/html,<script>alert(1)</script>')).toBeFalse();
      expect(isSafeActionUrl('file:///etc/passwd')).toBeFalse();
      expect(isSafeActionUrl('vbscript:msgbox(1)')).toBeFalse();
    });

    it('should return false for external HTTP/HTTPS links', () => {
      expect(isSafeActionUrl('http://evil.com')).toBeFalse();
      expect(isSafeActionUrl('https://evil.com/login')).toBeFalse();
      expect(isSafeActionUrl('//evil.com/test')).toBeFalse();
    });

    it('should return true for valid internal application paths', () => {
      expect(isSafeActionUrl('/products/123')).toBeTrue();
      expect(isSafeActionUrl('/client/orders/123')).toBeTrue();
      expect(isSafeActionUrl('/client/quotations/123?from=notification')).toBeTrue();
      expect(isSafeActionUrl('/client/maintenance/123#details')).toBeTrue();
      expect(isSafeActionUrl('client/orders/123')).toBeTrue();
    });
  });

  describe('safeNavigateActionUrl', () => {
    let routerSpy: jasmine.SpyObj<Router>;

    beforeEach(() => {
      routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);
    });

    it('should navigate via router for valid internal URLs', async () => {
      routerSpy.navigateByUrl.and.returnValue(Promise.resolve(true));

      const result = await safeNavigateActionUrl(routerSpy, '/products/123');

      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/products/123');
      expect(result).toBeTrue();
    });

    it('should not navigate for unsafe URLs', async () => {
      const result = await safeNavigateActionUrl(routerSpy, 'javascript:alert(1)');

      expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
      expect(result).toBeFalse();
    });

    it('should not navigate for null/empty URLs', async () => {
      const result = await safeNavigateActionUrl(routerSpy, null);

      expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
      expect(result).toBeFalse();
    });

    it('should handle router navigation rejection gracefully without crashing', async () => {
      routerSpy.navigateByUrl.and.returnValue(Promise.reject(new Error('Invalid route')));

      const result = await safeNavigateActionUrl(routerSpy, '/unsupported-route/999');

      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/unsupported-route/999');
      expect(result).toBeFalse();
    });
  });
});
