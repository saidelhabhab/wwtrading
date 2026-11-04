import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { routes } from '../../app.routes';
import { ProductApiService } from './product-api.service';
import { ProductDetailComponent } from './product-detail.component';
import { Product } from './product.models';

const PRODUCT_ID = '9ca88f6e-4b99-4689-ad15-e2a78c68abae';
const PRODUCT: Product = {
  id: PRODUCT_ID,
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
  updatedAt: '2026-01-02T00:00:00Z'
};
const FIRST_IMAGE = {
  id: 'e772412d-1490-4b6c-8be6-8f9ba2f6472b',
  imageUrl: `/api/products/${PRODUCT_ID}/images/e772412d-1490-4b6c-8be6-8f9ba2f6472b/content`,
  main: false,
  displayOrder: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z'
} as const;
const MAIN_IMAGE = {
  ...FIRST_IMAGE,
  id: '3cff095a-104b-4d21-a981-2bd6c19e4685',
  imageUrl: `/api/products/${PRODUCT_ID}/images/3cff095a-104b-4d21-a981-2bd6c19e4685/content`,
  main: true,
  displayOrder: 1
} as const;

describe('Product detail API contract', () => {
  it('retrieves a product by its encoded identifier without handling tokens', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: { apiBaseUrl: 'http://api.test/' } }
      ]
    });
    const service = TestBed.inject(ProductApiService);
    const http = TestBed.inject(HttpTestingController);

    service.getById(PRODUCT_ID).subscribe((product) => expect(product).toEqual(PRODUCT));

    const request = http.expectOne(`http://api.test/api/products/${PRODUCT_ID}`);
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush(PRODUCT);

    service.getImages(PRODUCT_ID).subscribe((images) => expect(images).toEqual([MAIN_IMAGE]));
    const imageRequest = http.expectOne(`http://api.test/api/products/${PRODUCT_ID}/images`);
    expect(imageRequest.request.method).toBe('GET');
    imageRequest.flush([MAIN_IMAGE]);
    http.verify();
  });
});

