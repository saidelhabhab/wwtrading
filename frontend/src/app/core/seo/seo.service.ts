import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

import { RUNTIME_CONFIG } from '../config/runtime-config';
import {
  MACHINE_CATEGORY_LABELS,
  PRODUCT_COLOR_MODE_LABELS,
  PRODUCT_TYPE_LABELS,
  type Product,
  type ProductFilters
} from '../../features/products/product.models';

interface PageSeo {
  readonly title: string;
  readonly description: string;
  readonly path: string;
  readonly image?: string;
  readonly type?: 'website' | 'product';
  readonly robots?: 'index,follow' | 'noindex,follow' | 'noindex,nofollow';
  readonly structuredData?: readonly Record<string, unknown>[];
}

const PUBLIC_PAGES: Readonly<Record<string, Omit<PageSeo, 'path'>>> = {
  '/': {
    title: 'WWTRADING | Solutions d’impression professionnelles au Maroc',
    description: 'WWTRADING accompagne les entreprises au Maroc pour leurs imprimantes professionnelles, location, maintenance, toners, consommables et pièces.',
    robots: 'index,follow'
  },
  '/products': {
    title: 'Imprimantes professionnelles et consommables | WWTRADING',
    description: 'Découvrez le catalogue WWTRADING : imprimantes professionnelles, toners, consommables, pièces de rechange et équipements d’impression.',
    robots: 'index,follow'
  },
  '/services/vente-machines': {
    title: 'Vente d’imprimantes professionnelles | WWTRADING',
    description: 'Découvrez les solutions WWTRADING pour équiper votre entreprise en imprimantes et systèmes multifonctions professionnels au Maroc.',
    robots: 'index,follow'
  },
  '/services/location': {
    title: 'Location d’imprimantes professionnelles | WWTRADING',
    description: 'Louez une solution d’impression professionnelle adaptée à votre activité avec l’accompagnement et l’expertise WWTRADING.',
    robots: 'index,follow'
  },
  '/services/maintenance': {
    title: 'Maintenance d’imprimantes professionnelles | WWTRADING',
    description: 'Maintenance préventive, corrective et assistance à distance pour préserver les performances de vos équipements d’impression.',
    robots: 'index,follow'
  },
  '/services/toners-consommables': {
    title: 'Toners et consommables pour imprimantes | WWTRADING',
    description: 'Trouvez les toners et consommables adaptés à vos équipements Konica Minolta, Develop et à vos besoins d’impression professionnels.',
    robots: 'index,follow'
  },
  '/services/pieces-rechange': {
    title: 'Pièces de rechange pour imprimantes | WWTRADING',
    description: 'Pièces mécaniques, électroniques et optiques destinées à l’entretien et à la réparation de vos équipements d’impression.',
    robots: 'index,follow'
  },
  '/services/support-technique': {
    title: 'Support technique pour systèmes d’impression | WWTRADING',
    description: 'Bénéficiez de l’assistance technique WWTRADING pour diagnostiquer, résoudre et prévenir les incidents de vos équipements.',
    robots: 'index,follow'
  },
  '/contact': {
    title: 'Contact et solutions d’impression au Maroc | WWTRADING',
    description: 'Contactez WWTRADING à Kénitra pour votre projet d’impression professionnelle, maintenance, location, consommables ou demande de devis.',
    robots: 'index,follow'
  },
  '/devis': {
    title: 'Demande de devis impression professionnelle | WWTRADING',
    description: 'Demandez un devis WWTRADING pour une imprimante professionnelle, une location, une maintenance ou des consommables adaptés à votre activité.',
    robots: 'index,follow'
  }
};

