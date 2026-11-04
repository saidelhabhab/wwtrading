import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { roleGuard } from '../../core/auth/role.guard';
import { routes } from '../../app.routes';
import { QuotationApiService } from './quotation-api.service';
import { QuotationEditComponent } from './quotation-edit.component';
import { Quotation } from './quotation.models';

describe('QuotationEditComponent', () => {
  let fixture: ComponentFixture<QuotationEditComponent>;
  let getById: jasmine.Spy;
  let update: jasmine.Spy;
  let router: Router;

  beforeEach(async () => {
    getById = jasmine.createSpy('getById');
    update = jasmine.createSpy('update');
    await TestBed.configureTestingModule({
      imports: [QuotationEditComponent],
      providers: [
        provideRouter([]),
        { provide: QuotationApiService, useValue: { getById, update } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'quotation-1' }) } } }
      ]
    }).compileComponents();
    router = TestBed.inject(Router);
  });

  it('loads existing editable content into the form', () => {
    createLoadedComponent();

    expect(getById).toHaveBeenCalledOnceWith('quotation-1');
    expect(field('edit-subject').value).toBe('Audit impression');
    expect(field('edit-description').value).toBe('Audit du parc existant');
    expect(fixture.nativeElement.textContent).toContain('customer-1');
    expect(fixture.nativeElement.querySelector('#customerReference')).toBeNull();
  });

  it('rejects blank required values before calling the API', () => {
    createLoadedComponent();
    setField('edit-subject', '   ');
    submit();

    expect(update).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Objet requis');
  });

  it('submits only editable fields and refreshes the detail route', () => {
    update.and.returnValue(of({ ...quotation, subject: 'Updated', requestDescription: 'Updated request', notes: null }));
    spyOn(router, 'navigate').and.resolveTo(true);
    createLoadedComponent();
    setField('edit-subject', ' Updated ');
    setField('edit-description', ' Updated request ');
    setField('edit-notes', '   ');
    submit();

    expect(update).toHaveBeenCalledOnceWith('quotation-1', {
      subject: 'Updated',
      requestDescription: 'Updated request',
      notes: null
    });
    expect(router.navigate).toHaveBeenCalledOnceWith(['/devis', 'quotation-1']);
  });

  it('keeps the form disabled while an update is pending', () => {
    update.and.returnValue(new Subject<Quotation>());
    createLoadedComponent();
    submit();

    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
    expect(button.textContent).toContain('Enregistrement');
  });

  it('shows a dedicated not-found state', () => {
    getById.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    fixture = TestBed.createComponent(QuotationEditComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Devis introuvable');
  });

  it('shows a safe update error', () => {
    update.and.returnValue(throwError(() => new Error('internal database detail')));
    createLoadedComponent();
    submit();

    expect(fixture.nativeElement.textContent).toContain('ne peuvent pas être enregistrées');
    expect(fixture.nativeElement.textContent).not.toContain('internal database detail');
  });

  it('restricts the edit route to read-authorized business roles', () => {
    const editRoute = routes.find((route) => route.path === 'devis/:id/modifier');

    expect(editRoute?.canActivate).toContain(roleGuard);
    expect(editRoute?.data?.['roles']).toEqual(['ADMIN', 'MANAGER', 'COMMERCIAL']);
    expect(editRoute?.data?.['roles']).not.toContain('CLIENT');
    expect(editRoute?.data?.['roles']).not.toContain('TECHNICIAN');
  });

  function createLoadedComponent(): void {
    getById.and.returnValue(of(quotation));
    fixture = TestBed.createComponent(QuotationEditComponent);
    fixture.detectChanges();
  }

  function field(id: string): HTMLInputElement | HTMLTextAreaElement {
    return fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement | HTMLTextAreaElement;
  }

  function setField(id: string, value: string): void {
    const control = field(id);
    control.value = value;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
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
