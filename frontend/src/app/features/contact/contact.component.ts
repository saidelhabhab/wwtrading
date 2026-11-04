import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

export interface ContactInquiry {
  fullName: string;
  company?: string;
  email: string;
  phone: string;
  service: string;
  message: string;
}

export const WWTRADING_SERVICES = [
  { id: 'VENTE', label: 'Vente de machines & imprimantes professionnelles' },
  { id: 'LOCATION', label: 'Location d’équipements d’impression (LFI / LLD)' },
  { id: 'MAINTENANCE', label: 'Maintenance & Contrat d’entretien (H+8)' },
  { id: 'TONERS', label: 'Toners & cartouches d’origine' },
  { id: 'CONSOMMABLES', label: 'Consommables & fournitures pour imprimantes' },
  { id: 'PIECES', label: 'Pièces de rechange Konica Minolta & Develop' },
  { id: 'SUPPORT', label: 'Support technique & Télémaintenance à distance' },
  { id: 'AUDIT_DEVIS', label: 'Audit gratuit & Devis personnalisé' },
  { id: 'AUTRE', label: 'Autre demande' }
] as const;

@Component({
  selector: 'wwt-contact',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './contact.component.html',
  styleUrl: './contact.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContactComponent {
  readonly servicesList = WWTRADING_SERVICES;

  readonly isSubmitting = signal(false);
  readonly isSubmitted = signal(false);
  readonly submissionRef = signal<string | null>(null);

  readonly contactForm = new FormGroup({
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100), Validators.pattern(/\S+/)]
    }),
    company: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(120)]
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(120)]
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(/^(?:(?:\+|00)212|0)[5-7]\d{8}$/)
      ]
    }),
    service: new FormControl<string>(WWTRADING_SERVICES[0].id, {
      nonNullable: true,
      validators: [Validators.required]
    }),
    message: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(1500)]
    })
  });

  scrollToForm(event: Event): void {
    event.preventDefault();
    document.getElementById('contact-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  onSubmit(): void {
    if (this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);

    // Simulate async submission with production feedback
    setTimeout(() => {
      const randomRef = 'WWT-' + Math.floor(100000 + Math.random() * 900000);
      this.submissionRef.set(randomRef);
      this.isSubmitting.set(false);
      this.isSubmitted.set(true);
    }, 600);
  }

  resetForm(): void {
    this.contactForm.reset({
      fullName: '',
      company: '',
      email: '',
      phone: '',
      service: WWTRADING_SERVICES[0].id,
      message: ''
    });
    this.isSubmitted.set(false);
    this.submissionRef.set(null);
  }

  getMailtoLink(): string {
    const values = this.contactForm.getRawValue();
    const subject = encodeURIComponent(`[Demande WWTRADING] ${values.service} - ${values.fullName || 'Contact'}`);
    const body = encodeURIComponent(
      `Nom: ${values.fullName}\n` +
      `Société: ${values.company || 'Non renseignée'}\n` +
      `Téléphone: ${values.phone}\n` +
      `Email: ${values.email}\n` +
      `Prestation souhaitée: ${values.service}\n\n` +
      `Message:\n${values.message}`
    );
    return `mailto:wwtrading.morocco@gmail.com?subject=${subject}&body=${body}`;
  }
}

