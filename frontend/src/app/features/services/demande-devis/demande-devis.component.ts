import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize, Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { ProductApiService } from '../../products/product-api.service';
import { Product, ProductPage } from '../../products/product.models';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import { CreateQuotationRequest, Quotation } from '../../quotations/quotation.models';

export interface SelectedProduct {
  readonly product: Product;
  readonly quantity: number;
}

const DRAFT_STORAGE_KEY = 'wwt_devis_draft_v1';

@Component({
  selector: 'wwt-demande-devis',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './demande-devis.component.html',
  styleUrl: './demande-devis.component.scss'
})
export class DemandeDevisComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly quotationApi = inject(QuotationApiService);
  protected readonly productApi = inject(ProductApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isSubmitted = signal(false);
  readonly createdQuotation = signal<Quotation | null>(null);
  readonly submitting = signal(false);
  readonly submissionError = signal<string | null>(null);
  readonly draftRestored = signal(false);

  readonly canViewMyQuotations = computed(() => this.auth.authenticated());

  readonly products = signal<readonly Product[]>([]);
  readonly selectedProducts = signal<readonly SelectedProduct[]>([]);
  readonly productsLoading = signal(false);
  readonly productsError = signal<string | null>(null);
  readonly productPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalProducts = signal(0);
  readonly searchTerm = signal('');

  private readonly searchSubject = new Subject<string>();
  private submissionIdempotencyKey: string | null = null;

  readonly devisForm = new FormGroup({
    fullName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    company: new FormControl('', { nonNullable: true }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern(/^[0-9+\s\-().]{8,25}$/)
      ]
    }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    additionalMessage: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(4000)] })
  });

  ngOnInit(): void {
    this.restoreDraft();
    this.prefillAuthenticatedUser();
    this.setupAutoSave();
    this.setupSearch();
    this.loadProducts(0, '');
  }

  private setupSearch(): void {
    this.searchSubject
      .pipe(
        debounceTime(350),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((term) => {
        this.loadProducts(0, term);
      });
  }

  private setupAutoSave(): void {
    this.devisForm.valueChanges
      .pipe(
        debounceTime(400),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.submissionIdempotencyKey = null;
        this.saveDraft();
      });
  }

  private prefillAuthenticatedUser(): void {
    if (!this.auth.authenticated()) return;
    const currentUser = this.auth.user();
    if (!currentUser) return;

    const currentValues = this.devisForm.getRawValue();
    const patch: Partial<{ fullName: string; email: string }> = {};

    if (!currentValues.fullName && currentUser.displayName) {
      patch.fullName = currentUser.displayName;
    }
    if (!currentValues.email && currentUser.email) {
      patch.email = currentUser.email;
    }

    if (Object.keys(patch).length > 0) {
      this.devisForm.patchValue(patch);
    }
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
    this.searchSubject.next(value);
  }

  clearSearch(): void {
    this.searchTerm.set('');
    this.loadProducts(0, '');
  }

  loadProducts(page: number, search?: string): void {
    const term = search !== undefined ? search : this.searchTerm();
    this.productsLoading.set(true);
    this.productsError.set(null);

    this.productApi
      .list(page, 20, 'name,asc', { search: term ? term.trim() : undefined })
      .pipe(finalize(() => this.productsLoading.set(false)))
      .subscribe({
        next: (result: ProductPage) => {
          this.products.set(result.content.filter((p) => p.active));
          this.productPage.set(result.number);
          this.totalPages.set(result.totalPages);
          this.totalProducts.set(result.totalElements);
        },
        error: () => this.productsError.set('Impossible de charger les produits du catalogue. Veuillez réessayer.')
      });
  }

  addProduct(product: Product): void {
    if (!product.active || this.selectedProducts().some((line) => line.product.id === product.id)) return;
    this.submissionIdempotencyKey = null;
    this.selectedProducts.update((lines) => [...lines, { product, quantity: 1 }]);
    this.saveDraft();
  }

  isSelected(productId: string): boolean {
    return this.selectedProducts().some((line) => line.product.id === productId);
  }

  changeQuantity(productId: string, delta: number): void {
    this.submissionIdempotencyKey = null;
    this.selectedProducts.update((lines) =>
      lines.map((line) =>
        line.product.id === productId ? { ...line, quantity: Math.max(1, line.quantity + delta) } : line
      )
    );
    this.saveDraft();
  }

  removeProduct(productId: string): void {
    this.submissionIdempotencyKey = null;
    this.selectedProducts.update((lines) => lines.filter((line) => line.product.id !== productId));
    this.saveDraft();
  }

  private getOrCreateIdempotencyKey(): string {
    if (!this.submissionIdempotencyKey) {
      this.submissionIdempotencyKey = crypto.randomUUID();
      this.saveDraft();
    }
    return this.submissionIdempotencyKey;
  }

  submitQuoteRequest(): void {
    if (this.submitting()) return;

    if (this.selectedProducts().length === 0) {
      this.submissionError.set('Veuillez sélectionner au moins un équipement du catalogue.');
      return;
    }

    if (this.devisForm.invalid) {
      this.devisForm.markAllAsTouched();
      this.submissionError.set('Veuillez vérifier et compléter tous les champs obligatoires.');
      return;
    }

    const value = this.devisForm.getRawValue();
    const idempotencyKey = this.getOrCreateIdempotencyKey();
    const request: CreateQuotationRequest = {
      serviceType: 'VENTE',
      fullName: value.fullName.trim(),
      company: value.company.trim() || null,
      phone: value.phone.trim(),
      email: value.email.trim(),
      city: value.city.trim(),
      additionalMessage: value.additionalMessage.trim() || null,
      requestedLines: this.selectedProducts().map((line) => ({
        productId: line.product.id,
        quantity: line.quantity
      })),
      idempotencyKey
    };

    this.submitting.set(true);
    this.submissionError.set(null);

    this.quotationApi
      .createQuotation(request)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (created) => {
          this.submissionIdempotencyKey = null;
          this.createdQuotation.set(created);
          this.isSubmitted.set(true);
          this.clearDraft();
        },
        error: (failure: unknown) => this.handleSubmissionError(failure)
      });
  }

  resetForm(): void {
    this.submissionIdempotencyKey = null;
    this.devisForm.reset();
    this.selectedProducts.set([]);
    this.createdQuotation.set(null);
    this.isSubmitted.set(false);
    this.submissionError.set(null);
    this.draftRestored.set(false);
    this.clearDraft();
    this.loadProducts(0, '');
  }

  private handleSubmissionError(failure: unknown): void {
    if (failure instanceof HttpErrorResponse) {
      if (failure.status === 400) {
        this.submissionError.set(
          'Vérifiez les produits sélectionnés et les coordonnées saisies avant de réessayer.'
        );
      } else if (failure.status === 403) {
        this.submissionError.set("Vous n'êtes pas autorisé à envoyer cette demande de devis.");
      } else if (failure.status === 409) {
        this.submissionError.set('Une demande similaire a déjà été envoyée. Notre équipe la traite actuellement.');
      } else if (failure.status === 429) {
        this.submissionError.set(
          'Trop de demandes de devis envoyées en peu de temps. Veuillez patienter un instant avant de réessayer.'
        );
      } else if (failure.status >= 500) {
        this.submissionError.set(
          'Le service de devis est momentanément indisponible. Vos informations sont conservées, veuillez réessayer dans un instant.'
        );
      } else {
        this.submissionError.set('La demande ne peut pas être envoyée pour le moment. Veuillez réessayer.');
      }
    } else {
      this.submissionError.set('Une erreur inattendue est survenue. Veuillez vérifier votre connexion.');
    }
  }

  private saveDraft(): void {
    if (this.isSubmitted()) return;
    try {
      const draft = {
        form: this.devisForm.getRawValue(),
        selectedProducts: this.selectedProducts(),
        idempotencyKey: this.submissionIdempotencyKey
      };
      sessionStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft));
    } catch {
      // Ignore sessionStorage errors (quota / private mode)
    }
  }

  private restoreDraft(): void {
    try {
      const raw = sessionStorage.getItem(DRAFT_STORAGE_KEY);
      if (!raw) return;
      const draft = JSON.parse(raw);
      if (draft?.form) {
        this.devisForm.patchValue(draft.form);
      }
      if (Array.isArray(draft?.selectedProducts) && draft.selectedProducts.length > 0) {
        this.selectedProducts.set(draft.selectedProducts);
      }
      if (typeof draft?.idempotencyKey === 'string' && draft.idempotencyKey.trim().length > 0) {
        this.submissionIdempotencyKey = draft.idempotencyKey.trim();
      }
      this.draftRestored.set(true);
    } catch {
      // Ignore parse errors
    }
  }

  private clearDraft(): void {
    try {
      sessionStorage.removeItem(DRAFT_STORAGE_KEY);
    } catch {
      // Ignore
    }
  }
}
