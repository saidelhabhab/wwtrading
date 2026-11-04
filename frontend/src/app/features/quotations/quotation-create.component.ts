import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { QuotationApiService } from './quotation-api.service';
import { CreateQuotationRequest } from './quotation.models';

@Component({
  selector: 'wwt-quotation-create',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quotation-create.component.html',
  styleUrl: './quotation-create.component.scss'
})
export class QuotationCreateComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly success = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    customerReference: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(64)]],
    subject: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    requestDescription: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(4000)]],
    notes: ['', [Validators.maxLength(10000)]]
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: CreateQuotationRequest = {
      customerReference: value.customerReference.trim(),
      subject: value.subject.trim(),
      requestDescription: value.requestDescription.trim(),
      notes: value.notes.trim() || null
    };

    this.submitting.set(true);
    this.success.set(false);
    this.error.set(null);
    this.quotationApi.create(request).pipe(
      finalize(() => this.submitting.set(false))
    ).subscribe({
      next: () => {
        this.success.set(true);
        void this.router.navigate(['/devis']);
      },
      error: () => this.error.set('Le devis ne peut pas être créé pour le moment.')
    });
  }

  protected showError(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }
}
