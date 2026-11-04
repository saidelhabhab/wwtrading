import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, concatMap, defer, finalize, from, of, reduce, shareReplay, switchMap, tap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { AddCartItemRequest, Cart, UpdateCartItemQuantityRequest } from './cart.models';

@Injectable({ providedIn: 'root' })
export class CartApiService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/cart`;
  private readonly guestStorageKey = 'wwtrading.guest-cart';
  private readonly cartState = signal<Cart | null>(this.readGuestCart());
  private authenticatedCartLoad: Observable<Cart> | null = null;

  readonly cart = this.cartState.asReadonly();
  readonly itemCount = computed(() =>
    this.cartState()?.items.reduce((total, item) => total + item.quantity, 0) ?? 0
  );

  getCart(): Observable<Cart> {
    if (!this.auth.authenticated()) {
      const guestCart = this.readGuestCart();
      this.cartState.set(guestCart);
      return of(guestCart);
    }

    if (this.authenticatedCartLoad) return this.authenticatedCartLoad;

    this.authenticatedCartLoad = defer(() => this.loadAndMergeAuthenticatedCart()).pipe(
      finalize(() => this.authenticatedCartLoad = null),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    return this.authenticatedCartLoad;
  }

  addItem(request: AddCartItemRequest, availableQuantity: number | null = null): Observable<Cart> {
    if (!this.auth.authenticated()) {
      return defer(() => of(this.updateGuestCart((current) => {
          const existing = current.items.find((item) => item.productReference === request.productReference);
          const nextQuantity = (existing?.quantity ?? 0) + request.quantity;
          if (availableQuantity != null && nextQuantity > availableQuantity) {
            throw new Error(this.quantityLimitMessage(availableQuantity));
          }
          const items = existing
            ? current.items.map((item) => item === existing
              ? { ...item, quantity: nextQuantity, availableQuantity }
              : item)
            : [...current.items, {
                id: crypto.randomUUID(),
                productReference: request.productReference,
                quantity: request.quantity,
                availableQuantity
              }];
          return { ...current, items, updatedAt: new Date().toISOString() };
        })));
    }
    return this.http.post<Cart>(`${this.resourceUrl}/items`, request).pipe(
      tap((cart) => this.cartState.set(cart))
    );
  }

  updateItem(itemId: string, request: UpdateCartItemQuantityRequest): Observable<Cart> {
    if (!this.auth.authenticated()) {
      return defer(() => of(this.updateGuestCart((current) => {
        const item = current.items.find((candidate) => candidate.id === itemId);
        if (item?.availableQuantity != null && request.quantity > item.availableQuantity) {
          throw new Error(this.quantityLimitMessage(item.availableQuantity));
        }
        return {
          ...current,
          items: current.items.map((candidate) => candidate.id === itemId
            ? { ...candidate, quantity: request.quantity }
            : candidate),
          updatedAt: new Date().toISOString()
        };
      })));
    }
    return this.http.put<Cart>(
      `${this.resourceUrl}/items/${encodeURIComponent(itemId)}`,
      request
    ).pipe(tap((cart) => this.cartState.set(cart)));
  }

  removeItem(itemId: string): Observable<Cart> {
    if (!this.auth.authenticated()) {
      return of(this.updateGuestCart((current) => ({
        ...current,
        items: current.items.filter((item) => item.id !== itemId),
        updatedAt: new Date().toISOString()
      })));
    }
    return this.http.delete<void>(`${this.resourceUrl}/items/${encodeURIComponent(itemId)}`).pipe(
      switchMap(() => this.getCart())
    );
  }

  clearCart(): Observable<Cart> {
    if (!this.auth.authenticated()) {
      const cart = this.updateGuestCart((current) => ({ ...current, items: [], updatedAt: new Date().toISOString() }));
      return of(cart);
    }
    return this.http.delete<void>(`${this.resourceUrl}/items`).pipe(
      switchMap(() => this.getCart())
    );
  }

  private emptyCart(): Cart {
    const now = new Date().toISOString();
    return { id: 'guest-cart', items: [], createdAt: now, updatedAt: now };
  }

  private readGuestCart(): Cart {
    try {
      const stored = localStorage.getItem(this.guestStorageKey);
      return stored ? JSON.parse(stored) as Cart : this.emptyCart();
    } catch {
      return this.emptyCart();
    }
  }

  private updateGuestCart(update: (current: Cart) => Cart): Cart {
    const cart = update(this.readGuestCart());
    this.cartState.set(cart);
    localStorage.setItem(this.guestStorageKey, JSON.stringify(cart));
    return cart;
  }

  private loadAndMergeAuthenticatedCart(): Observable<Cart> {
    this.cartState.set(null);
    return this.http.get<Cart>(this.resourceUrl).pipe(
      tap((cart) => this.cartState.set(cart)),
      switchMap((serverCart) => {
        const guestItems = this.readGuestCart().items;
        if (guestItems.length === 0) {
          this.clearGuestCart();
          return of(serverCart);
        }

        return from(guestItems).pipe(
          concatMap((guestItem) => this.http.post<Cart>(`${this.resourceUrl}/items`, {
            productReference: guestItem.productReference,
            quantity: guestItem.quantity
          }).pipe(
            tap((mergedCart) => {
              this.cartState.set(mergedCart);
              this.removeMergedGuestItem(guestItem.id);
            })
          )),
          reduce((_latestCart, mergedCart) => mergedCart, serverCart)
        );
      }),
      tap((cart) => this.cartState.set(cart))
    );
  }

  private removeMergedGuestItem(itemId: string): void {
    const guestCart = this.readGuestCart();
    const remainingItems = guestCart.items.filter((item) => item.id !== itemId);
    if (remainingItems.length === 0) {
      this.clearGuestCart();
      return;
    }
    localStorage.setItem(this.guestStorageKey, JSON.stringify({
      ...guestCart,
      items: remainingItems,
      updatedAt: new Date().toISOString()
    }));
  }

  private clearGuestCart(): void {
    localStorage.removeItem(this.guestStorageKey);
  }

  private quantityLimitMessage(availableQuantity: number): string {
    return `Quantité maximale atteinte. Maximum disponible : ${availableQuantity}.`;
  }
}
