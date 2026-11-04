import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { OrderApiService } from '../../orders/order-api.service';
import {
  CreatePaymentBankPayload,
  DeliverySettings,
  PaymentBank,
  UpdateDeliverySettingsPayload,
  UpdatePaymentBankPayload
} from '../../orders/order.models';

const MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

@Component({
  selector: 'wwt-admin-payment-banks',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule,
    DashboardLayoutComponent,
    PageHeaderComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-payment-banks.component.html',
  styleUrl: './admin-payment-banks.component.scss'
})
export class AdminPaymentBanksComponent implements OnInit {
  private readonly orderApi = inject(OrderApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly banks = signal<readonly PaymentBank[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly copiedRib = signal<string | null>(null);

  protected copyRib(rib: string): void {
    if (!rib || typeof navigator === 'undefined' || !navigator.clipboard) return;
    navigator.clipboard.writeText(rib).then(() => {
      this.copiedRib.set(rib);
      setTimeout(() => this.copiedRib.set(null), 2500);
    }).catch(() => undefined);
  }

  protected formatRib(rib: string): string {
    if (!rib) return '';
    const clean = rib.replace(/\s+/g, '');
    if (clean.length === 24) {
      return `${clean.slice(0, 3)} ${clean.slice(3, 6)} ${clean.slice(6, 22)} ${clean.slice(22, 24)}`;
    }
    return rib;
  }

  protected resolveBankLogo(logoUrl?: string | null, bankNameOrRef?: string | null): string {
    return this.orderApi.resolveBankLogo(logoUrl, bankNameOrRef);
  }

  protected onLogoError(event: Event, bankNameOrRef?: string | null): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.dataset['fallbackApplied']) {
      img.dataset['fallbackApplied'] = 'true';
      img.src = this.orderApi.resolveBankLogo(null, bankNameOrRef);
    }
  }

  // Delivery Settings state
  protected readonly deliverySettings = signal<DeliverySettings | null>(null);
  protected readonly deliverySettingsLoading = signal(false);
  protected readonly deliverySettingsSaving = signal(false);
  protected readonly deliverySettingsMessage = signal<string | null>(null);
  protected readonly deliverySettingsError = signal<string | null>(null);

  protected readonly deliverySettingsForm: FormGroup = this.fb.group({
    feesEnabled: [true],
    kenitraAreaFee: [300, [Validators.required, Validators.min(0)]],
    casablancaFee: [300, [Validators.required, Validators.min(0)]],
    otherAreasFee: [400, [Validators.required, Validators.min(0)]],
    freeDeliveryThreshold: [20000, [Validators.required, Validators.min(0)]]
  });

  // Modal / Form state
  protected readonly isModalOpen = signal(false);
  protected readonly editingBank = signal<PaymentBank | null>(null);

  // Logo File state
  protected readonly selectedLogoFile = signal<File | null>(null);
  protected readonly logoPreviewUrl = signal<string | null>(null);

  protected readonly bankForm: FormGroup = this.fb.group({
    reference: ['', [Validators.required, Validators.maxLength(100)]],
    bankName: ['', [Validators.required, Validators.maxLength(150)]],
    rib: ['', [Validators.required, Validators.maxLength(50)]],
    beneficiary: ['', [Validators.required, Validators.maxLength(150)]],
    active: [true]
  });

  ngOnInit(): void {
    this.loadBanks();
    this.loadDeliverySettings();
  }

  protected loadDeliverySettings(): void {
    this.deliverySettingsLoading.set(true);
    this.deliverySettingsError.set(null);
    this.orderApi.adminGetDeliverySettings().pipe(
      finalize(() => this.deliverySettingsLoading.set(false))
    ).subscribe({
      next: (settings) => {
        this.deliverySettings.set(settings);
        this.deliverySettingsForm.patchValue({
          feesEnabled: settings.feesEnabled,
          kenitraAreaFee: settings.kenitraAreaFee,
          casablancaFee: settings.casablancaFee,
          otherAreasFee: settings.otherAreasFee,
          freeDeliveryThreshold: settings.freeDeliveryThreshold
        });
      },
      error: () => {
        this.deliverySettingsError.set('Impossible de charger la configuration des frais de livraison.');
      }
    });
  }

  protected toggleDeliveryFeesEnabled(): void {
    const current = this.deliverySettings();
    const nextEnabled = !(current ? current.feesEnabled : this.deliverySettingsForm.value.feesEnabled);
    this.deliverySettingsForm.patchValue({ feesEnabled: nextEnabled });
    this.saveDeliverySettings();
  }

