import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { catchError, finalize, map, of } from 'rxjs';

import { WishlistApiService } from './wishlist-api.service';
import { Wishlist } from './wishlist.models';
import { CartApiService } from '../cart/cart-api.service';

@Component({
  selector: 'wwt-wishlist',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './wishlist.component.html',
  styleUrl: './wishlist.component.scss'
})
export class WishlistComponent implements OnInit {
  private readonly wishlistApi = inject(WishlistApiService);
  private readonly cartApi = inject(CartApiService);

  protected readonly wishlist = signal<Wishlist | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly removingItemId = signal<string | null>(null);
  protected readonly clearing = signal(false);
  protected readonly addingProductReference = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.wishlistApi.getCurrentWishlist().pipe(
      catchError(() => {
        this.error.set('La liste de souhaits ne peut pas être chargée pour le moment.');
        return of(null);
      }),
      finalize(() => this.loading.set(false))
    ).subscribe((wishlist) => this.wishlist.set(wishlist));
  }

  protected removeItem(itemId: string): void {
    if (this.removingItemId() || this.clearing()) return;
    this.actionError.set(null);
    this.removingItemId.set(itemId);
    this.wishlistApi.removeProduct(itemId).pipe(
      map(() => true),
      catchError((failure: unknown) => {
        this.actionError.set(this.actionErrorMessage(failure, 'La référence ne peut pas être retirée pour le moment.'));
        return of(false);
      }),
      finalize(() => this.removingItemId.set(null))
    ).subscribe((removed) => {
      if (!removed) return;
      const current = this.wishlist();
      if (current) {
        this.wishlist.set({ ...current, items: current.items.filter((item) => item.id !== itemId) });
      }
    });
  }

  protected clearWishlist(): void {
    if (this.clearing() || this.removingItemId()) return;
    if (!window.confirm('Voulez-vous vraiment vider votre liste de souhaits ?')) return;
    this.actionError.set(null);
    this.clearing.set(true);
    this.wishlistApi.clearWishlist().pipe(
      map(() => true),
      catchError((failure: unknown) => {
        this.actionError.set(this.actionErrorMessage(failure, 'La liste de souhaits ne peut pas être vidée pour le moment.'));
        return of(false);
      }),
      finalize(() => this.clearing.set(false))
    ).subscribe((cleared) => {
      if (!cleared) return;
      const current = this.wishlist();
      if (current) this.wishlist.set({ ...current, items: [] });
    });
  }

  protected addToCart(productReference: string): void {
    if (this.addingProductReference()) return;
    this.actionError.set(null);
    this.addingProductReference.set(productReference);
    this.cartApi.addItem({ productReference, quantity: 1 }).pipe(
      catchError((failure: unknown) => {
        this.actionError.set(this.actionErrorMessage(failure, 'Cette référence ne peut pas être ajoutée au panier pour le moment.'));
        return of(null);
      }),
      finalize(() => this.addingProductReference.set(null))
    ).subscribe();
  }

  private actionErrorMessage(failure: unknown, fallback: string): string {
    if (!(failure instanceof HttpErrorResponse)) return fallback;
    if (failure.status === 404) return 'Cette référence n’existe plus dans votre liste de souhaits.';
    if (failure.status === 409) return 'Cette action ne peut pas être effectuée pour le moment.';
    if (failure.status === 0) return 'Le service est momentanément indisponible. Veuillez réessayer.';
    return fallback;
  }
}
