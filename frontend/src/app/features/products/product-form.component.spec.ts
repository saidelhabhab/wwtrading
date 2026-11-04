import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { NEVER, of, Subject, throwError } from 'rxjs';

import { authGuard } from '../../core/auth/auth.guard';
import { roleGuard } from '../../core/auth/role.guard';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { routes } from '../../app.routes';
import { ProductApiService } from './product-api.service';
import { ProductFormComponent } from './product-form.component';
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

describe('Product write API contract', () => {
  it('sends only editable fields for create and update without handling authorization', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: { apiBaseUrl: 'http://api.test/' } }
      ]
    });
    const service = TestBed.inject(ProductApiService);
    const http = TestBed.inject(HttpTestingController);
    const payload = {
      reference: 'REF', name: 'Produit', description: null, price: 10, type: 'PRINTER' as const,
      brand: null, model: null, machineCategory: null, colorMode: null, warrantyMonths: null, warrantyCopies: null
    };

    service.create(payload).subscribe();
    const create = http.expectOne('http://api.test/api/products');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    expect(create.request.headers.has('Authorization')).toBeFalse();
    create.flush(PRODUCT);

    service.update(PRODUCT_ID, payload).subscribe();
    const update = http.expectOne(`http://api.test/api/products/${PRODUCT_ID}`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual(payload);
    expect(Object.keys(update.request.body)).toEqual([
      'reference', 'name', 'description', 'price', 'type', 'brand', 'model',
      'machineCategory', 'colorMode', 'warrantyMonths', 'warrantyCopies'
    ]);
    update.flush(PRODUCT);
    http.verify();
  });
});

