import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { QuotationApiService } from './quotation-api.service';
import { QuotationDetailComponent } from './quotation-detail.component';
import { Quotation } from './quotation.models';

describe('QuotationDetailComponent', () => {
  let fixture: ComponentFixture<QuotationDetailComponent>;
  let getById: jasmine.Spy;

  beforeEach(async () => {
    getById = jasmine.createSpy('getById');
    await TestBed.configureTestingModule({
      imports: [QuotationDetailComponent],
      providers: [
        provideRouter([]),
        { provide: QuotationApiService, useValue: { getById } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'quotation-1' }) } } }
      ]
    }).compileComponents();
  });

  it('loads the route quotation and renders it read-only', () => {
    getById.and.returnValue(of(quotation));
    createComponent();

    expect(getById).toHaveBeenCalledOnceWith('quotation-1');
    expect(fixture.nativeElement.textContent).toContain('Audit impression');
    expect(fixture.nativeElement.textContent).toContain('Brouillon');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('shows a loading state while the request is pending', () => {
    getById.and.returnValue(new Subject<Quotation>());
    createComponent();

    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  });

  it('shows a dedicated not-found state', () => {
    getById.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Ce devis n’existe pas');
  });

  it('shows a safe error without exposing API details', () => {
    getById.and.returnValue(throwError(() => new Error('internal database detail')));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Chargement impossible');
    expect(fixture.nativeElement.textContent).not.toContain('internal database detail');
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(QuotationDetailComponent);
    fixture.detectChanges();
  }
});

const quotation: Quotation = ({
  id: 'quotation-1',
  customerReference: 'customer-1',
  status: 'DRAFT',
  subject: 'Audit impression',
  requestDescription: 'Audit du parc existant',
  notes: 'Contacter le client.',
  createdAt: '2026-08-31T00:00:00Z',
  updatedAt: '2026-08-31T00:00:00Z'
} as unknown as Quotation);
