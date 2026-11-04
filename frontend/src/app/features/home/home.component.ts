import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnInit,
  PLATFORM_ID,
  computed,
  inject,
  signal
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { ProductApiService } from '../products/product-api.service';
import { Product, PRODUCT_TYPE_LABELS } from '../products/product.models';

interface FeaturedProduct {
  readonly product: Product;
  readonly imageUrl: string | null;
}

@Component({
  selector: 'wwt-home',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, AfterViewInit {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly productApi = inject(ProductApiService);
  private revealObserver: IntersectionObserver | null = null;

  protected readonly featuredProducts = signal<readonly FeaturedProduct[]>([]);
  protected readonly activeProductIndex = signal(0);
  protected readonly activeHeroProduct = computed(() =>
    this.featuredProducts()[this.activeProductIndex()] ?? null
  );
  protected readonly isSingleProduct = computed(() => this.featuredProducts().length === 1);
  protected readonly hasMultipleProducts = computed(() => this.featuredProducts().length > 1);
  protected readonly hasProducts = computed(() => this.featuredProducts().length > 0);
  protected readonly productTypeLabel = PRODUCT_TYPE_LABELS;
  protected readonly parallaxTransform = signal('translate3d(0px, 0px, 0px)');

  protected readonly whatsappUrl =
    'https://wa.me/212776886836?text=Bonjour%20WWTRADING%2C%20je%20souhaite%20un%20devis%20pour%20vos%20solutions%20d%27impression.';
  protected readonly phoneCallUrl = 'tel:+212776886836';

  protected onHeroMouseMove(event: MouseEvent): void {
    if (!isPlatformBrowser(this.platformId) || window.innerWidth < 1025) return;
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const normX = (event.clientX - centerX) / (rect.width / 2);
    const normY = (event.clientY - centerY) / (rect.height / 2);
    const x = Math.max(-8, Math.min(8, normX * 8));
    const y = Math.max(-6, Math.min(6, normY * 6));
    this.parallaxTransform.set(`translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0px)`);
  }

  protected onHeroMouseLeave(): void {
    this.parallaxTransform.set('translate3d(0px, 0px, 0px)');
  }

  ngOnInit(): void {
    this.loadFeaturedProducts();
  }

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId) || !('IntersectionObserver' in window)) {
      return;
    }

    const hostEl = this.host.nativeElement;
    hostEl.classList.add('js-reveal-active');

    this.revealObserver = new IntersectionObserver((entries, observer) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      }
    }, { rootMargin: '0px 0px -6% 0px', threshold: 0.08 });

    const elements = hostEl.querySelectorAll('[data-reveal], [data-reveal-services]') as NodeListOf<HTMLElement>;
    elements.forEach((element: HTMLElement) => {
      // If already in top viewport, make visible immediately
      if (element.getBoundingClientRect().top < window.innerHeight * 0.95) {
        element.classList.add('is-visible');
      } else {
        this.revealObserver?.observe(element);
      }
    });

    this.destroyRef.onDestroy(() => this.revealObserver?.disconnect());
  }

  protected selectHeroProduct(index: number): void {
    this.activeProductIndex.set(index);
  }

  protected productName(product: Product): string {
    return [product.brand, product.model || product.name].filter(Boolean).join(' ');
  }

  @HostListener('window:scroll')
  protected revealPassedSections(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const elements = this.host.nativeElement.querySelectorAll('[data-reveal]:not(.is-visible), [data-reveal-services]:not(.is-visible)') as NodeListOf<HTMLElement>;
    elements.forEach((element: HTMLElement) => {
      if (element.getBoundingClientRect().top < window.innerHeight * 0.95) {
        element.classList.add('is-visible');
        this.revealObserver?.unobserve(element);
      }
    });
  }

  private loadFeaturedProducts(): void {
    this.productApi.list(0, 4, 'updatedAt,desc')
      .pipe(
        map((page) => page.content.filter((product) => product.active).slice(0, 4)),
        switchMap((products) => {
          if (!products.length) return of([] as readonly FeaturedProduct[]);
          return forkJoin(products.map((product) =>
            this.productApi.getImages(product.id).pipe(
              map((images) => ({
                product,
                imageUrl: images.length
                  ? this.productApi.resolveImageUrl((images.find((image) => image.main) ?? images[0]).imageUrl)
                  : null
              })),
              catchError(() => of({ product, imageUrl: null }))
            )
          ));
        }),
        catchError(() => of([] as readonly FeaturedProduct[])),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((products) => {
        this.featuredProducts.set(products);
        this.activeProductIndex.set(0);
      });
  }
}
