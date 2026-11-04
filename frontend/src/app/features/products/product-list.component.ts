import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, map, of } from 'rxjs';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AuthService } from '../../core/auth/auth.service';
import { CartApiService } from '../cart/cart-api.service';
import { WishlistApiService } from '../wishlist/wishlist-api.service';
import { Wishlist } from '../wishlist/wishlist.models';
import { SeoService } from '../../core/seo/seo.service';
import { ProductApiService } from './product-api.service';
import {
  MACHINE_CATEGORIES,
  MACHINE_CATEGORY_LABELS,
  MachineCategory,
  Product,
  ProductColorMode,
  ProductFilters,
  ProductImage,
  PRODUCT_COLOR_MODES,
  PRODUCT_COLOR_MODE_LABELS,
  PRODUCT_TYPE_LABELS,
  PRODUCT_TYPES,
  ProductType
} from './product.models';

@Component({
  selector: 'wwt-product-list',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss'
})
export class ProductListComponent implements OnInit {
  private readonly productApi = inject(ProductApiService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly seo = inject(SeoService);
  private readonly cartApi = inject(CartApiService);
  private readonly wishlistApi = inject(WishlistApiService);

  protected readonly catalogChips: readonly {
    readonly label: string;
    readonly icon: string;
    readonly queryParams: Readonly<Record<string, string>>;
  }[] = [
    { label: 'Tous les produits', icon: 'apps', queryParams: {} },
    { label: 'Imprimantes Couleur', icon: 'palette', queryParams: { type: 'PRINTER', colorMode: 'COLOR' } },
    { label: 'Imprimantes Noir & Blanc', icon: 'monochrome_photos', queryParams: { type: 'PRINTER', colorMode: 'MONOCHROME' } },
    { label: 'Presses numériques', icon: 'print', queryParams: { type: 'PRINTER', machineCategory: 'DIGITAL_PRESS' } },
    { label: 'Grand Format', icon: 'aspect_ratio', queryParams: { type: 'PRINTER', machineCategory: 'GRAND_FORMAT_ECOSOLVENT' } },
    { label: 'Matériel de finition', icon: 'content_cut', queryParams: { type: 'PRINTER', machineCategory: 'FINISHING_MACHINE' } },
    { label: 'Toners', icon: 'invert_colors', queryParams: { type: 'TONER' } },
    { label: 'Consommables', icon: 'inventory_2', queryParams: { type: 'CONSUMABLE' } },
    { label: 'Pièces détachées', icon: 'build', queryParams: { type: 'SPARE_PART' } }
  ];

  protected isChipActive(chipParams: Readonly<Record<string, string>>): boolean {
    const current = this.filterParams();
    const hasAnyCurrentFilter = Boolean(current.type || current.category || current.colorMode);
    if (Object.keys(chipParams).length === 0) {
      return !hasAnyCurrentFilter;
    }
    if (chipParams['type'] && current.type !== chipParams['type']) return false;
    if (chipParams['colorMode'] && current.colorMode !== chipParams['colorMode']) return false;
    const cat = chipParams['machineCategory'] ?? chipParams['category'];
    if (cat && current.category !== cat) return false;
    return true;
  }

  protected readonly products = signal<readonly Product[]>([]);
  /** Active catalogue filters, read from the URL and applied by the backend. */
  protected readonly catalogFilters = signal<ProductFilters>({});
  /** Heading shown when the catalog is opened from a header product category. */
  protected readonly activeCatalogFilterLabel = computed<string | null>(() => {
    const { type, category, colorMode } = this.filterParams();
    if (type === 'PRINTER' && colorMode === 'COLOR') return 'Imprimantes Couleur';
    if (type === 'PRINTER' && colorMode === 'MONOCHROME') return 'Imprimantes Noir & Blanc';
    if (type === 'PRINTER' && category === 'DIGITAL_PRESS') return 'Machines de production / Presse numérique';
    if (category) return MACHINE_CATEGORY_LABELS[category];
    if (colorMode) return PRODUCT_COLOR_MODE_LABELS[colorMode];
    if (type) return PRODUCT_TYPE_LABELS[type];
    return null;
  });
  protected readonly productImages = signal<Readonly<Record<string, readonly ProductImage[]>>>({});
  protected readonly imageRequests = signal<Readonly<Record<string, boolean>>>({});
  protected readonly loadedImages = signal<Readonly<Record<string, boolean>>>({});
  protected readonly failedImages = signal<Readonly<Record<string, boolean>>>({});
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pageNumber = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly numberOfElements = signal(0);
  protected readonly firstPage = signal(true);
  protected readonly lastPage = signal(true);
  protected readonly canManageProducts = computed(() =>
    this.authService.roles().some((role) =>
      role === 'ADMIN' || role === 'MANAGER' || role === 'COMMERCIAL'
    )
);
  protected readonly togglingProductId = signal<string | null>(null);
  protected readonly managementMode = this.route.snapshot.data['mode'] === 'management';
  protected readonly managementBaseRoute = this.route.snapshot.data['managementBaseRoute'] as string | undefined;
  protected readonly wishlist = signal<Wishlist | null>(null);
  protected readonly wishlistLoading = signal(false);
  protected readonly wishlistPending = signal<string | null>(null);
  protected readonly cartPending = signal<string | null>(null);
  protected readonly actionFeedback = signal<string | null>(null);
  protected readonly actionFeedbackProductId = signal<string | null>(null);
  protected readonly actionFeedbackFailed = signal(false);
  protected readonly activeCardId = signal<string | null>(null);

  protected onCardTouch(productId: string): void {
    this.activeCardId.set(productId);
  }

  @HostListener('document:touchstart', ['$event'])
  protected onDocumentTouch(event: TouchEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target?.closest('.card')) {
      this.activeCardId.set(null);
    }
  }

