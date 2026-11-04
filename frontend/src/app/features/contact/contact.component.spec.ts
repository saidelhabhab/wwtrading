import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ContactComponent } from './contact.component';

describe('ContactComponent', () => {
  let component: ContactComponent;
  let fixture: ComponentFixture<ContactComponent>;
  let root: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContactComponent],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(ContactComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    root = fixture.nativeElement as HTMLElement;
  });

  it('creates the component successfully', () => {
    expect(component).toBeTruthy();
    expect(component.isSubmitting()).toBeFalse();
    expect(component.isSubmitted()).toBeFalse();
    expect(component.submissionRef()).toBeNull();
  });

  it('renders verified WWTRADING business data from source of truth', () => {
    const text = root.textContent || '';
    // Phones
    expect(text).toContain('06 76 88 68 36');
    expect(text).toContain('06 03 56 68 41');
    // Email
    expect(text).toContain('wwtrading.morocco@gmail.com');
    // Location & Coverage
    expect(text).toContain('Kénitra, Maroc');
    expect(text).toContain('Tanger → Agadir');
    // Key commitments & response
    expect(text).toContain('H+8');
    expect(text).toContain('10+');
    expect(text).toContain('500+');
  });

  it('renders click-to-call and mailto links for immediate contact', () => {
    const phoneLinks = Array.from(root.querySelectorAll<HTMLAnchorElement>('a[href^="tel:"]'));
    expect(phoneLinks.length).toBe(2);
    expect(phoneLinks[0].href).toBe('tel:+212676886836');
    expect(phoneLinks[1].href).toBe('tel:+212603566841');

    const emailLinks = Array.from(root.querySelectorAll<HTMLAnchorElement>('a[href^="mailto:wwtrading.morocco@gmail.com"]'));
    expect(emailLinks.length).toBeGreaterThanOrEqual(1);
  });

  it('marks form as invalid when empty', () => {
    expect(component.contactForm.valid).toBeFalse();
    expect(component.contactForm.controls.fullName.valid).toBeFalse();
    expect(component.contactForm.controls.email.valid).toBeFalse();
    expect(component.contactForm.controls.phone.valid).toBeFalse();
    expect(component.contactForm.controls.message.valid).toBeFalse();
  });

  it('validates Moroccan phone number formats', () => {
    const phoneControl = component.contactForm.controls.phone;

    phoneControl.setValue('12345');
    expect(phoneControl.valid).toBeFalse();

    phoneControl.setValue('0676886836');
    expect(phoneControl.valid).toBeTrue();

    phoneControl.setValue('+212603566841');
    expect(phoneControl.valid).toBeTrue();
  });

  it('shows error messages when invalid form is submitted', () => {
    component.onSubmit();
    fixture.detectChanges();

    expect(component.contactForm.touched).toBeTrue();
    const errorMessages = root.querySelectorAll('.error-msg');
    expect(errorMessages.length).toBeGreaterThan(0);
    expect(component.isSubmitted()).toBeFalse();
  });

  it('submits valid form and transitions to success panel with reference code', fakeAsync(() => {
    component.contactForm.setValue({
      fullName: 'Mohamed Bennani',
      company: 'Imprimerie du Nord',
      email: 'm.bennani@imprimerie.ma',
      phone: '0676886836',
      service: 'MAINTENANCE',
      message: 'Demande de contrat de maintenance pour 3 copieurs Konica Minolta.'
    });
    expect(component.contactForm.valid).toBeTrue();

    component.onSubmit();
    expect(component.isSubmitting()).toBeTrue();

    tick(700);
    fixture.detectChanges();

    expect(component.isSubmitting()).toBeFalse();
    expect(component.isSubmitted()).toBeTrue();
    expect(component.submissionRef()).toMatch(/^WWT-\d{6}$/);

    const successPanel = root.querySelector('.success-panel');
    expect(successPanel).not.toBeNull();
    expect(successPanel?.textContent).toContain('Merci pour votre message');
    expect(successPanel?.textContent).toContain(component.submissionRef()!);
  }));

  it('resets form when clicking reset in success state', fakeAsync(() => {
    component.contactForm.setValue({
      fullName: 'Mohamed Bennani',
      company: 'Imprimerie du Nord',
      email: 'm.bennani@imprimerie.ma',
      phone: '0676886836',
      service: 'VENTE',
      message: 'Recherche imprimante multifonction Develop Ineo.'
    });
    component.onSubmit();
    tick(700);
    fixture.detectChanges();

    component.resetForm();
    fixture.detectChanges();

    expect(component.isSubmitted()).toBeFalse();
    expect(component.submissionRef()).toBeNull();
    expect(component.contactForm.controls.fullName.value).toBe('');
    expect(root.querySelector('form.contact-form')).not.toBeNull();
  }));

  it('generates a valid mailto URL with encoded parameters', () => {
    component.contactForm.patchValue({
      fullName: 'Rachid Idrissi',
      company: 'BTP Service',
      email: 'r.idrissi@btp.ma',
      phone: '0603566841',
      service: 'AUDIT_DEVIS',
      message: 'Demande devis audit gratuit.'
    });

    const mailto = component.getMailtoLink();
    expect(mailto).toContain('mailto:wwtrading.morocco@gmail.com');
    expect(mailto).toContain('Rachid%20Idrissi');
    expect(mailto).toContain('AUDIT_DEVIS');
  });
});