describe('ProductDetailComponent', () => {
  function create(
    apiResult: ReturnType<ProductApiService['getById']>,
    id: string | null = PRODUCT_ID,
    roles: readonly WwtradingRole[] = ['ADMIN'],
    imageResult: ReturnType<ProductApiService['getImages']> = of([])
  ) {
    const api = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['getById', 'getImages', 'resolveImageUrl', 'activateProduct', 'deactivateProduct']);
    api.getById.and.returnValue(apiResult);
    api.getImages.and.returnValue(imageResult);
    api.resolveImageUrl.and.callFake((url: string) => `http://api.test${url}`);
    TestBed.configureTestingModule({
      imports: [ProductDetailComponent],
      providers: [
        { provide: ProductApiService, useValue: api },
        { provide: AuthService, useValue: { user: () => ({ roles }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } }
      ]
    });
    const fixture = TestBed.createComponent(ProductDetailComponent);
    fixture.detectChanges();
    return { fixture, api };
  }

  afterEach(() => TestBed.resetTestingModule());

  it('rejects a missing or invalid route parameter without an API call', () => {
    const { fixture, api } = create(of(PRODUCT), 'invalid-id');
    expect(api.getById).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('La référence du produit est invalide.');
  });

  it('shows the loading state', () => {
    const { fixture } = create(NEVER);
    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  });

  it('retrieves and displays the read-only product', () => {
    const { fixture, api } = create(of(PRODUCT));
    expect(api.getById).toHaveBeenCalledOnceWith(PRODUCT_ID);
    expect(fixture.nativeElement.textContent).toContain('Konica Minolta C360i');
    expect(fixture.nativeElement.textContent).toContain('KM-C360I');
    expect(fixture.nativeElement.textContent).toContain('Imprimante multifonction professionnelle');
    expect(fixture.nativeElement.textContent).toContain('Actif');
    expect(fixture.nativeElement.textContent).toContain('Imprimante');
    expect(fixture.nativeElement.textContent).toContain('Machine de production / Presse numérique');
    expect(fixture.nativeElement.textContent).toContain('Couleur');
    expect(fixture.nativeElement.textContent).toContain('10.000 copies');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('loads the gallery, selects main initially and supports keyboard-accessible thumbnail selection', () => {
    const { fixture, api } = create(of(PRODUCT), PRODUCT_ID, ['CLIENT'], of([MAIN_IMAGE, FIRST_IMAGE]));
    expect(api.getImages).toHaveBeenCalledOnceWith(PRODUCT_ID);
    let main = fixture.nativeElement.querySelector('.main-image > img') as HTMLImageElement;
    expect(main.src).toBe(`http://api.test${MAIN_IMAGE.imageUrl}`);
    expect(main.alt).toContain('image 2');
    const thumbnails = fixture.nativeElement.querySelectorAll('.thumbnail') as NodeListOf<HTMLButtonElement>;
    expect(thumbnails.length).toBe(2);
    expect(thumbnails[0].tagName).toBe('BUTTON');
    thumbnails[0].click();
    fixture.detectChanges();
    main = fixture.nativeElement.querySelector('.main-image > img');
    expect(main.src).toBe(`http://api.test${FIRST_IMAGE.imageUrl}`);
    expect(thumbnails[0].getAttribute('aria-pressed')).toBe('true');
  });

  it('falls back to the first ordered image when no main image exists', () => {
    const { fixture } = create(of(PRODUCT), PRODUCT_ID, ['CLIENT'], of([{ ...MAIN_IMAGE, main: false }, FIRST_IMAGE]));
    const main = fixture.nativeElement.querySelector('.main-image > img') as HTMLImageElement;
    expect(main.src).toBe(`http://api.test${FIRST_IMAGE.imageUrl}`);
  });

  it('supports zero or one image without unnecessary thumbnails', () => {
    const empty = create(of(PRODUCT), PRODUCT_ID, ['CLIENT']).fixture;
    expect(empty.nativeElement.textContent).toContain('Aucune image disponible');
    TestBed.resetTestingModule();

    const single = create(of(PRODUCT), PRODUCT_ID, ['CLIENT'], of([MAIN_IMAGE])).fixture;
    expect(single.nativeElement.querySelector('.main-image > img')).not.toBeNull();
    expect(single.nativeElement.querySelector('.thumbnails')).toBeNull();
  });

  it('keeps product details visible when the image API fails', () => {
    const { fixture } = create(of(PRODUCT), PRODUCT_ID, ['CLIENT'], throwError(() => new Error('raw storage path')));
    expect(fixture.nativeElement.textContent).toContain(PRODUCT.name);
    expect(fixture.nativeElement.textContent).toContain('Images momentanément indisponibles');
    expect(fixture.nativeElement.textContent).not.toContain('raw storage path');
  });

  it('replaces a broken main image with the gallery fallback', () => {
    const { fixture } = create(of(PRODUCT), PRODUCT_ID, ['CLIENT'], of([MAIN_IMAGE]));
    const image = fixture.nativeElement.querySelector('.main-image > img') as HTMLImageElement;
    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.main-image > img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Image indisponible');
  });

  it('shows the not-found state for a 404', () => {
    const { fixture } = create(throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(fixture.nativeElement.textContent).toContain('Ce produit n’existe pas');
  });

  it('shows a safe generic error and retry action', () => {
    const { fixture } = create(throwError(() => new Error('raw backend details')));
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Le produit ne peut pas être chargé pour le moment.');
    expect(alert.textContent).not.toContain('raw backend details');
    expect(alert.querySelector('button').textContent).toContain('Réessayer');
  });

  it('links back to the Product list', () => {
    const { fixture } = create(of(PRODUCT));
    expect(fixture.nativeElement.querySelector('.back-link').getAttribute('href')).toBe('/products');
  });

  it('shows management actions only when a matching protected management route exists', () => {
    const manager = create(of(PRODUCT), PRODUCT_ID, ['MANAGER']).fixture;
    const managerActions = manager.nativeElement.querySelector('.management-actions');
    expect(managerActions).not.toBeNull();
    expect(managerActions.querySelector('a').getAttribute('href')).toBe('/manager/products/new');
    expect(managerActions.querySelector('.primary-action').getAttribute('href')).toBe(`/manager/products/${PRODUCT_ID}/edit`);
    TestBed.resetTestingModule();

    const admin = create(of(PRODUCT), PRODUCT_ID, ['ADMIN']).fixture;
    expect(admin.nativeElement.querySelector('.primary-action').getAttribute('href')).toBe(`/admin/products/${PRODUCT_ID}/edit`);
    TestBed.resetTestingModule();

    const commercial = create(of(PRODUCT), PRODUCT_ID, ['COMMERCIAL']).fixture;
    expect(commercial.nativeElement.querySelector('.management-actions')).toBeNull();
    TestBed.resetTestingModule();

    const client = create(of(PRODUCT), PRODUCT_ID, ['CLIENT']).fixture;
    expect(client.nativeElement.querySelector('.management-actions')).toBeNull();
  });

  it('toggles product activation from the detail page and updates the status', () => {
    const { fixture, api } = create(of(PRODUCT), PRODUCT_ID, ['ADMIN']);
    api.deactivateProduct.and.returnValue(of({ ...PRODUCT, active: false }));
    spyOn(window, 'confirm').and.returnValue(true);

    const button = fixture.nativeElement.querySelector('[data-testid="toggle-product-status"]');
    button.click();
    fixture.detectChanges();

    expect(api.deactivateProduct).toHaveBeenCalledOnceWith(PRODUCT_ID);
    expect(fixture.nativeElement.textContent).toContain('Inactif');
    expect(button.textContent).toContain('Activer');
  });

  it('prevents duplicate detail toggles while a request is pending', () => {
    const { fixture, api } = create(of(PRODUCT), PRODUCT_ID, ['ADMIN']);
    api.deactivateProduct.and.returnValue(NEVER);
    spyOn(window, 'confirm').and.returnValue(true);

    const button = fixture.nativeElement.querySelector('[data-testid="toggle-product-status"]');
    button.click();
    button.click();
    fixture.detectChanges();

    expect(api.deactivateProduct).toHaveBeenCalledOnceWith(PRODUCT_ID);
    expect(button.disabled).toBeTrue();
    expect(button.textContent).toContain('Désactivation...');
  });

  it('does not call the API when detail toggle is cancelled', () => {
    const { fixture, api } = create(of(PRODUCT), PRODUCT_ID, ['ADMIN']);
    spyOn(window, 'confirm').and.returnValue(false);

    fixture.nativeElement.querySelector('[data-testid="toggle-product-status"]')?.click();

    expect(api.deactivateProduct).not.toHaveBeenCalled();
  });

  it('leaves the public detail route accessible without authentication guards', () => {
    const detailRoute = routes.find((route) => route.path === 'products/:id');
    expect(detailRoute?.canActivate).toBeUndefined();
    expect(detailRoute?.data).toBeUndefined();
    expect(detailRoute?.loadComponent).toBeDefined();
  });
});
describe('ProductDetailComponent classification', () => {
  function render(product: Partial<Product>) {
    const full = { ...PRODUCT, ...product } as Product;
    const api = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['getById', 'getImages', 'resolveImageUrl']);
    api.getById.and.returnValue(of(full));
    api.getImages.and.returnValue(of([]));
    api.resolveImageUrl.and.callFake((url: string) => `http://api.test${url}`);
    TestBed.configureTestingModule({
      imports: [ProductDetailComponent],
      providers: [
        { provide: ProductApiService, useValue: api },
        { provide: AuthService, useValue: { user: () => ({ roles: [] }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: PRODUCT_ID }) } } }
      ]
    });
    const fixture = TestBed.createComponent(ProductDetailComponent);
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('renders a colour printer as Imprimante + Couleur', () => {
    const text = render({ type: 'PRINTER', machineCategory: null, colorMode: 'COLOR' });
    expect(text).toContain('Imprimante');
    expect(text).toContain('Couleur');
    expect(text).not.toContain('Noir & Blanc');
  });

  it('renders a monochrome printer as Imprimante + Noir & Blanc', () => {
    const text = render({ type: 'PRINTER', machineCategory: null, colorMode: 'MONOCHROME' });
    expect(text).toContain('Imprimante');
    expect(text).toContain('Noir & Blanc');
  });

  it('renders the digital press machine category label', () => {
    const text = render({ type: 'PRINTER', machineCategory: 'DIGITAL_PRESS', colorMode: 'COLOR' });
    expect(text).toContain('Machine de production / Presse numérique');
  });

  it('renders the finishing machine category label', () => {
    const text = render({ type: 'PRINTER', machineCategory: 'FINISHING_MACHINE', colorMode: null });
    expect(text).toContain('Matériel de finition');
  });

  it('renders the grand format machine category label', () => {
    const text = render({ type: 'PRINTER', machineCategory: 'GRAND_FORMAT_ECOSOLVENT', colorMode: null });
    expect(text).toContain('Machine grand format / Ecosolver');
  });

  it('omits both classification rows for a spare part', () => {
    const text = render({ type: 'SPARE_PART', machineCategory: null, colorMode: null });
    expect(text).toContain('Pièce détachée');
    expect(text).not.toContain('Catégorie');
    expect(text).not.toContain('Impression');
    expect(text).not.toContain('Non applicable');
  });

  it('never renders raw enum values', () => {
    const text = render({ type: 'SPARE_PART', machineCategory: null, colorMode: null });
    for (const raw of ['SPARE_PART', 'PRINTER', 'TONER', 'CONSUMABLE', 'COLOR', 'MONOCHROME',
      'DIGITAL_PRESS', 'FINISHING_MACHINE', 'GRAND_FORMAT_ECOSOLVENT']) {
      expect(text).not.toContain(raw);
    }
  });

  it('hides colour mode for a non-printer even when the API returns one', () => {
    const text = render({ type: 'CONSUMABLE', machineCategory: null, colorMode: 'COLOR' });
    expect(text).toContain('Consommable');
    expect(text).not.toContain('Impression');
    expect(text).not.toContain('Couleur');
  });

  it('renders no null or undefined placeholder text', () => {
    const text = render({ type: 'PRINTER', machineCategory: null, colorMode: null });
    expect(text).not.toContain('null');
    expect(text).not.toContain('undefined');
  });

  it('keeps reference, brand, model, price and warranty functional', () => {
    const text = render({});
    expect(text).toContain('KM-C360I');
    expect(text).toContain('Konica Minolta');
    expect(text).toContain('C360i');
    expect(text).toContain('12');
    expect(text).toContain('10.000 copies');
  });
});