  protected addProductRoute(): string {
    if (this.managementBaseRoute) {
      return `${this.managementBaseRoute}/new`;
    }

    const roles = this.authService.roles();
    if (roles.includes('ADMIN')) return '/admin/products/new';
    if (roles.includes('MANAGER')) return '/manager/products/new';
    return '/commercial/products/new';
  }

  protected editProductRoute(productId: string): string {
    const baseRoute = this.managementBaseRoute ?? (
      this.authService.roles().includes('ADMIN')
        ? '/admin/products'
        : this.authService.roles().includes('MANAGER')
          ? '/manager/products'
          : '/commercial/products'
    );
    return `${baseRoute}/${encodeURIComponent(productId)}/edit`;
  }

  ngOnInit(): void {
    this.applyCatalogFilters();
    this.load(0);
    if (!this.managementMode) this.loadWishlist();
    // React to header category links that re-target the catalog without recreating the route.
    this.route.queryParamMap.subscribe(() => {
      const previous = JSON.stringify(this.catalogFilters());
      this.applyCatalogFilters();
      if (JSON.stringify(this.catalogFilters()) !== previous) this.load(0);
    });
  }

  /**
   * Reads the header product-category query parameters. Filters are handed to the
   * API so the database filters before pagination.
   */
  private applyCatalogFilters(): void {
    const params = this.route.snapshot.queryParamMap;
    const filters: ProductFilters = {
      type: this.normalizeProductType(params.get('type')),
      machineCategory: this.normalizeMachineCategory(
        params.get('machineCategory') ?? params.get('category')
      ),
      colorMode: this.normalizeColorMode(params.get('colorMode'))
    };
    this.catalogFilters.set(filters);
    if (!this.managementMode) this.seo.setCatalog(filters);
  }

  private normalizeProductType(value: string | null): ProductType | undefined {
    return value && (PRODUCT_TYPES as readonly string[]).includes(value)
      ? (value as ProductType)
      : undefined;
  }

  private normalizeMachineCategory(value: string | null): MachineCategory | undefined {
    return value && (MACHINE_CATEGORIES as readonly string[]).includes(value)
      ? (value as MachineCategory)
      : undefined;
  }

  private normalizeColorMode(value: string | null): ProductColorMode | undefined {
    return value && (PRODUCT_COLOR_MODES as readonly string[]).includes(value)
      ? (value as ProductColorMode)
      : undefined;
  }

