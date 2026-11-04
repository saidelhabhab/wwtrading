import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, Injector, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, finalize, of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { CartApiService } from '../cart/cart-api.service';
import { WishlistApiService } from '../wishlist/wishlist-api.service';
import { Wishlist, WishlistItem } from '../wishlist/wishlist.models';
import { ProductApiService } from './product-api.service';
import { MACHINE_CATEGORY_LABELS, Product, ProductImage, PRODUCT_COLOR_MODE_LABELS, PRODUCT_TYPE_LABELS } from './product.models';
import { SeoService } from '../../core/seo/seo.service';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

@Component({
  selector: 'wwt-product-detail',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss'
})
export class ProductDetailComponent implements OnInit {
  protected readonly PRODUCT_TYPE_LABELS = PRODUCT_TYPE_LABELS;
  protected readonly MACHINE_CATEGORY_LABELS = MACHINE_CATEGORY_LABELS;
  protected readonly PRODUCT_COLOR_MODE_LABELS = PRODUCT_COLOR_MODE_LABELS;
  /**
   * Printer colour mode is only meaningful for printers, and the API returns null
   * when it is not applicable, so both conditions gate the display.
   */
  protected readonly colorModeLabel = computed<string | null>(() => {
    const item = this.product();
    if (!item || item.type !== 'PRINTER' || !item.colorMode) return null;
    return PRODUCT_COLOR_MODE_LABELS[item.colorMode] ?? null;
  });
  protected readonly machineCategoryLabel = computed<string | null>(() => {
    const category = this.product()?.machineCategory;
    return category ? MACHINE_CATEGORY_LABELS[category] ?? null : null;
  });
  private readonly route = inject(ActivatedRoute);
  private readonly productApi = inject(ProductApiService);
  private readonly seo = inject(SeoService);
  private readonly injector = inject(Injector);
  private readonly authService = inject(AuthService);
  protected readonly authenticated = this.authService.authenticated;

