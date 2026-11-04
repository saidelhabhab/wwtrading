import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { CartApiService } from './features/cart/cart-api.service';
import { WishlistApiService } from './features/wishlist/wishlist-api.service';
import { SeoService } from './core/seo/seo.service';

interface ProductMenuLink {
  readonly label: string;
  readonly description: string;
  readonly queryParams: Readonly<Record<string, string>>;
}

interface ProductMenuColumn {
  readonly heading: string;
  readonly links: readonly ProductMenuLink[];
}

/**
 * Header product categories, grouped into two explicit columns. Each entry maps to a
 * real backend filter on GET /api/products, so the catalog filters server-side.
 */
const PRODUCT_MENU_COLUMNS: readonly ProductMenuColumn[] = [
  {
    heading: 'Imprimantes',
    links: [
      {
        label: 'Imprimantes Couleur',
        description: 'Impression couleur professionnelle',
        queryParams: { type: 'PRINTER', colorMode: 'COLOR' }
      },
      {
        label: 'Imprimantes Noir & Blanc',
        description: 'Impression monochrome professionnelle',
        queryParams: { type: 'PRINTER', colorMode: 'MONOCHROME' }
      },
      {
        label: 'Machines de production / Presse numérique',
        description: 'Presses numériques de production',
        queryParams: { type: 'PRINTER', machineCategory: 'DIGITAL_PRESS' }
      }
    ]
  },
  {
    heading: 'Équipements',
    links: [
      {
        label: 'Matériel de finition',
        description: 'Équipements de finition et façonnage',
        queryParams: { type: 'PRINTER', machineCategory: 'FINISHING_MACHINE' }
      },
      {
        label: 'Pièces de rechange',
        description: 'Pièces pour entretenir vos équipements',
        queryParams: { type: 'SPARE_PART' }
      },
      {
        label: 'Machines grand format / Ecosolver',
        description: 'Impression grand format écosolvant',
        queryParams: { type: 'PRINTER', machineCategory: 'GRAND_FORMAT_ECOSOLVENT' }
      }
    ]
  }
];

@Component({
  selector: 'wwt-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);
  protected readonly auth = inject(AuthService);
  protected readonly cartApi = inject(CartApiService);
  protected readonly wishlistApi = inject(WishlistApiService);
  protected readonly cartItemCount = this.cartApi.itemCount;
  protected readonly isDashboardWorkspace = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => isDashboardWorkspaceUrl(event.urlAfterRedirects)),
      startWith(isDashboardWorkspaceUrl(this.router.url))
    ),
    { initialValue: isDashboardWorkspaceUrl(this.router.url) }
  );
  protected readonly canAccessAccount = computed(() => this.auth.roles().length > 0);
  protected readonly dashboardRoute = computed(() => AuthService.resolveDashboardRoute(this.auth.roles()));
  /** Role-scoped account route; null for an unsupported role, which falls back to the dashboard. */
  protected readonly accountRoute = computed(() => AuthService.resolveAccountRoute(this.auth.roles()));
  /**
   * Order tracking entry point: authenticated users with an order history
   * (CLIENT / ADMIN / MANAGER / COMMERCIAL) go to their existing order list,
   * guests (and any role without order access) go to the public token-based
   * tracking page /suivi-commande.
   */
  protected readonly orderTrackingRoute = computed(() => {
    const roles = this.auth.roles();
    return roles.includes('CLIENT') || roles.includes('ADMIN') || roles.includes('MANAGER') || roles.includes('COMMERCIAL')
      ? '/orders'
      : '/suivi-commande';
  });
  protected readonly authActionPending = signal(false);
  protected readonly authActionFailed = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly scrolled = signal(false);
  protected readonly currentYear = new Date().getFullYear();
  protected readonly productMenuColumns = PRODUCT_MENU_COLUMNS;
  private readonly productsMenu = viewChild<ElementRef<HTMLDetailsElement>>('productsMenu');
  private readonly servicesMenu = viewChild<ElementRef<HTMLDetailsElement>>('servicesMenu');

  /**
   * Native <details> toggles itself, so only the cross-menu behaviour is handled here:
   * opening one menu closes the other. Nothing calls preventDefault(), which keeps
   * default keyboard activation of <summary> intact.
   */
  protected onProductMenuToggle(): void {
    this.closeOtherMenu(this.productsMenu()?.nativeElement, this.servicesMenu()?.nativeElement);
  }

  protected onServicesMenuToggle(): void {
    this.closeOtherMenu(this.servicesMenu()?.nativeElement, this.productsMenu()?.nativeElement);
  }

  private closeOtherMenu(opened: HTMLDetailsElement | undefined, other: HTMLDetailsElement | undefined): void {
    if (opened?.open && other?.open) other.open = false;
  }

  /** Closes the menus after a link is activated, then closes the mobile navigation. */
  protected closeProductMenu(menu: HTMLDetailsElement): void {
    menu.open = false;
    this.closeMenu();
  }

  protected closeServicesMenu(menu: HTMLDetailsElement): void {
    menu.open = false;
    this.closeMenu();
  }

  @HostListener('document:click', ['$event'])
  protected closeMenusOnOutsideClick(event: MouseEvent): void {
    const target = event.target as Node | null;
    if (!target) return;
    this.closeMenuWhenOutside(this.productsMenu()?.nativeElement, target);
    this.closeMenuWhenOutside(this.servicesMenu()?.nativeElement, target);
  }

  private closeMenuWhenOutside(menu: HTMLDetailsElement | undefined, target: Node): void {
    if (menu?.open && !menu.contains(target)) menu.open = false;
  }

  constructor() {
    this.seo.applyRoute(this.router.url);
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd)
    ).subscribe((event) => {
      // Apply after the Router's TitleStrategy so route titles cannot overwrite
      // the more specific catalogue/filter metadata on same-component navigation.
      queueMicrotask(() => this.seo.applyRoute(event.urlAfterRedirects));
    });

    effect(() => {
      if (!this.auth.authenticated()) return;
      this.cartApi.getCart().subscribe({ error: () => undefined });
      this.wishlistApi.getCurrentWishlist().subscribe({ error: () => undefined });
    });
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  protected closeMenuOnEscape(): void {
    this.closeMenu();
    this.closeAllDropdowns();
  }

  /** Closes both dropdowns unconditionally. */
  private closeAllDropdowns(): void {
    const products = this.productsMenu()?.nativeElement;
    const services = this.servicesMenu()?.nativeElement;
    if (products) products.open = false;
    if (services) services.open = false;
  }

  @HostListener('window:scroll')
  protected updateScrolledState(): void {
    this.scrolled.set(window.scrollY > 160);
  }

  protected scrollToTop(): void {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' });
  }

  protected login(): Promise<void> {
    this.closeMenu();
    return this.runAuthAction(() => this.auth.login(this.router.url));
  }

  protected logout(): Promise<void> {
    this.closeMenu();
    return this.runAuthAction(() => this.auth.logout());
  }

  private async runAuthAction(action: () => Promise<void>): Promise<void> {
    if (this.authActionPending()) return;
    this.authActionPending.set(true);
    this.authActionFailed.set(false);
    try {
      await action();
    } catch {
      this.authActionFailed.set(true);
    } finally {
      this.authActionPending.set(false);
    }
  }
}

function isDashboardWorkspaceUrl(url: string): boolean {
  return /^\/(admin|manager|commercial|technician|client)(?:\/|$)/.test(url)
    || /^\/orders(?:[/?#]|$)/.test(url)
    || /^\/devis\/mes-demandes(?:[/?#]|$)/.test(url)
    || /^\/notifications(?:[/?#]|$)/.test(url);
}