  /** The resolved query parameters, used for headings and reset links. */
  private filterParams(): {
    type: ProductType | null;
    category: MachineCategory | null;
    colorMode: ProductColorMode | null;
  } {
    const params = this.route.snapshot.queryParamMap;
    return {
      type: this.normalizeProductType(params.get('type')) ?? null,
      category: this.normalizeMachineCategory(
        params.get('machineCategory') ?? params.get('category')
      ) ?? null,
      colorMode: this.normalizeColorMode(params.get('colorMode')) ?? null
    };
  }

  protected canUseCustomerFeatures(): boolean {
    return this.authService.roles().some((role) =>
      role === 'ADMIN' || role === 'MANAGER' || role === 'COMMERCIAL' || role === 'CLIENT'
    );
  }

  protected showCustomerActions(): boolean {
    return !this.authService.authenticated() || this.canUseCustomerFeatures();
  }

  protected inWishlist(reference: string): boolean {
    return this.wishlist()?.items.some((item) => item.productReference === reference) ?? false;
  }

  protected addProductToCart(product: Product): void {
    if (this.cartPending() || !product.active) return;
    this.cartPending.set(product.id);
    this.actionFeedbackProductId.set(product.id);
    this.actionFeedback.set(null);
    this.actionFeedbackFailed.set(false);
    this.cartApi.addItem({ productReference: product.reference, quantity: 1 }, product.availableQuantity).pipe(
      catchError((failure: unknown) => {
        this.actionFeedbackFailed.set(true);
        this.actionFeedback.set(this.cartActionError(failure, product.availableQuantity));
        return of(null);
      }),
      finalize(() => this.cartPending.set(null))
    ).subscribe((cart) => {
      if (cart) this.actionFeedback.set('Produit ajouté au panier.');
    });
  }

  protected toggleWishlistProduct(product: Product): void {
    if (this.wishlistPending() || !this.wishlist()) return;
    const item = this.wishlist()!.items.find((candidate) => candidate.productReference === product.reference);
    this.wishlistPending.set(product.id);
    this.actionFeedbackProductId.set(product.id);
    this.actionFeedback.set(null);
    this.actionFeedbackFailed.set(false);

    type WishlistActionResult =
      | { type: 'remove'; itemId: string }
      | { type: 'add'; wishlist: Wishlist }
      | null;

    const request$: Observable<WishlistActionResult> = item
      ? this.wishlistApi.removeProduct(item.id).pipe(
          map(() => ({ type: 'remove', itemId: item.id } satisfies WishlistActionResult))
        )
      : this.wishlistApi.addProduct(product.reference).pipe(
          map((wishlist) => ({ type: 'add', wishlist } satisfies WishlistActionResult))
        );

    request$.pipe(
      catchError(() => {
        this.actionFeedbackFailed.set(true);
        this.actionFeedback.set('Impossible de modifier votre liste de souhaits.');
        return of(null as WishlistActionResult);
      }),
      finalize(() => this.wishlistPending.set(null))
    ).subscribe((result: WishlistActionResult) => {
      if (!result) return;
      if (result.type === 'remove') {
        this.wishlist.update((current) => current ? { ...current, items: current.items.filter((candidate) => candidate.id !== result.itemId) } : current);
        this.actionFeedback.set('Produit retiré des souhaits.');
        return;
      }
      this.wishlist.set(result.wishlist);
      this.actionFeedback.set('Produit ajouté aux souhaits.');
    });
  }

  private loadWishlist(): void {
    this.wishlistLoading.set(true);
    this.wishlistApi.getCurrentWishlist().pipe(finalize(() => this.wishlistLoading.set(false))).subscribe({
      next: (wishlist) => this.wishlist.set(wishlist),
      error: () => this.wishlist.set(null)
    });
  }