describe('ProductFormComponent', () => {
  function setValue(fixture: ComponentFixture<ProductFormComponent>, selector: string, value: string): void {
    const element = fixture.nativeElement.querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
    element.value = value;
    element.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function setup(mode: 'create' | 'edit', options?: {
    id?: string | null;
    getResult?: ReturnType<ProductApiService['getById']>;
    createResult?: ReturnType<ProductApiService['create']>;
    updateResult?: ReturnType<ProductApiService['update']>;
  }) {
    const api = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['getById', 'create', 'update']);
    api.getById.and.returnValue(options?.getResult ?? of(PRODUCT));
    api.create.and.returnValue(options?.createResult ?? of(PRODUCT));
    api.update.and.returnValue(options?.updateResult ?? of(PRODUCT));
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);

    TestBed.configureTestingModule({
      imports: [ProductFormComponent],
      providers: [
        { provide: ProductApiService, useValue: api },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { mode },
              paramMap: convertToParamMap(options?.id === null ? {} : { id: options?.id ?? PRODUCT_ID })
            }
          }
        }
      ]
    });
    const fixture = TestBed.createComponent(ProductFormComponent);
    fixture.detectChanges();
    return { fixture, api, router };
  }

  function fillValidForm(fixture: ComponentFixture<ProductFormComponent>): void {
    setValue(fixture, '#product-reference', ' NEW-REF ');
    setValue(fixture, '#product-name', ' Nouveau produit ');
    setValue(fixture, '#product-description', ' Description ');
    setValue(fixture, '#product-price', '99.50');
    setValue(fixture, '#product-brand', ' Konica Minolta ');
    setValue(fixture, '#product-model', ' C450i ');
  }

  afterEach(() => TestBed.resetTestingModule());

  it('enforces backend-aligned create validation', () => {
    const { fixture, api } = setup('create');
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(api.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.field-error').length).toBe(3);
  });

  it('creates with an editable-only payload and navigates to detail', () => {
    const { fixture, api, router } = setup('create');
    fillValidForm(fixture);
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(api.create).toHaveBeenCalledOnceWith({
      reference: 'NEW-REF', name: 'Nouveau produit', description: 'Description', price: 99.5, type: 'PRINTER',
      brand: 'Konica Minolta', model: 'C450i', machineCategory: null, colorMode: null, warrantyMonths: null, warrantyCopies: null
    });
    expect(Object.keys(api.create.calls.mostRecent().args[0])).toEqual([
      'reference', 'name', 'description', 'price', 'type', 'brand', 'model',
      'machineCategory', 'colorMode', 'warrantyMonths', 'warrantyCopies'
    ]);
    expect(router.navigate).toHaveBeenCalledOnceWith(['/products', PRODUCT_ID]);
  });

  it('offers only the four supported typed Product categories', () => {
    const { fixture } = setup('create');
    const elements = Array.from(fixture.nativeElement.querySelectorAll('#product-type option') as NodeListOf<HTMLOptionElement>);
    const options = elements.map((option) => option.value);

    expect(options).toEqual(['PRINTER', 'TONER', 'CONSUMABLE', 'SPARE_PART']);
    expect(elements.map((option) => option.textContent?.trim())).toEqual([
      'Imprimante', 'Toner', 'Consommable', 'Pièce détachée'
    ]);
  });

  it('prevents duplicate submissions while a create request is pending', () => {
    const pending = new Subject<Product>();
    const { fixture, api } = setup('create', { createResult: pending });
    fillValidForm(fixture);
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(api.create).toHaveBeenCalledTimes(1);
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('loads an existing product and updates only editable fields', () => {
    const { fixture, api, router } = setup('edit');
    expect(api.getById).toHaveBeenCalledOnceWith(PRODUCT_ID);
    expect((fixture.nativeElement.querySelector('#product-reference') as HTMLInputElement).value).toBe('KM-C360I');
    setValue(fixture, '#product-name', 'Produit actualisé');
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(api.update).toHaveBeenCalledOnceWith(PRODUCT_ID, {
      reference: PRODUCT.reference,
      name: 'Produit actualisé',
      description: PRODUCT.description,
      price: PRODUCT.price,
      type: PRODUCT.type,
      brand: PRODUCT.brand,
      model: PRODUCT.model,
      machineCategory: PRODUCT.machineCategory,
      colorMode: PRODUCT.colorMode,
      warrantyMonths: PRODUCT.warrantyMonths,
      warrantyCopies: PRODUCT.warrantyCopies
    });
    expect(Object.keys(api.update.calls.mostRecent().args[1])).toEqual([
      'reference', 'name', 'description', 'price', 'type', 'brand', 'model',
      'machineCategory', 'warrantyMonths', 'warrantyCopies'
    ]);
    expect(router.navigate).toHaveBeenCalledOnceWith(['/products', PRODUCT_ID]);
  });

  it('handles invalid IDs, 404 responses, and safe API errors', () => {
    const invalid = setup('edit', { id: 'invalid-id' });
    expect(invalid.api.getById).not.toHaveBeenCalled();
    expect(invalid.fixture.nativeElement.textContent).toContain('référence du produit est invalide');
    TestBed.resetTestingModule();

    const missing = setup('edit', {
      getResult: throwError(() => new HttpErrorResponse({ status: 404 }))
    });
    expect(missing.fixture.nativeElement.textContent).toContain('Produit introuvable');
    TestBed.resetTestingModule();

    const failed = setup('create', { createResult: throwError(() => new Error('raw database detail')) });
    fillValidForm(failed.fixture);
    (failed.fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    failed.fixture.detectChanges();
    expect(failed.fixture.nativeElement.textContent).toContain('ne peut pas être enregistré');
    expect(failed.fixture.nativeElement.textContent).not.toContain('raw database detail');
  });

  it('shows safe status-specific errors without exposing backend details', () => {
    const forbidden = setup('create', {
      createResult: throwError(() => new HttpErrorResponse({ status: 403, error: 'internal authorization detail' }))
    });
    fillValidForm(forbidden.fixture);
    (forbidden.fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    forbidden.fixture.detectChanges();

    expect(forbidden.fixture.nativeElement.textContent).toContain('droits nécessaires');
    expect(forbidden.fixture.nativeElement.textContent).not.toContain('internal authorization detail');
  });

  it('keeps loading state while an edit request is pending', () => {
    const { fixture } = setup('edit', { getResult: NEVER });
    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  });

  it('protects create and edit routes with the backend write-role policy', () => {
    for (const path of ['products/new', 'products/:id/edit']) {
      const route = routes.find((candidate) => candidate.path === path);
      expect(route?.canActivate).toEqual([authGuard, roleGuard]);
      expect(route?.data?.['roles']).toEqual(['ADMIN', 'MANAGER', 'COMMERCIAL']);
    }
  });
});
