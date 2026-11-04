import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';

import { authGuard } from '../../core/auth/auth.guard';
import { AuthService } from '../../core/auth/auth.service';
import { roleGuard } from '../../core/auth/role.guard';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { routes } from '../../app.routes';
import { ProductApiService } from './product-api.service';
import { ProductListComponent } from './product-list.component';
import { ProductPage } from './product.models';

const PRODUCT = {
  id: '9ca88f6e-4b99-4689-ad15-e2a78c68abae',
  reference: 'KM-C360I',
  name: 'Konica Minolta C360i',
  description: 'Imprimante multifonction professionnelle',
  price: 12500,
  type: 'PRINTER',
  brand: 'Konica Minolta',
  model: 'C360i',
  machineCategory: 'DIGITAL_PRESS',
  colorMode: 'COLOR',
  warrantyMonths: 6,
  warrantyCopies: 10000,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z'
} as const;

const IMAGE = {
  id: 'e772412d-1490-4b6c-8be6-8f9ba2f6472b',
  imageUrl: `/api/products/${PRODUCT.id}/images/e772412d-1490-4b6c-8be6-8f9ba2f6472b/content`,
  main: true,
  displayOrder: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z'
} as const;

function page(content = [PRODUCT]): ProductPage {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    numberOfElements: content.length,
    first: true,
    last: true,
    empty: content.length === 0
  };
}

describe('ProductApiService', () => {
  it('requests the protected paginated Product API without handling tokens', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: { apiBaseUrl: 'http://api.test/' } }
      ]
    });
    const service = TestBed.inject(ProductApiService);
    const http = TestBed.inject(HttpTestingController);

    service.list(2, 10, 'name,desc').subscribe((response) => expect(response.content).toEqual([PRODUCT]));

    const request = http.expectOne((candidate) =>
      candidate.url === 'http://api.test/api/products' &&
      candidate.params.get('page') === '2' &&
      candidate.params.get('size') === '10' &&
      candidate.params.get('sort') === 'name,desc'
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush(page());

    service.getImages(PRODUCT.id).subscribe((images) => expect(images).toEqual([IMAGE]));
    const imagesRequest = http.expectOne(`http://api.test/api/products/${PRODUCT.id}/images`);
    expect(imagesRequest.request.method).toBe('GET');
    expect(imagesRequest.request.headers.has('Authorization')).toBeFalse();
    imagesRequest.flush([IMAGE]);
    expect(service.resolveImageUrl(IMAGE.imageUrl)).toBe(`http://api.test${IMAGE.imageUrl}`);
    http.verify();
  });
});