  protected readonly product = signal<Product | null>(null);
  protected readonly images = signal<readonly ProductImage[]>([]);
  protected readonly selectedImage = signal<ProductImage | null>(null);
  protected readonly imagesLoading = signal(false);
  protected readonly imageApiFailed = signal(false);
  protected readonly loadedImages = signal<Readonly<Record<string, boolean>>>({});
  protected readonly failedImages = signal<Readonly<Record<string, boolean>>>({});
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canManageProducts = computed(() =>
    this.authService.roles().some((role) =>
      role === 'ADMIN' || role === 'MANAGER'
    )
  );
  protected readonly canUseCart = computed(() =>
    !this.authService.authenticated() || this.authService.roles().some((role) =>
      role === 'ADMIN' || role === 'MANAGER' || role === 'COMMERCIAL' || role === 'CLIENT'
    )
  );
  protected readonly canUseWishlist = this.canUseCart;
  protected readonly togglingStatus = signal(false);
  protected readonly addingToCart = signal(false);
  protected readonly cartFeedback = signal<string | null>(null);
  protected readonly cartAddFailed = signal(false);
  protected readonly wishlist = signal<Wishlist | null>(null);
  protected readonly wishlistLoading = signal(false);
  protected readonly wishlistUnavailable = signal(false);
  protected readonly wishlistPending = signal(false);
  protected readonly wishlistFeedback = signal<string | null>(null);
  protected readonly wishlistActionFailed = signal(false);
  protected readonly wishlistItem = computed<WishlistItem | null>(() => {
    const reference = this.product()?.reference;
    if (!reference) return null;
    return this.wishlist()?.items.find((item) => item.productReference === reference) ?? null;
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.product.set(null);
    this.notFound.set(false);
    this.error.set(null);
    if (!id || !UUID_PATTERN.test(id)) {
      this.error.set('La référence du produit est invalide.');
      this.loading.set(false);
      this.seo.setNotFound();
      return;
    }

    this.loading.set(true);
    this.productApi.getById(id).pipe(
      catchError((failure: unknown) => {
        if (failure instanceof HttpErrorResponse && failure.status === 404) {
          this.notFound.set(true);
          this.seo.setNotFound();
        } else {
          this.error.set('Le produit ne peut pas être chargé pour le moment.');
        }
        return of(null);
      }),
      finalize(() => this.loading.set(false))
    ).subscribe((product) => {
      this.product.set(product);
      if (product) {
        this.seo.setProduct(product);
        this.loadImages(product.id);
        this.loadWishlist();
      }
    });
  }

  protected imageUrl(image: ProductImage): string {
    return this.productApi.resolveImageUrl(image.imageUrl);
  }

  protected selectImage(image: ProductImage): void {
    this.selectedImage.set(image);
  }

  protected selectedIndex(): number {
    const selected = this.selectedImage();
    return selected ? Math.max(0, this.images().findIndex((image) => image.id === selected.id)) : 0;
  }

  protected showPrevious(): void {
    const images = this.images();
    if (images.length < 2) return;
    this.selectedImage.set(images[(this.selectedIndex() - 1 + images.length) % images.length]);
  }

  protected showNext(): void {
    const images = this.images();
    if (images.length < 2) return;
    this.selectedImage.set(images[(this.selectedIndex() + 1) % images.length]);
  }

  protected imageLoaded(imageId: string): boolean {
    return this.loadedImages()[imageId] === true;
  }

  protected imageFailed(imageId: string): boolean {
    return this.failedImages()[imageId] === true;
  }

  protected markImageLoaded(imageId: string): void {
    this.loadedImages.update((state) => ({ ...state, [imageId]: true }));
  }

  protected markImageFailed(imageId: string): void {
    this.failedImages.update((state) => ({ ...state, [imageId]: true }));
  }

  protected formatPrice(price: number): string {
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(price) + ' DH';
  }

  protected formatCopies(copies: number): string { return `${new Intl.NumberFormat('fr-MA').format(copies)} copies`; }

  /**
   * Availability comes from StockEntity via the API. When the field is absent from
   * the payload it is reported as not provided rather than invented as zero.
   */
  protected availabilityLabel(): string {
    const item = this.product();
    if (!item || !item.active) return 'Indisponible';
    const available = item.availableQuantity;
    if (available === null || available === undefined) return 'Non renseignée';
    if (available <= 0) return 'Indisponible';
    return `${new Intl.NumberFormat('fr-MA').format(available)} en stock`;
  }

  protected managementProductRoute(path: string): string {
    const baseRoute = this.authService.roles().includes('ADMIN')
      ? '/admin/products'
      : '/manager/products';
    return `${baseRoute}/${path}`;
  }

  protected addToCart(): void {
    const item = this.product();
    if (!item) return;
    if (!item.active || item.availableQuantity === 0 || !this.canUseCart() || this.addingToCart()) return;

    this.addingToCart.set(true);
    this.cartFeedback.set(null);
    this.cartAddFailed.set(false);
    this.injector.get(CartApiService).addItem({ productReference: item.reference, quantity: 1 }, item.availableQuantity).pipe(
      catchError((failure: unknown) => {
        this.cartAddFailed.set(true);
        this.cartFeedback.set(this.cartActionError(failure, item.availableQuantity));
        return of(null);
      }),
      finalize(() => this.addingToCart.set(false))
    ).subscribe((cart) => {
      if (cart) this.cartFeedback.set('Produit ajouté au panier.');
    });
  }

  private cartActionError(failure: unknown, availableQuantity: number | null | undefined): string {
    if (failure instanceof HttpErrorResponse) {
      if (failure.status === 0) return 'Le service panier est momentanément indisponible.';
      if (failure.status === 401) return 'Votre session est requise pour ajouter ce produit.';
      if (failure.status === 403) return 'Vous n’avez pas les permissions nécessaires.';
      if (failure.status === 409 && this.isRecord(failure.error)) {
        const backendAvailableQuantity = failure.error['availableQuantity'];
        if (typeof backendAvailableQuantity === 'number') {
          return `Quantité maximale atteinte. Maximum disponible : ${backendAvailableQuantity}.`;
        }
      }
    }
    if (availableQuantity === 0) return 'Produit actuellement indisponible.';
    return 'Impossible d’ajouter ce produit au panier pour le moment.';
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }

  protected loadWishlist(): void {
    if (this.wishlistLoading()) return;

    this.wishlistLoading.set(true);
    this.wishlistUnavailable.set(false);
    this.wishlistFeedback.set(null);
    this.injector.get(WishlistApiService).getCurrentWishlist().pipe(
      catchError(() => {
        this.wishlistUnavailable.set(true);
        this.wishlistActionFailed.set(true);
        this.wishlistFeedback.set('Impossible de charger votre liste de souhaits pour le moment.');
        return of(null);
      }),
      finalize(() => this.wishlistLoading.set(false))
    ).subscribe((wishlist) => {
      if (wishlist) this.wishlist.set(wishlist);
    });
  }

  protected toggleWishlist(): void {
    const product = this.product();
    if (!product) return;
    if (!this.canUseWishlist() || this.wishlistLoading() || this.wishlistPending()) return;

    const api = this.injector.get(WishlistApiService);
    const existingItem = this.wishlistItem();
    const request = existingItem
      ? api.removeProduct(existingItem.id).pipe(switchMap(() => api.getCurrentWishlist()))
      : api.addProduct(product.reference);

    this.wishlistPending.set(true);
    this.wishlistActionFailed.set(false);
    this.wishlistFeedback.set(null);
    request.pipe(
      catchError(() => {
        this.wishlistActionFailed.set(true);
        this.wishlistFeedback.set(existingItem
          ? 'Impossible de retirer ce produit de vos souhaits pour le moment.'
          : 'Impossible d’ajouter ce produit à vos souhaits pour le moment.');
        return of(null);
      }),
      finalize(() => this.wishlistPending.set(false))
    ).subscribe((wishlist) => {
      if (!wishlist) return;
      this.wishlist.set(wishlist);
      this.wishlistFeedback.set(existingItem
        ? 'Produit retiré de vos souhaits.'
        : 'Produit ajouté à vos souhaits.');
    });
  }

  protected toggleProductStatus(): void {
    const item = this.product();
    if (!item || !this.canManageProducts() || this.togglingStatus()) {
      return;
    }
    const action = item.active ? 'désactiver' : 'activer';
    if (!window.confirm(`Voulez-vous ${action} le produit « ${item.name} » ?`)) {
      return;
    }

    this.error.set(null);
    this.togglingStatus.set(true);
    const request = item.active
      ? this.productApi.deactivateProduct(item.id)
      : this.productApi.activateProduct(item.id);

    request.pipe(
      catchError((failure: unknown) => {
        this.error.set(this.safeErrorMessage(failure));
        return of(null);
      }),
      finalize(() => this.togglingStatus.set(false))
    ).subscribe((updatedProduct) => {
      if (updatedProduct) {
        this.product.set(updatedProduct);
      }
    });
  }

  private safeErrorMessage(failure: unknown): string {
    if (failure instanceof HttpErrorResponse && failure.status === 0) {
      return 'Le changement de statut est momentanément indisponible. Veuillez réessayer.';
    }
    return 'Le statut du produit ne peut pas être modifié pour le moment.';
  }

  private loadImages(productId: string): void {
    this.imagesLoading.set(true);
    this.imageApiFailed.set(false);
    this.images.set([]);
    this.selectedImage.set(null);
    this.productApi.getImages(productId).pipe(
      catchError(() => {
        this.imageApiFailed.set(true);
        return of([] as readonly ProductImage[]);
      }),
      finalize(() => this.imagesLoading.set(false))
    ).subscribe((images) => {
      const ordered = [...images].sort((left, right) => left.displayOrder - right.displayOrder);
      this.images.set(ordered);
      this.selectedImage.set(ordered.find((image) => image.main) ?? ordered[0] ?? null);
      const product = this.product();
      const primaryImage = this.selectedImage();
      if (product) this.seo.setProduct(product, primaryImage ? this.imageUrl(primaryImage) : undefined);
    });
  }
}
