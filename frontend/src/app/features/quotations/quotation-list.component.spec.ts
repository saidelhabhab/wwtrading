import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { QuotationApiService } from './quotation-api.service';
import { QuotationListComponent } from './quotation-list.component';
import { PageResponse, Quotation } from './quotation.models';

describe('QuotationListComponent', () => {
  let fixture: ComponentFixture<QuotationListComponent>;
  let list: jasmine.Spy;

  beforeEach(async () => {
    list = jasmine.createSpy('list');
    await TestBed.configureTestingModule({
      imports: [QuotationListComponent],
      providers: [
        { provide: QuotationApiService, useValue: { list } },
        {
          provide: AuthService,
          useValue: {
            authenticated: () => true,
            logout: jasmine.createSpy('logout').and.resolveTo()
          }
        }
      ]
    }).compileComponents();
  });

  it('shows a loading state while the API request is pending', () => {
    list.and.returnValue(new Subject<PageResponse<Quotation>>());
    fixture = TestBed.createComponent(QuotationListComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  });

  it('shows an empty state', () => {
    list.and.returnValue(of(page([])));
    fixture = TestBed.createComponent(QuotationListComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Aucun devis');
  });

  it('shows a safe error state', () => {
    list.and.returnValue(throwError(() => new Error('internal detail')));
    fixture = TestBed.createComponent(QuotationListComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Chargement impossible');
    expect(fixture.nativeElement.textContent).not.toContain('internal detail');
  });

  it('renders quotations returned by the API', () => {
    list.and.returnValue(of(page([({
      id: 'quotation-1',
      customerReference: 'customer-1',
      status: 'DRAFT',
      subject: 'Audit impression',
      requestDescription: 'Audit du parc existant',
      notes: null,
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z'
    } as unknown as Quotation)])));
    fixture = TestBed.createComponent(QuotationListComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Audit impression');
    expect(fixture.nativeElement.textContent).toContain('Brouillon');
  });
});

function page(content: readonly Quotation[]): PageResponse<Quotation> {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    first: true,
    last: true,
    empty: content.length === 0
  };
}
