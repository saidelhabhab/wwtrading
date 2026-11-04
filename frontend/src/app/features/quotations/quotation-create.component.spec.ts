import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { QuotationApiService } from './quotation-api.service';
import { QuotationCreateComponent } from './quotation-create.component';
import { Quotation } from './quotation.models';

describe('QuotationCreateComponent', () => {
  let fixture: ComponentFixture<QuotationCreateComponent>;
  let create: jasmine.Spy;
  let router: Router;

  beforeEach(async () => {
    create = jasmine.createSpy('create');
    await TestBed.configureTestingModule({
      imports: [QuotationCreateComponent],
      providers: [
        provideRouter([]),
        { provide: QuotationApiService, useValue: { create } }
      ]
    }).compileComponents();
    router = TestBed.inject(Router);
  });

  it('does not submit invalid or whitespace-only fields', () => {
    fixture = TestBed.createComponent(QuotationCreateComponent);
    fixture.detectChanges();
    setField('customer-reference', '   ');
    submit();

    expect(create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Référence requise');
  });

  it('submits only the four allowed client fields and navigates to the list', () => {
    create.and.returnValue(of(response));
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(QuotationCreateComponent);
    fixture.detectChanges();
    fillValidForm();
    submit();

    expect(create).toHaveBeenCalledOnceWith({
      customerReference: 'customer-1',
      subject: 'Audit impression',
      requestDescription: 'Audit du parc',
      notes: null
    });
    expect(router.navigate).toHaveBeenCalledOnceWith(['/devis']);
  });

  it('disables submission while the request is pending', () => {
    create.and.returnValue(new Subject<Quotation>());
    fixture = TestBed.createComponent(QuotationCreateComponent);
    fixture.detectChanges();
    fillValidForm();
    submit();

    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
    expect(button.textContent).toContain('Envoi en cours');
  });

  it('shows a safe API error', () => {
    create.and.returnValue(throwError(() => new Error('database detail')));
    fixture = TestBed.createComponent(QuotationCreateComponent);
    fixture.detectChanges();
    fillValidForm();
    submit();

    expect(fixture.nativeElement.textContent).toContain('ne peut pas être créé');
    expect(fixture.nativeElement.textContent).not.toContain('database detail');
  });

  function fillValidForm(): void {
    setField('customer-reference', ' customer-1 ');
    setField('quotation-subject', ' Audit impression ');
    setField('request-description', ' Audit du parc ');
  }

  function setField(id: string, value: string): void {
    const field = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement | HTMLTextAreaElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }
});

const response: Quotation = ({
  id: 'quotation-1',
  customerReference: 'customer-1',
  status: 'DRAFT',
  subject: 'Audit impression',
  requestDescription: 'Audit du parc',
  notes: null,
  createdAt: '2026-08-31T00:00:00Z',
  updatedAt: '2026-08-31T00:00:00Z'
} as unknown as Quotation);
