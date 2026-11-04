import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';

import { CartApiService } from './cart-api.service';
import { CartItem } from './cart.models';

@Component({
  selector: 'wwt-cart',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss'
})
export class CartComponent implements OnInit {
  private readonly cartApi = inject(CartApiService);

  readonly cart = this.cartApi.cart;
  readonly itemCount = this.cartApi.itemCount;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly pendingItemId = signal<string | null>(null);
  readonly clearing = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.cartApi.getCart().pipe(
      catchError(() => {
        this.error.set('Impossible de charger votre panier pour le moment.');
        return of(null);
      }),
      finalize(() => this.loading.set(false))
    ).subscribe();
  }

  changeQuantity(item: CartItem, delta: number): void {
    if (
      delta > 0 &&
      item.availableQuantity != null &&
      item.quantity >= item.availableQuantity
    ) {
      this.error.set(this.quantityLimitMessage(item.availableQuantity));
      return;
    }

    const quantity = item.quantity + delta;

    if (
      quantity < 1 ||
      this.pendingItemId() !== null ||
      this.clearing()
    ) {
      return;
    }

    this.error.set(null);
    this.pendingItemId.set(item.id);

    this.cartApi.updateItem(item.id, { quantity }).pipe(
      catchError((failure: unknown) => {
        this.error.set(this.mutationErrorMessage(failure, 'modifier la quantité'));

        if (this.requiresReconciliation(failure)) {
          return this.cartApi.getCart().pipe(catchError(() => of(null)));
        }

        return of(null);
      }),
      finalize(() => this.pendingItemId.set(null))
    ).subscribe();
  }

  remove(item: CartItem): void {
    if (
      this.pendingItemId() !== null ||
      this.clearing()
    ) {
      return;
    }

    this.error.set(null);
    this.pendingItemId.set(item.id);

    this.cartApi.removeItem(item.id).pipe(
      catchError((failure: unknown) => {
        this.error.set(this.mutationErrorMessage(failure, 'retirer cet article'));
        if (this.requiresReconciliation(failure)) {
          return this.cartApi.getCart().pipe(catchError(() => of(null)));
        }
        return of(null);
      }),
      finalize(() => this.pendingItemId.set(null))
    ).subscribe();
  }

  clear(): void {
    if (
      this.clearing() ||
      this.pendingItemId() !== null
    ) {
      return;
    }

    this.error.set(null);
    this.clearing.set(true);

    this.cartApi.clearCart().pipe(
      catchError((failure: unknown) => {
        this.error.set(this.mutationErrorMessage(failure, 'vider votre panier'));
        return of(null);
      }),
      finalize(() => this.clearing.set(false))
    ).subscribe();
  }

  private availableQuantityFrom(failure: unknown): number | null {
    if (!(failure instanceof HttpErrorResponse) || !this.isRecord(failure.error)) return null;
    const availableQuantity = failure.error['availableQuantity'];
    return typeof availableQuantity === 'number' && Number.isFinite(availableQuantity)
      ? availableQuantity
      : null;
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }

  private requiresReconciliation(failure: unknown): boolean {
    return failure instanceof HttpErrorResponse && (failure.status === 404 || failure.status === 409);
  }

  private mutationErrorMessage(failure: unknown, action: string): string {
    if (!(failure instanceof HttpErrorResponse)) return `Impossible de ${action}.`;

    const availableQuantity = this.availableQuantityFrom(failure);
    if (failure.status === 409 && availableQuantity != null) {
      return this.quantityLimitMessage(availableQuantity);
    }

    switch (failure.status) {
      case 0:
        return 'Le service panier est momentanément inaccessible.';
      case 400:
        return 'La quantité demandée est invalide.';
      case 401:
        return 'Votre session a expiré. Reconnectez-vous pour continuer.';
      case 403:
        return 'Vous n’êtes pas autorisé à modifier ce panier.';
      case 404:
        return 'Cet article n’est plus disponible dans votre panier. Le panier a été actualisé.';
      case 409:
        return 'Le panier a changé entre-temps. Il a été actualisé.';
      default:
        return `Impossible de ${action} pour le moment.`;
    }
  }

  private quantityLimitMessage(availableQuantity: number): string {
    return `Quantité maximale atteinte. Maximum disponible : ${availableQuantity}.`;
  }
}
