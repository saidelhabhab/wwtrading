import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth/auth.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { CartApiService } from './cart-api.service';
import { Cart } from './cart.models';

describe('CartApiService guest cart synchronization', () => {
  const guestStorageKey = 'wwtrading.guest-cart';
  const authenticated = signal(true);
  let service: CartApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem(guestStorageKey);
    authenticated.set(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { authenticated: authenticated.asReadonly() } },
        {
          provide: RUNTIME_CONFIG,
          useValue: { apiBaseUrl: 'http://api.test', keycloak: {} }
        }
      ]
    });
    service = TestBed.inject(CartApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(guestStorageKey);
  });

  it('shares one guest merge between concurrent cart loads', () => {
    storeGuestCart([{ id: 'guest-item', productReference: 'P-1', quantity: 1, availableQuantity: 10 }]);
    const results: Cart[] = [];

    service.getCart().subscribe((cart) => results.push(cart));
    service.getCart().subscribe((cart) => results.push(cart));

    http.expectOne('http://api.test/api/cart').flush(cart('server-cart', []));
    const merge = http.expectOne('http://api.test/api/cart/items');
    expect(merge.request.body).toEqual({ productReference: 'P-1', quantity: 1 });
    merge.flush(cart('server-cart', [
      { id: 'server-item', productReference: 'P-1', quantity: 1, availableQuantity: 10 }
    ]));

    expect(results.length).toBe(2);
    expect(results.every((result) => result.items[0]?.id === 'server-item')).toBeTrue();
    expect(localStorage.getItem(guestStorageKey)).toBeNull();
  });

  it('keeps server identifiers visible when a later guest item cannot be merged', () => {
    storeGuestCart([
      { id: 'guest-1', productReference: 'P-1', quantity: 1, availableQuantity: 10 },
      { id: 'guest-2', productReference: 'P-2', quantity: 1, availableQuantity: 10 }
    ]);

    service.getCart().subscribe({ error: () => undefined });
    http.expectOne('http://api.test/api/cart').flush(cart('server-cart', []));
    http.expectOne('http://api.test/api/cart/items').flush(cart('server-cart', [
      { id: 'server-1', productReference: 'P-1', quantity: 1, availableQuantity: 10 }
    ]));
    http.expectOne('http://api.test/api/cart/items').flush(
      { title: 'Stock conflict' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(service.cart()?.items.map((item) => item.id)).toEqual(['server-1']);
    const remainingGuestCart = JSON.parse(localStorage.getItem(guestStorageKey) ?? '{}') as Cart;
    expect(remainingGuestCart.items.map((item) => item.id)).toEqual(['guest-2']);
  });

  it('restores the guest cart after authentication ends', () => {
    service.getCart().subscribe();
    http.expectOne('http://api.test/api/cart').flush(cart('server-cart', [
      { id: 'server-item', productReference: 'P-1', quantity: 2, availableQuantity: 10 }
    ]));

    storeGuestCart([{ id: 'guest-item', productReference: 'P-2', quantity: 1, availableQuantity: 5 }]);
    authenticated.set(false);
    service.getCart().subscribe();

    expect(service.cart()?.id).toBe('guest-cart');
    expect(service.cart()?.items.map((item) => item.id)).toEqual(['guest-item']);
  });

  function storeGuestCart(items: Cart['items']): void {
    localStorage.setItem(guestStorageKey, JSON.stringify(cart('guest-cart', items)));
  }

  function cart(id: string, items: Cart['items']): Cart {
    return {
      id,
      items,
      createdAt: '2026-09-11T00:00:00Z',
      updatedAt: '2026-09-11T00:00:00Z'
    };
  }
});