  protected load(page = this.pageNumber()): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) return;
    this.loading.set(true);
    this.error.set(null);
    this.productApi.list(page, 20, 'name,asc', this.catalogFilters()).pipe(
      catchError((failure: unknown) => {
        this.error.set(this.errorMessage(failure));
        return of(null);
      }),
      finalize(() => this.loading.set(false))
    ).subscribe((response) => {
      this.products.set(response?.content ?? []);
      if (!response) return;
      this.loadProductImages(response.content);
      this.pageNumber.set(response.number);
      this.totalPages.set(response.totalPages);
      this.totalElements.set(response.totalElements);
      this.pageSize.set(response.size);
      this.numberOfElements.set(response.numberOfElements);
      this.firstPage.set(response.first);
      this.lastPage.set(response.last);
    });
  }

  protected mainImage(productId: string): ProductImage | null {
    const images = this.productImages()[productId] ?? [];
    return images.find((image) => image.main) ?? images[0] ?? null;
  }

  protected imageUrl(image: ProductImage): string {
    return this.productApi.resolveImageUrl(image.imageUrl);
  }

  protected imageLoading(productId: string): boolean {
    return this.imageRequests()[productId] === true;
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

  protected toggleProduct(product: Product): void {
    if (!this.canManageProducts() || this.togglingProductId() === product.id) {
      return;
    }
    const action = product.active ? 'désactiver' : 'activer';
    if (!window.confirm(`Voulez-vous ${action} le produit « ${product.name} » ?`)) {
      return;
    }

    this.error.set(null);
    this.togglingProductId.set(product.id);

    const request = product.active
      ? this.productApi.deactivateProduct(product.id)
      : this.productApi.activateProduct(product.id);

    request.pipe(
      catchError((failure: unknown) => {
        this.error.set(this.errorMessage(failure));
        return of(null);
      }),
      finalize(() => this.togglingProductId.set(null))
    ).subscribe((updatedProduct) => {
      if (!updatedProduct) {
        return;
      }

      this.products.update((items) => items.map((item) => item.id === updatedProduct.id ? updatedProduct : item));
    });
  }

  protected formatPrice(price: number): string {
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(price) + ' DH';
  }

  protected productTypeLabel(type: ProductType): string { return PRODUCT_TYPE_LABELS[type]; }

  protected machineCategoryLabel(category: MachineCategory | null): string | null {
    return category ? MACHINE_CATEGORY_LABELS[category] : null;
  }

  protected warrantyLabel(product: Product): string | null {
    const parts: string[] = [];
    if (product.warrantyMonths !== null) parts.push(`${product.warrantyMonths} mois`);
    if (product.warrantyCopies !== null) parts.push(`${new Intl.NumberFormat('fr-MA').format(product.warrantyCopies)} copies`);
    return parts.length ? parts.join(' / ') : null;
  }

  protected rangeStart(): number { return this.totalElements() === 0 ? 0 : this.pageNumber() * this.pageSize() + 1; }
  protected rangeEnd(): number { return this.pageNumber() * this.pageSize() + this.numberOfElements(); }

  private errorMessage(failure: unknown): string {
    if (failure instanceof HttpErrorResponse && failure.status === 0) {
      return 'Le catalogue est momentanément indisponible. Veuillez réessayer.';
    }
    if (failure instanceof HttpErrorResponse && failure.status === 401) return 'Votre session est requise pour cette action.';
    if (failure instanceof HttpErrorResponse && failure.status === 403) return 'Vous n’avez pas les permissions nécessaires.';
    return 'Les produits ne peuvent pas être chargés pour le moment.';
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
    return 'Impossible d’ajouter ce produit au panier.';
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }

  private loadProductImages(products: readonly Product[]): void {
    for (const product of products) {
      if (product.images) {
        const ordered = [...product.images].sort((left, right) => left.displayOrder - right.displayOrder);
        this.productImages.update((state) => ({ ...state, [product.id]: ordered }));
        continue;
      }
      this.imageRequests.update((state) => ({ ...state, [product.id]: true }));
      this.productApi.getImages(product.id).pipe(
        catchError(() => of([] as readonly ProductImage[])),
        finalize(() => this.imageRequests.update((state) => ({ ...state, [product.id]: false })))
      ).subscribe((images) => {
        const ordered = [...images].sort((left, right) => left.displayOrder - right.displayOrder);
        this.productImages.update((state) => ({ ...state, [product.id]: ordered }));
      });
    }
  }
}