describe('ProductListComponent', () => {
  function create(
    apiResult: ReturnType<ProductApiService['list']>,
    roles: readonly string[] = ['ADMIN'],
    mode = 'catalog',
    imageResult: ReturnType<ProductApiService['getImages']> = of([])
  ) {
    const api = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['list', 'getImages', 'resolveImageUrl', 'activateProduct', 'deactivateProduct']);
    api.list.and.returnValue(apiResult);
    api.getImages.and.returnValue(imageResult);
    api.resolveImageUrl.and.callFake((url: string) => `http://api.test${url}`);
    TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [
        { provide: ProductApiService, useValue: api },
        { provide: AuthService, useValue: { user: () => ({ roles }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { mode } } } }
      ]
    });
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();
    return { fixture, api };
  }

  afterEach(() => TestBed.resetTestingModule());

  it('shows the loading state', () => {
    const { fixture } = create(NEVER);
    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  });

  it('renders a successful product list', () => {
    const { fixture } = create(of(page()));
    expect(fixture.nativeElement.textContent).toContain('Konica Minolta C360i');
    expect(fixture.nativeElement.textContent).toContain('KM-C360I');
    expect(fixture.nativeElement.textContent).toContain('Imprimante');
    expect(fixture.nativeElement.textContent).toContain('Machine presse numérique');
    expect(fixture.nativeElement.textContent).toContain('6 mois');
  });

  it('shows the add product button on the admin product list', () => {
    TestBed.resetTestingModule();
    const api = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['list', 'getImages', 'resolveImageUrl']);
    api.list.and.returnValue(of(page([])));
    api.getImages.and.returnValue(of([]));
    TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [
        { provide: ProductApiService, useValue: api },
        { provide: AuthService, useValue: { user: () => ({ roles: ['ADMIN'] }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { mode: 'management', managementBaseRoute: '/admin/products' } } } }
      ]
    });

    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.detectChanges();

    const addProductLink = fixture.nativeElement.querySelector('.primary-link') as HTMLAnchorElement;
    expect(addProductLink).not.toBeNull();
    expect(addProductLink.textContent).toContain('Ajouter un produit');
    expect(addProductLink.getAttribute('href')).toBe('/admin/products/new');
  });

  it('displays the backend main image with meaningful alt text', () => {
    const { fixture, api } = create(of(page()), ['CLIENT'], 'catalog', of([IMAGE]));
    const image = fixture.nativeElement.querySelector('.product-media img') as HTMLImageElement;
    expect(api.getImages).toHaveBeenCalledOnceWith(PRODUCT.id);
    expect(image.src).toBe(`http://api.test${IMAGE.imageUrl}`);
    expect(image.alt).toBe(PRODUCT.name);
  });

  it('uses a clean fallback when no image exists or image loading fails', () => {
    const empty = create(of(page())).fixture;
    expect(empty.nativeElement.textContent).toContain('Image non disponible');
    TestBed.resetTestingModule();

    const failed = create(of(page()), ['CLIENT'], 'catalog', of([IMAGE])).fixture;
    const image = failed.nativeElement.querySelector('.product-media img') as HTMLImageElement;
    image.dispatchEvent(new Event('error'));
    failed.detectChanges();
    expect(failed.nativeElement.querySelector('.product-media img')).toBeNull();
    expect(failed.nativeElement.textContent).toContain('Image indisponible');
  });

  it('keeps the product usable when its image API fails', () => {
    const { fixture } = create(of(page()), ['CLIENT'], 'catalog', throwError(() => new Error('storage details')));
    expect(fixture.nativeElement.textContent).toContain(PRODUCT.name);
    expect(fixture.nativeElement.textContent).toContain('Image non disponible');
    expect(fixture.nativeElement.textContent).not.toContain('storage details');
  });

  it('renders the empty state', () => {
    const { fixture } = create(of(page([])));
    expect(fixture.nativeElement.textContent).toContain('Aucun produit disponible');
  });

  it('renders a safe error with retry', () => {
    const { fixture } = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Les produits ne peuvent pas être chargés pour le moment.');
    expect(alert.querySelector('button').textContent).toContain('Réessayer');
  });

  it('deactivates a product from the list and updates its local state', () => {
    const { fixture, api } = create(of(page([PRODUCT])), ['ADMIN'], 'management');
    api.deactivateProduct.and.returnValue(of({ ...PRODUCT, active: false }));
    spyOn(window, 'confirm').and.returnValue(true);

    const button = fixture.nativeElement.querySelector('[data-testid="toggle-product-9ca88f6e-4b99-4689-ad15-e2a78c68abae"]');
    button.click();
    fixture.detectChanges();

    expect(api.deactivateProduct).toHaveBeenCalledOnceWith(PRODUCT.id);
    expect(fixture.nativeElement.textContent).toContain('Inactif');
  });

  it('prevents duplicate list toggles while a request is pending', () => {
    const { fixture, api } = create(of(page([PRODUCT])), ['ADMIN'], 'management');
    api.deactivateProduct.and.returnValue(NEVER);
    spyOn(window, 'confirm').and.returnValue(true);

    const button = fixture.nativeElement.querySelector('[data-testid="toggle-product-9ca88f6e-4b99-4689-ad15-e2a78c68abae"]');
    button.click();
    button.click();
    fixture.detectChanges();

    expect(api.deactivateProduct).toHaveBeenCalledOnceWith(PRODUCT.id);
    expect(button.disabled).toBeTrue();
    expect(button.textContent).toContain('Traitement…');
  });

  it('does not call the API when list toggle is cancelled', () => {
    const { fixture, api } = create(of(page([PRODUCT])), ['ADMIN'], 'management');
    spyOn(window, 'confirm').and.returnValue(false);

    fixture.nativeElement.querySelector('[data-testid="toggle-product-9ca88f6e-4b99-4689-ad15-e2a78c68abae"]')?.click();

    expect(api.deactivateProduct).not.toHaveBeenCalled();
  });

  it('hides the toggle in the list for non-authorized roles', () => {
    const { fixture } = create(of(page([PRODUCT])), ['CLIENT']);
    const button = fixture.nativeElement.querySelector('[data-testid="toggle-product-9ca88f6e-4b99-4689-ad15-e2a78c68abae"]');
    expect(button).toBeNull();
  });

  it('leaves the Product catalog route public', () => {
    const productRoute = routes.find((route) => route.path === 'products');
    expect(productRoute?.canActivate).toBeUndefined();
    expect(productRoute?.data).toBeUndefined();
    expect(productRoute?.loadComponent).toBeDefined();
  });
});
