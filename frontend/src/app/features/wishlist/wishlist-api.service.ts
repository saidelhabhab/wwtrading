import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, concatMap, from, of, switchMap, tap, toArray } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { AddWishlistItemRequest, Wishlist } from './wishlist.models';

@Injectable({ providedIn: 'root' })
export class WishlistApiService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/wishlist`;
  private readonly guestStorageKey = 'wwtrading.guest-wishlist';

  getCurrentWishlist(): Observable<Wishlist> {
    if (!this.auth.authenticated()) return of(this.readGuestWishlist());
    const guestWishlist = this.readGuestWishlist();
    return this.http.get<Wishlist>(this.resourceUrl).pipe(
      switchMap((wishlist) => from(guestWishlist.items).pipe(
        concatMap((item) => wishlist.items.some((current) => current.productReference === item.productReference)
          ? of(wishlist)
          : this.http.post<Wishlist>(`${this.resourceUrl}/items`, { productReference: item.productReference })),
        toArray(),
        switchMap((merged) => of(merged.at(-1) ?? wishlist)),
        tap(() => localStorage.removeItem(this.guestStorageKey))
      ))
    );
  }

  addProduct(productReference: string): Observable<Wishlist> {
    const request: AddWishlistItemRequest = { productReference };
    if (!this.auth.authenticated()) {
      const wishlist = this.updateGuestWishlist((current) => current.items.some((item) => item.productReference === productReference)
        ? current
        : { ...current, items: [...current.items, { id: crypto.randomUUID(), productReference }], updatedAt: new Date().toISOString() });
      return of(wishlist);
    }
    return this.http.post<Wishlist>(`${this.resourceUrl}/items`, request);
  }

  removeProduct(itemId: string): Observable<void> {
    if (!this.auth.authenticated()) {
      this.updateGuestWishlist((current) => ({ ...current, items: current.items.filter((item) => item.id !== itemId), updatedAt: new Date().toISOString() }));
      return of(void 0);
    }
    return this.http.delete<void>(`${this.resourceUrl}/items/${encodeURIComponent(itemId)}`);
  }

  clearWishlist(): Observable<void> {
    if (!this.auth.authenticated()) {
      this.updateGuestWishlist((current) => ({ ...current, items: [], updatedAt: new Date().toISOString() }));
      return of(void 0);
    }
    return this.http.delete<void>(`${this.resourceUrl}/items`);
  }

  private readGuestWishlist(): Wishlist {
    try {
      const stored = localStorage.getItem(this.guestStorageKey);
      if (stored) return JSON.parse(stored) as Wishlist;
    } catch { /* Ignore malformed guest state. */ }
    const now = new Date().toISOString();
    return { id: 'guest-wishlist', items: [], createdAt: now, updatedAt: now };
  }

  private updateGuestWishlist(update: (current: Wishlist) => Wishlist): Wishlist {
    const wishlist = update(this.readGuestWishlist());
    localStorage.setItem(this.guestStorageKey, JSON.stringify(wishlist));
    return wishlist;
  }
}
