import { HttpErrorResponse } from '@angular/common/http';
import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService, AuthenticatedUser, WwtradingRole } from '../../../core/auth/auth.service';
import { ProductApiService } from '../../products/product-api.service';
import { Product, ProductPage } from '../../products/product.models';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import { Quotation } from '../../quotations/quotation.models';
import { DemandeDevisComponent } from './demande-devis.component';

describe('DemandeDevisComponent', () => {
  let component: DemandeDevisComponent;
  let fixture: ComponentFixture<DemandeDevisComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let productApiSpy: jasmine.SpyObj<ProductApiService>;
  let quotationApiSpy: jasmine.SpyObj<QuotationApiService>;

  let authenticatedSignal: WritableSignal<boolean>;
  let userSignal: WritableSignal<AuthenticatedUser | null>;
  let rolesSignal: WritableSignal<readonly WwtradingRole[]>;

  const mockProduct1: Product = {
    id: 'prod-001',
    reference: 'KM-C250I',
    name: 'bizhub C250i',
    description: 'Multifonction couleur A3',
    price: 15000,
    type: 'PRINTER',
    brand: 'Konica Minolta',
    model: 'C250i',
    machineCategory: 'DIGITAL_PRESS',
    colorMode: 'COLOR',
    warrantyMonths: 12,
    warrantyCopies: 10000,
    availableQuantity: 5,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z'
  };

  const mockProduct2: Product = {
    id: 'prod-025',
    reference: 'DEV-INEOPLUS',
    name: 'ineo+ 3070',
    description: 'Presse numérique de production',
    price: 45000,
    type: 'PRINTER',
    brand: 'Develop',
    model: 'ineo+ 3070',
    machineCategory: 'DIGITAL_PRESS',
    colorMode: 'COLOR',
    warrantyMonths: 24,
    warrantyCopies: 50000,
    availableQuantity: 2,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z'
  };

  const samplePageResponse: ProductPage = {
    content: [mockProduct1],
    number: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    numberOfElements: 1,
    first: true,
    last: true,
    empty: false
  };

  const sampleQuotation: Quotation = {
    id: 'quot-12345',
    customerReference: 'prospect@example.com',
    serviceType: 'VENTE',
    requestedEquipment: null,
    brandModel: null,
    quantity: 1,
    description: 'Devis personnalisé',
    fullName: 'Karim Alaoui',
    company: 'Société SARL',
    phone: '0661234567',
    email: 'prospect@example.com',
    city: 'Kénitra',
    desiredTimeline: null,
    estimatedBudget: null,
    additionalMessage: 'Besoin urgent',
    status: 'PENDING',
    proposedAmount: null,
    commercialNotes: null,
    subject: 'Demande de devis',
    requestDescription: '',
    notes: null,
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z'
  };

  beforeEach(async () => {
    sessionStorage.clear();

    authenticatedSignal = signal(false);
    userSignal = signal<AuthenticatedUser | null>(null);
    rolesSignal = signal<readonly WwtradingRole[]>([]);

    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login'], {
      authenticated: authenticatedSignal,
      user: userSignal,
      roles: rolesSignal
    });

    productApiSpy = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['list', 'resolveImageUrl']);
    productApiSpy.list.and.returnValue(of(samplePageResponse));
    productApiSpy.resolveImageUrl.and.callFake((url: string) => url);

    quotationApiSpy = jasmine.createSpyObj<QuotationApiService>('QuotationApiService', ['createQuotation']);
    quotationApiSpy.createQuotation.and.returnValue(of(sampleQuotation));

    await TestBed.configureTestingModule({
      imports: [DemandeDevisComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ProductApiService, useValue: productApiSpy },
        { provide: QuotationApiService, useValue: quotationApiSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DemandeDevisComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('initializes and loads first page of products', () => {
    expect(productApiSpy.list).toHaveBeenCalledWith(0, 20, 'name,asc', { search: undefined });
    expect(component.products().length).toBe(1);
    expect(component.products()[0].id).toBe('prod-001');
  });

  it('allows unauthenticated guest to compose and submit quotation without forcing Keycloak login', () => {
    // 1. Guest selects a product
    component.addProduct(mockProduct1);
    expect(component.selectedProducts().length).toBe(1);

    // 2. Fill required form fields
    component.devisForm.setValue({
      fullName: 'Karim Alaoui',
      company: 'Société SARL',
      phone: '0661234567',
      email: 'prospect@example.com',
      city: 'Kénitra',
      additionalMessage: 'Besoin urgent'
    });

    // 3. Submit
    component.submitQuoteRequest();

    // 4. Verify login was NOT triggered
    expect(authServiceSpy.login).not.toHaveBeenCalled();

    // 5. Verify API was called with guest payload
    expect(quotationApiSpy.createQuotation).toHaveBeenCalledWith(
      jasmine.objectContaining({
        fullName: 'Karim Alaoui',
        email: 'prospect@example.com',
        phone: '0661234567',
        city: 'Kénitra',
        requestedLines: [{ productId: 'prod-001', quantity: 1 }]
      })
    );

    // 6. Verify successful submission state
    expect(component.isSubmitted()).toBeTrue();
    expect(component.createdQuotation()?.id).toBe('quot-12345');
  });

  it('pre-fills name and email when user is authenticated as CLIENT', () => {
    authenticatedSignal.set(true);
    userSignal.set({
      subject: 'sub-123',
      displayName: 'Ahmed Bennani',
      email: 'ahmed@client.ma',
      roles: ['CLIENT']
    });

    const newFixture = TestBed.createComponent(DemandeDevisComponent);
    const newComponent = newFixture.componentInstance;
    newFixture.detectChanges();

    expect(newComponent.devisForm.controls.fullName.value).toBe('Ahmed Bennani');
    expect(newComponent.devisForm.controls.email.value).toBe('ahmed@client.ma');
    expect(newComponent.canViewMyQuotations()).toBeTrue();
  });

  it('allows COMMERCIAL or ADMIN role to submit quotation on behalf of prospect', () => {
    authenticatedSignal.set(true);
    rolesSignal.set(['COMMERCIAL']);

    component.addProduct(mockProduct1);
    component.devisForm.setValue({
      fullName: 'Prospect Client',
      company: 'Prospect SARL',
      phone: '0611223344',
      email: 'client@prospect.ma',
      city: 'Casablanca',
      additionalMessage: 'Devis initié par commercial'
    });

    component.submitQuoteRequest();

    expect(quotationApiSpy.createQuotation).toHaveBeenCalled();
    expect(component.isSubmitted()).toBeTrue();
  });

  it('performs debounced server-side product search across full catalog', fakeAsync(() => {
    productApiSpy.list.calls.reset();
    productApiSpy.list.and.returnValue(
      of({
        content: [mockProduct2],
        number: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        numberOfElements: 1,
        first: true,
        last: true,
        empty: false
      })
    );

    // User types search query
    component.onSearchInput('ineo');
    expect(productApiSpy.list).not.toHaveBeenCalled(); // Debouncing

    tick(400); // Wait for debounce

    expect(productApiSpy.list).toHaveBeenCalledWith(0, 20, 'name,asc', { search: 'ineo' });
    expect(component.products().length).toBe(1);
    expect(component.products()[0].reference).toBe('DEV-INEOPLUS');
  }));

  it('handles empty search results cleanly without crashing', fakeAsync(() => {
    productApiSpy.list.calls.reset();
    productApiSpy.list.and.returnValue(
      of({
        content: [],
        number: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        numberOfElements: 0,
        first: true,
        last: true,
        empty: true
      })
    );

    component.onSearchInput('unknown-model-xyz');
    tick(400);

    expect(component.products().length).toBe(0);
    expect(component.productsError()).toBeNull();
  }));

  it('persists draft in sessionStorage and restores it on reload', () => {
    // 1. User inputs information
    component.devisForm.controls.fullName.setValue('Fatima Zahra');
    component.devisForm.controls.city.setValue('Rabat');
    component.addProduct(mockProduct1);

    // 2. Draft is in sessionStorage
    const stored = sessionStorage.getItem('wwt_devis_draft_v1');
    expect(stored).not.toBeNull();

    // 3. Create fresh component instance
    const freshFixture = TestBed.createComponent(DemandeDevisComponent);
    const freshComponent = freshFixture.componentInstance;
    freshFixture.detectChanges();

    // 4. Verify draft restored
    expect(freshComponent.devisForm.controls.fullName.value).toBe('Fatima Zahra');
    expect(freshComponent.devisForm.controls.city.value).toBe('Rabat');
    expect(freshComponent.selectedProducts().length).toBe(1);
    expect(freshComponent.selectedProducts()[0].product.id).toBe('prod-001');
    expect(freshComponent.draftRestored()).toBeTrue();
  });

  it('preserves form and draft when a network or server error occurs during submission', () => {
    component.addProduct(mockProduct1);
    component.devisForm.setValue({
      fullName: 'Said Amrani',
      company: 'Bureau SARL',
      phone: '0655443322',
      email: 'said@bureau.ma',
      city: 'Tanger',
      additionalMessage: 'Test network error'
    });

    quotationApiSpy.createQuotation.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' }))
    );

    component.submitQuoteRequest();

    expect(component.submitting()).toBeFalse();
    expect(component.isSubmitted()).toBeFalse();
    expect(component.submissionError()).toContain('Le service de devis est momentanément indisponible');

    // Form data and selected items remain completely intact
    expect(component.devisForm.controls.fullName.value).toBe('Said Amrani');
    expect(component.selectedProducts().length).toBe(1);
  });

  it('rejects submission when no products are selected', () => {
    component.devisForm.setValue({
      fullName: 'Yassine',
      company: '',
      phone: '0612345678',
      email: 'yassine@example.com',
      city: 'Fès',
      additionalMessage: ''
    });

    component.submitQuoteRequest();

    expect(quotationApiSpy.createQuotation).not.toHaveBeenCalled();
    expect(component.submissionError()).toBe('Veuillez sélectionner au moins un équipement du catalogue.');
  });

  it('rejects submission when required form fields are invalid', () => {
    component.addProduct(mockProduct1);
    component.devisForm.setValue({
      fullName: '',
      company: '',
      phone: 'invalid',
      email: 'not-an-email',
      city: '',
      additionalMessage: ''
    });

    component.submitQuoteRequest();

    expect(quotationApiSpy.createQuotation).not.toHaveBeenCalled();
    expect(component.submissionError()).toBe('Veuillez vérifier et compléter tous les champs obligatoires.');
  });

  it('prevents double-submission on rapid double click', () => {
    component.addProduct(mockProduct1);
    component.devisForm.setValue({
      fullName: 'Test User',
      company: 'Test Co',
      phone: '0612345678',
      email: 'test@example.com',
      city: 'Kénitra',
      additionalMessage: ''
    });

    // Simulate first in-flight submission
    component.submitting.set(true);

    component.submitQuoteRequest();

    expect(quotationApiSpy.createQuotation).not.toHaveBeenCalled();
  });
});