@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly document = inject(DOCUMENT);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly config = inject(RUNTIME_CONFIG);

  private get siteUrl(): string {
    const configured = this.config.siteUrl?.trim();
    if (configured) {
      try {
        const url = new URL(configured);
        if (
          url.protocol === 'https:'
          && url.pathname === '/'
          && !url.username
          && !url.password
          && !url.search
          && !url.hash
        ) return url.origin;
      } catch {
        // Invalid optional configuration falls back to the current browser origin.
      }
    }
    return this.document.location?.origin ?? '';
  }

  applyRoute(rawUrl: string): void {
    const [pathPart, query = ''] = rawUrl.split('?');
    const path = normalizePath(pathPart.split('#')[0]);

    if (path === '/products' && query) {
      const params = new URLSearchParams(query);
      const label = this.catalogLabel(params);
      this.apply({
        title: label ? `${label} | Catalogue WWTRADING` : PUBLIC_PAGES['/products'].title,
        description: label
          ? `Consultez les ${label.toLocaleLowerCase('fr')} disponibles dans le catalogue professionnel WWTRADING.`
          : PUBLIC_PAGES['/products'].description,
        path: '/products',
        robots: 'noindex,follow'
      });
      return;
    }

    const publicPage = PUBLIC_PAGES[path];
    if (publicPage) {
      const structuredData = path === '/'
        ? [this.organizationSchema(), this.websiteSchema()]
        : [this.breadcrumbSchema([{ name: 'Accueil', path: '/' }, { name: publicPage.title.split('|')[0].trim(), path }])];
      this.apply({ ...publicPage, path, structuredData });
      return;
    }

    if (/^\/products\/[^/]+$/.test(path)) {
      this.apply({
        title: 'Détail du produit | WWTRADING',
        description: 'Consultez les informations de ce produit dans le catalogue professionnel WWTRADING.',
        path,
        robots: 'index,follow'
      });
      return;
    }

    this.applyPrivatePage(path);
  }

  setCatalog(filters: ProductFilters): void {
    const hasFilters = Object.values(filters).some(Boolean);
    if (!hasFilters) {
      this.applyRoute('/products');
      return;
    }
    const label = filters.machineCategory
      ? MACHINE_CATEGORY_LABELS[filters.machineCategory]
      : filters.colorMode
        ? `${filters.type === 'PRINTER' ? 'Imprimantes' : 'Produits'} ${PRODUCT_COLOR_MODE_LABELS[filters.colorMode]}`
        : filters.type
          ? `${PRODUCT_TYPE_LABELS[filters.type]}s`
          : 'Produits';
    this.apply({
      title: `${label} | Catalogue WWTRADING`,
      description: `Consultez les ${label.toLocaleLowerCase('fr')} disponibles dans le catalogue professionnel WWTRADING.`,
      path: '/products',
      robots: 'noindex,follow'
    });
  }

  setProduct(product: Product, imageUrl?: string): void {
    const productName = uniqueWords([product.brand, product.model, product.name]).join(' ') || product.name;
    const typeLabel = PRODUCT_TYPE_LABELS[product.type];
    const description = conciseDescription(
      product.description,
      `${productName}, ${typeLabel.toLocaleLowerCase('fr')} référence ${product.reference}, présenté dans le catalogue professionnel WWTRADING.`
    );
    const path = `/products/${encodeURIComponent(product.id)}`;
    const image = imageUrl ? this.absoluteUrl(imageUrl) : undefined;
    const schema: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: productName,
      description,
      sku: product.reference,
      category: typeLabel,
      ...(product.brand ? { brand: { '@type': 'Brand', name: product.brand } } : {}),
      ...(product.model ? { model: product.model } : {}),
      ...(image ? { image: [image] } : {})
    };
    this.apply({
      title: `${productName} | WWTRADING`,
      description,
      path,
      image,
      type: 'product',
      robots: product.active ? 'index,follow' : 'noindex,follow',
      structuredData: [
        schema,
        this.breadcrumbSchema([
          { name: 'Accueil', path: '/' },
          { name: 'Produits', path: '/products' },
          { name: productName, path }
        ])
      ]
    });
  }

  setNotFound(): void {
    this.apply({
      title: 'Page introuvable | WWTRADING',
      description: 'La page demandée est introuvable. Retrouvez le catalogue et les services WWTRADING.',
      path: this.document.location?.pathname ?? '/404',
      robots: 'noindex,nofollow'
    }, false);
  }

  private applyPrivatePage(path: string): void {
    this.apply({
      title: this.title.getTitle() || 'Espace sécurisé | WWTRADING',
      description: 'Espace sécurisé WWTRADING.',
      path,
      robots: 'noindex,nofollow'
    }, false);
  }

  private apply(page: PageSeo, includeCanonical = true): void {
    const image = page.image ?? this.absoluteUrl('/assets/wwtrading-logo.png');
    const canonical = this.absoluteUrl(page.path);
    this.title.setTitle(page.title);
    this.setName('description', page.description);
    this.setName('robots', page.robots ?? 'index,follow');
    this.setProperty('og:title', page.title);
    this.setProperty('og:description', page.description);
    this.setProperty('og:type', page.type ?? 'website');
    this.setProperty('og:site_name', 'WWTRADING');
    this.setProperty('og:locale', 'fr_MA');
    this.setProperty('og:url', canonical);
    this.setProperty('og:image', image);
    this.setName('twitter:card', 'summary_large_image');
    this.setName('twitter:title', page.title);
    this.setName('twitter:description', page.description);
    this.setName('twitter:image', image);
    this.updateCanonical(includeCanonical ? canonical : null);
    this.updateStructuredData(page.structuredData ?? []);

    const verification = this.config.googleSiteVerification?.trim();
    if (verification) this.setName('google-site-verification', verification);
    else this.meta.removeTag("name='google-site-verification'");
  }

  private setName(name: string, content: string): void {
    this.meta.updateTag({ name, content }, `name='${name}'`);
  }

  private setProperty(property: string, content: string): void {
    this.meta.updateTag({ property, content }, `property='${property}'`);
  }

  private updateCanonical(url: string | null): void {
    this.document.querySelectorAll('link[rel="canonical"]').forEach((element) => element.remove());
    if (!url) return;
    const link = this.document.createElement('link');
    link.rel = 'canonical';
    link.href = url;
    this.document.head.appendChild(link);
  }

  private updateStructuredData(entries: readonly Record<string, unknown>[]): void {
    this.document.querySelectorAll('script[data-wwt-seo]').forEach((element) => element.remove());
    entries.forEach((entry) => {
      const script = this.document.createElement('script');
      script.type = 'application/ld+json';
      script.dataset['wwtSeo'] = 'true';
      script.textContent = JSON.stringify(entry).replace(/</g, '\\u003c');
      this.document.head.appendChild(script);
    });
  }

  private organizationSchema(): Record<string, unknown> {
    return {
      '@context': 'https://schema.org',
      '@type': 'Organization',
      name: 'WWTRADING',
      url: this.absoluteUrl('/'),
      logo: this.absoluteUrl('/assets/wwtrading-logo.png'),
      email: 'wwtrading.morocco@gmail.com',
      telephone: ['+212676886836', '+212603566841'],
      address: { '@type': 'PostalAddress', addressLocality: 'Kénitra', addressCountry: 'MA' }
    };
  }

  private websiteSchema(): Record<string, unknown> {
    return { '@context': 'https://schema.org', '@type': 'WebSite', name: 'WWTRADING', url: this.absoluteUrl('/') };
  }

  private breadcrumbSchema(items: readonly { name: string; path: string }[]): Record<string, unknown> {
    return {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: items.map((item, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: item.name,
        item: this.absoluteUrl(item.path)
      }))
    };
  }

  private catalogLabel(params: URLSearchParams): string | null {
    const category = params.get('machineCategory') ?? params.get('category');
    if (category && category in MACHINE_CATEGORY_LABELS) {
      return MACHINE_CATEGORY_LABELS[category as keyof typeof MACHINE_CATEGORY_LABELS];
    }
    const colorMode = params.get('colorMode');
    if (colorMode && colorMode in PRODUCT_COLOR_MODE_LABELS) {
      return `Imprimantes ${PRODUCT_COLOR_MODE_LABELS[colorMode as keyof typeof PRODUCT_COLOR_MODE_LABELS]}`;
    }
    const type = params.get('type');
    return type && type in PRODUCT_TYPE_LABELS
      ? `${PRODUCT_TYPE_LABELS[type as keyof typeof PRODUCT_TYPE_LABELS]}s`
      : null;
  }

  private absoluteUrl(path: string): string {
    if (/^https?:\/\//i.test(path)) return path;
    return `${this.siteUrl}${path.startsWith('/') ? path : `/${path}`}`;
  }
}

function normalizePath(path: string): string {
  if (!path || path === '/') return '/';
  return `/${path.replace(/^\/+|\/+$/g, '')}`;
}

function uniqueWords(values: readonly (string | null | undefined)[]): string[] {
  const result: string[] = [];
  for (const value of values) {
    const normalized = value?.trim();
    if (!normalized) continue;
    if (!result.some((item) => item.toLocaleLowerCase('fr') === normalized.toLocaleLowerCase('fr'))) result.push(normalized);
  }
  return result;
}

function conciseDescription(description: string | null, fallback: string): string {
  const normalized = (description?.trim() || fallback).replace(/\s+/g, ' ');
  if (normalized.length <= 158) return normalized;
  return `${normalized.slice(0, 154).replace(/\s+\S*$/, '')}…`;
}
