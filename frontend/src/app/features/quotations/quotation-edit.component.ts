import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';

import { QuotationApiService } from './quotation-api.service';
import { Quotation, UpdateQuotationRequest } from './quotation.models';

@Component({
  selector: 'wwt-quotation-edit',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quotation-edit.component.html',
  styleUrl: './quotation-edit.component.scss'
})
export class QuotationEditComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private quotationId: string | null = null;

  protected readonly quotation = signal<Quotation | null>(null);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly success = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    subject: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    requestDescription: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(4000)]],
    notes: ['', [Validators.maxLength(10000)]]
  });

  constructor() {
    this.form.disable();
  }

  ngOnInit(): void {
    this.quotationId = this.route.snapshot.paramMap.get('id');
    if (!this.quotationId) {
      this.error.set('La référence du devis est invalide.');
      this.loading.set(false);
      return;
    }

    this.quotationApi.getById(this.quotationId).pipe(
      catchError((failure: unknown) => {
        if (failure instanceof HttpErrorResponse && failure.status === 404) {
          this.notFound.set(true);
        } else {
          this.error.set('Le devis ne peut pas être chargé pour le moment.');
        }
        return of(null);
      }),
      finalize(() => this.loading.set(false))
    ).subscribe((quotation) => {
      if (!quotation) return;
      this.quotation.set(quotation);
      this.form.setValue({
        subject: quotation.subject,
        requestDescription: quotation.requestDescription,
        notes: quotation.notes ?? ''
      });
      this.form.enable();
    });
  }

  protected submit(): void {
    if (!this.quotationId || this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: UpdateQuotationRequest = {
      subject: value.subject.trim(),
      requestDescription: value.requestDescription.trim(),
      notes: value.notes.trim() || null
    };

    this.submitting.set(true);
    this.success.set(false);
    this.error.set(null);
    this.form.disable();
    this.quotationApi.update(this.quotationId, request).pipe(
      finalize(() => {
        this.submitting.set(false);
        this.form.enable();
      })
    ).subscribe({
      next: (quotation) => {
        this.quotation.set(quotation);
        this.success.set(true);
        void this.router.navigate(['/devis', quotation.id]);
      },
      error: () => this.error.set('Les modifications ne peuvent pas être enregistrées pour le moment.')
    });
  }

  protected showError(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }
}
