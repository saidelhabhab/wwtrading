import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { QuotationApiService } from './quotation-api.service';
import { getQuotationStatusLabel, getServiceTypeLabel, Quotation, QuotationStatus } from './quotation.models';

interface ProposalLineDraft { readonly productId: string; readonly quantity: number; readonly proposedUnitPrice: number | null; }

@Component({
  selector: 'wwt-quotation-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quotation-detail.component.html',
  styleUrl: './quotation-detail.component.scss'
})
export class QuotationDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly auth = inject(AuthService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly quotation = signal<Quotation | null>(null);
  readonly updatingStatus = signal(false);
  readonly updateError = signal<string | null>(null);
  readonly pdfDownloading = signal(false);
  readonly pdfError = signal<string | null>(null);
  readonly showConfirmModal = signal(false);
  readonly proposalLines = signal<readonly ProposalLineDraft[]>([]);

  readonly userRole = computed<WwtradingRole>(() => this.auth.roles()[0] ?? 'COMMERCIAL');

  readonly backPath = computed(() => {
    const role = this.userRole();
    switch (role) {
      case 'ADMIN': return '/admin/devis';
      case 'MANAGER': return '/manager/devis';
      case 'COMMERCIAL': return '/commercial/devis';
      default: return '/commercial/devis';
    }
  });

  readonly statusForm = new FormGroup({
    targetStatus: new FormControl<QuotationStatus>('IN_REVIEW', { nonNullable: true, validators: [Validators.required] }),
    proposedAmount: new FormControl<number | null>(null),
    commercialNotes: new FormControl<string>('', { nonNullable: true }),
    discount: new FormControl<number>(0, { nonNullable: true, validators: [Validators.min(0)] }),
    deliveryFee: new FormControl<number>(0, { nonNullable: true, validators: [Validators.min(0)] }),
    validUntil: new FormControl<string>('', { nonNullable: true })
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? this.route.snapshot.paramMap.get('orderId');
    if (id) {
      this.loadQuotation(id);
    } else {
      this.error.set('Identifiant de devis invalide.');
      this.loading.set(false);
    }
  }

  loadQuotation(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.quotationApi.getQuotation(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (q) => {
          this.quotation.set(q);
          this.syncFormWithQuotation(q);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 404) {
            this.error.set('Cette demande de devis n’existe pas.');
          } else if (err.status === 403) {
            this.error.set('Vous n’avez pas l’autorisation de consulter ce devis.');
          } else {
            this.error.set('Impossible de charger cette demande de devis.');
          }
        }
      });
  }

  downloadPdf(): void {
    const current = this.quotation();
    if (!current || this.pdfDownloading()) return;
    this.pdfDownloading.set(true);
    this.pdfError.set(null);
    this.quotationApi.getQuotationPdf(current.id).pipe(finalize(() => this.pdfDownloading.set(false)))
      .subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `devis-${current.id}.pdf`;
          document.body.appendChild(link);
          link.click();
          link.remove();
          globalThis.setTimeout(() => URL.revokeObjectURL(url), 10_000);
        },
        error: (failure: unknown) => {
          this.pdfError.set(failure instanceof HttpErrorResponse && failure.status === 404
            ? 'Ce devis est introuvable.'
            : failure instanceof HttpErrorResponse && failure.status === 403
              ? 'Vous n’avez pas accès à ce document.'
              : 'Le PDF du devis ne peut pas être téléchargé pour le moment.');
        }
      });
  }

  syncFormWithQuotation(q: Quotation): void {
    this.proposalLines.set((q.lines ?? []).map(line => ({
      productId: line.productId,
      quantity: line.proposedQuantity ?? line.requestedQuantity,
      proposedUnitPrice: line.proposedUnitPrice ?? line.catalogUnitPrice ?? null
    })));
    this.statusForm.patchValue({
      discount: q.discount ?? 0,
      deliveryFee: q.deliveryFee ?? 0,
      validUntil: q.validUntil ?? ''
    });
    if (q.status === 'PENDING') {
      this.statusForm.patchValue({
        targetStatus: 'IN_REVIEW',
        proposedAmount: q.proposedAmount ?? null,
        commercialNotes: q.commercialNotes ?? ''
      });
    } else if (q.status === 'IN_REVIEW') {
      this.statusForm.patchValue({
        targetStatus: 'QUOTED',
        proposedAmount: q.proposedAmount ?? q.estimatedBudget ?? null,
        commercialNotes: q.commercialNotes ?? ''
      });
    } else {
      this.statusForm.patchValue({
        targetStatus: q.status,
        proposedAmount: q.proposedAmount ?? null,
        commercialNotes: q.commercialNotes ?? ''
      });
    }
  }

  onSelectTargetStatus(target: QuotationStatus): void {
    this.statusForm.controls.targetStatus.setValue(target);
    const amountCtrl = this.statusForm.controls.proposedAmount;

    if (target === 'QUOTED' && !this.quotation()?.lines?.length) {
      amountCtrl.setValidators([Validators.required, Validators.min(0.01)]);
    } else {
      amountCtrl.clearValidators();
    }
    amountCtrl.updateValueAndValidity();
  }

  promptConfirmation(): void {
    this.updateError.set(null);
    const target = this.statusForm.controls.targetStatus.value;
    const amountCtrl = this.statusForm.controls.proposedAmount;

    if (target === 'QUOTED' && !this.quotation()?.lines?.length) {
      amountCtrl.setValidators([Validators.required, Validators.min(0.01)]);
      amountCtrl.updateValueAndValidity();
      amountCtrl.markAsTouched();

      if (amountCtrl.invalid) {
        return;
      }
    }

    if (target === 'QUOTED' && this.quotation()?.lines?.length) {
      const lines = this.proposalLines();
      if (!this.statusForm.controls.validUntil.value
          || lines.some(line => !Number.isInteger(line.quantity) || line.quantity < 1 || line.proposedUnitPrice === null
            || !Number.isFinite(line.proposedUnitPrice) || line.proposedUnitPrice < 0)) {
        this.updateError.set('Renseignez les quantités, les prix proposés et la validité du devis.');
        return;
      }
    }

    if (this.statusForm.invalid) {
      this.statusForm.markAllAsTouched();
      return;
    }

    this.showConfirmModal.set(true);
  }

  cancelConfirmation(): void {
    this.showConfirmModal.set(false);
  }

  executeStatusUpdate(): void {
    const q = this.quotation();
    if (!q) return;

    this.showConfirmModal.set(false);
    this.updatingStatus.set(true);
    this.updateError.set(null);

    const val = this.statusForm.getRawValue();

    const catalogProposal = val.targetStatus === 'QUOTED' && !!q.lines?.length;
    this.quotationApi.updateQuotationStatus(q.id, {
      status: val.targetStatus,
      proposedAmount: catalogProposal ? undefined : val.targetStatus === 'QUOTED' ? val.proposedAmount : (val.proposedAmount ?? undefined),
      commercialNotes: val.commercialNotes || undefined,
      ...(catalogProposal ? {
        proposedLines: this.proposalLines().map(line => ({
          productId: line.productId, quantity: line.quantity,
          proposedUnitPrice: line.proposedUnitPrice ?? 0
        })),
        discount: val.discount, deliveryFee: val.deliveryFee, validUntil: val.validUntil
      } : {})
    })
      .pipe(finalize(() => this.updatingStatus.set(false)))
      .subscribe({
        next: (updated) => {
          this.quotation.set(updated);
          this.syncFormWithQuotation(updated);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 400 && err.error?.detail) {
            this.updateError.set(err.error.detail);
          } else {
            this.updateError.set('Impossible de mettre à jour le statut du devis.');
          }
        }
      });
  }

  setProposalQuantity(productId: string, value: string): void {
    this.proposalLines.update(lines => lines.map(line => line.productId === productId
      ? { ...line, quantity: Number(value) } : line));
  }

  setProposalUnitPrice(productId: string, value: string): void {
    this.proposalLines.update(lines => lines.map(line => line.productId === productId
      ? { ...line, proposedUnitPrice: value === '' ? null : Number(value) } : line));
  }

  takeOverInReview(): void {
    const q = this.quotation();
    if (!q) return;

    this.updatingStatus.set(true);
    this.updateError.set(null);

    this.quotationApi.updateQuotationStatus(q.id, {
      status: 'IN_REVIEW',
      commercialNotes: q.commercialNotes ?? undefined
    })
      .pipe(finalize(() => this.updatingStatus.set(false)))
      .subscribe({
        next: (updated) => {
          this.quotation.set(updated);
          this.syncFormWithQuotation(updated);
        },
        error: () => this.updateError.set('Impossible de passer la demande en cours d’étude.')
      });
  }

  getStatusLabel(status: QuotationStatus | string): string {
    return getQuotationStatusLabel(status);
  }

  getServiceLabel(serviceType: string): string {
    return getServiceTypeLabel(serviceType);
  }

  getStatusBadgeClass(status: QuotationStatus | string): string {
    switch (status) {
      case 'PENDING': return 'badge--pending';
      case 'IN_REVIEW': return 'badge--in-review';
      case 'QUOTED': return 'badge--quoted';
      case 'ACCEPTED': return 'badge--accepted';
      case 'REJECTED': return 'badge--rejected';
      case 'CANCELLED': return 'badge--cancelled';
      default: return '';
    }
  }

  getTimelineLabel(t: string | null): string {
    switch (t) {
      case 'ASAP': return 'Dès que possible';
      case 'THIS_WEEK': return 'Cette semaine';
      case 'THIS_MONTH': return 'Ce mois-ci';
      case 'TO_DEFINE': return 'À définir';
      default: return t ?? '—';
    }
  }
}