  protected saveDeliverySettings(): void {
    if (this.deliverySettingsForm.invalid || this.deliverySettingsSaving()) {
      this.deliverySettingsForm.markAllAsTouched();
      return;
    }
    this.deliverySettingsSaving.set(true);
    this.deliverySettingsError.set(null);
    this.deliverySettingsMessage.set(null);
    const val = this.deliverySettingsForm.value;
    const payload: UpdateDeliverySettingsPayload = {
      feesEnabled: !!val.feesEnabled,
      kenitraAreaFee: Number(val.kenitraAreaFee),
      casablancaFee: Number(val.casablancaFee),
      otherAreasFee: Number(val.otherAreasFee),
      freeDeliveryThreshold: Number(val.freeDeliveryThreshold)
    };
    this.orderApi.adminUpdateDeliverySettings(payload).pipe(
      finalize(() => this.deliverySettingsSaving.set(false))
    ).subscribe({
      next: (updated) => {
        this.deliverySettings.set(updated);
        this.deliverySettingsForm.patchValue({
          feesEnabled: updated.feesEnabled,
          kenitraAreaFee: updated.kenitraAreaFee,
          casablancaFee: updated.casablancaFee,
          otherAreasFee: updated.otherAreasFee,
          freeDeliveryThreshold: updated.freeDeliveryThreshold
        });
        this.deliverySettingsMessage.set(
          updated.feesEnabled
            ? 'Frais de livraison activés et enregistrés.'
            : 'Frais de livraison désactivés (livraison à 0 DH pour toutes les nouvelles commandes).'
        );
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          if (err.status === 403) {
            this.deliverySettingsError.set('Accès refusé. Rôle Administrateur requis.');
            return;
          }
          if (err.status === 401) {
            this.deliverySettingsError.set('Session expirée. Veuillez vous reconnecter.');
            return;
          }
          if (err.error?.message) {
            this.deliverySettingsError.set(`Erreur: ${err.error.message}`);
            return;
          }
        }
        this.deliverySettingsError.set('Impossible d’enregistrer la configuration des frais de livraison.');
      }
    });
  }

  protected loadBanks(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orderApi.adminGetAllPaymentBanks().pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (data) => this.banks.set(data),
      error: (err: unknown) => this.error.set(
        err instanceof HttpErrorResponse && err.status === 403
          ? 'Accès refusé. Rôle Administrateur requis.'
          : 'Impossible de charger la liste des banques.'
      )
    });
  }

  protected openAddModal(): void {
    this.editingBank.set(null);
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(null);
    this.bankForm.reset({
      reference: '',
      bankName: '',
      rib: '',
      beneficiary: '',
      active: true
    });
    this.formError.set(null);
    this.isModalOpen.set(true);
  }

  protected openEditModal(bank: PaymentBank): void {
    this.editingBank.set(bank);
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(bank.logoUrl || null);
    this.bankForm.patchValue({
      reference: bank.reference,
      bankName: bank.name || (bank as any).bankName || '',
      rib: bank.rib,
      beneficiary: bank.beneficiary || '',
      active: bank.active !== false
    });
    this.formError.set(null);
    this.isModalOpen.set(true);
  }

  protected closeModal(): void {
    this.isModalOpen.set(false);
    this.editingBank.set(null);
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(null);
    this.formError.set(null);
  }

  protected onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;

    if (!ALLOWED_MIME_TYPES.includes(file.type.toLowerCase())) {
      this.formError.set('Seuls les formats JPG, JPEG, PNG et WebP sont autorisés.');
      input.value = '';
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      this.formError.set('Le fichier est trop volumineux. La taille maximale est de 2 MB.');
      input.value = '';
      return;
    }

    this.formError.set(null);
    this.selectedLogoFile.set(file);
    this.logoPreviewUrl.set(URL.createObjectURL(file));
  }

  protected removeSelectedLogo(): void {
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(null);
  }

  protected saveBank(): void {
    if (this.bankForm.invalid || this.submitting()) {
      this.bankForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.formError.set(null);

    const formVal = this.bankForm.value;
    const editing = this.editingBank();
    const logoFile = this.selectedLogoFile();

    if (editing && editing.id) {
      const payload: UpdatePaymentBankPayload = {
        reference: formVal.reference?.trim(),
        bankName: formVal.bankName?.trim(),
        rib: formVal.rib?.trim(),
        beneficiary: formVal.beneficiary?.trim(),
        active: formVal.active
      };

      this.orderApi.adminUpdatePaymentBank(editing.id, payload, logoFile).pipe(
        finalize(() => this.submitting.set(false))
      ).subscribe({
        next: () => {
          this.closeModal();
          this.loadBanks();
        },
        error: (err: unknown) => this.handleFormError(err)
      });
    } else {
      const payload: CreatePaymentBankPayload = {
        reference: formVal.reference?.trim(),
        bankName: formVal.bankName?.trim(),
        rib: formVal.rib?.trim(),
        beneficiary: formVal.beneficiary?.trim(),
        active: formVal.active
      };

      this.orderApi.adminCreatePaymentBank(payload, logoFile).pipe(
        finalize(() => this.submitting.set(false))
      ).subscribe({
        next: () => {
          this.closeModal();
          this.loadBanks();
        },
        error: (err: unknown) => this.handleFormError(err)
      });
    }
  }

  protected toggleActive(bank: PaymentBank): void {
    if (!bank.id) return;
    const newActiveState = !(bank.active !== false);

    this.orderApi.adminTogglePaymentBankActive(bank.id, newActiveState).subscribe({
      next: () => this.loadBanks(),
      error: () => alert('Impossible de modifier le statut de la banque.')
    });
  }

  private handleFormError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 409) {
        this.formError.set('Cette référence de banque existe déjà. Veuillez en choisir une autre.');
        return;
      }
      if (err.status === 400) {
        this.formError.set(err.error?.detail || 'Champs invalides ou logo invalide (2 MB max, JPG/PNG/WebP).');
        return;
      }
    }
    this.formError.set('Une erreur est survenue lors de l’enregistrement.');
  }

  protected bankNameDisplay(bank: PaymentBank): string {
    return bank.name || (bank as any).bankName || bank.reference;
  }
}
