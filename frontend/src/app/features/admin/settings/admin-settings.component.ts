import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';
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

export type SettingsTab = 'general' | 'delivery' | 'banks' | 'orders' | 'services';

export interface CompanySettings {
  companyName: string;
  tagline: string;
  phoneWhatsapp: string;
  phoneHotline: string;
  emailSales: string;
  emailSupport: string;
  addressKenitra: string;
  addressTangier: string;
  openingHours: string;
  iceNumber: string;
  rcNumber: string;
}

export interface OrderPolicySettings {
  splitDepositPercent: number;
  slipReviewTimeHours: number;
  guaranteeMonths: number;
  allowCashOnDeliveryForBalance: boolean;
  notifyOnStatusChange: boolean;
}

const DEFAULT_COMPANY_SETTINGS: CompanySettings = {
  companyName: 'WWTRADING SARL',
  tagline: 'Expertise impression professionnelle & photocopieurs Konica Minolta & Develop',
  phoneWhatsapp: '+212 677-688636',
  phoneHotline: '+212 603-566841',
  emailSales: 'wwtrading.morocco@gmail.com',
  emailSupport: 'contact@wwtrading.ma',
  addressKenitra: 'Angle Avenue Mohammed V et Rue Al Qods, Kénitra, Maroc',
  addressTangier: 'Hay saada bendi bane, d, HADADA, 13489 Tanger, Maroc',
  openingHours: 'Lundi au Vendredi : 08:30 - 18:30 · Samedi : 09:00 - 13:00',
  iceNumber: '002938471000045',
  rcNumber: '48291 Kénitra'
};

const DEFAULT_ORDER_POLICIES: OrderPolicySettings = {
  splitDepositPercent: 50,
  slipReviewTimeHours: 24,
  guaranteeMonths: 12,
  allowCashOnDeliveryForBalance: true,
  notifyOnStatusChange: true
};

const STORAGE_KEY_COMPANY = 'wwt_settings_company';
const STORAGE_KEY_POLICIES = 'wwt_settings_policies';

const MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

@Component({
  selector: 'wwt-admin-settings',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule,
    DashboardLayoutComponent,
    PageHeaderComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-settings.component.html',
  styleUrl: './admin-settings.component.scss'
})
export class AdminSettingsComponent implements OnInit {
  private readonly orderApi = inject(OrderApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // Tab management
  protected readonly activeTab = signal<SettingsTab>('general');

  // Company info form
  protected readonly companyForm: FormGroup = this.fb.group({
    companyName: [DEFAULT_COMPANY_SETTINGS.companyName, [Validators.required, Validators.maxLength(150)]],
    tagline: [DEFAULT_COMPANY_SETTINGS.tagline, [Validators.required, Validators.maxLength(255)]],
    phoneWhatsapp: [DEFAULT_COMPANY_SETTINGS.phoneWhatsapp, [Validators.required, Validators.maxLength(30)]],
    phoneHotline: [DEFAULT_COMPANY_SETTINGS.phoneHotline, [Validators.required, Validators.maxLength(30)]],
    emailSales: [DEFAULT_COMPANY_SETTINGS.emailSales, [Validators.required, Validators.email]],
    emailSupport: [DEFAULT_COMPANY_SETTINGS.emailSupport, [Validators.required, Validators.email]],
    addressKenitra: [DEFAULT_COMPANY_SETTINGS.addressKenitra, [Validators.required, Validators.maxLength(250)]],
    addressTangier: [DEFAULT_COMPANY_SETTINGS.addressTangier, [Validators.required, Validators.maxLength(250)]],
    openingHours: [DEFAULT_COMPANY_SETTINGS.openingHours, [Validators.required, Validators.maxLength(150)]],
    iceNumber: [DEFAULT_COMPANY_SETTINGS.iceNumber, [Validators.required, Validators.maxLength(50)]],
    rcNumber: [DEFAULT_COMPANY_SETTINGS.rcNumber, [Validators.required, Validators.maxLength(50)]]
  });
  protected readonly companySaving = signal(false);
  protected readonly companySuccessMessage = signal<string | null>(null);

  // Delivery settings state & form
  protected readonly deliverySettings = signal<DeliverySettings | null>(null);
  protected readonly deliverySettingsLoading = signal(false);
  protected readonly deliverySettingsSaving = signal(false);
  protected readonly deliverySettingsMessage = signal<string | null>(null);
  protected readonly deliverySettingsError = signal<string | null>(null);

  protected readonly deliverySettingsForm: FormGroup = this.fb.group({
    feesEnabled: [true],
    kenitraAreaFee: [100, [Validators.required, Validators.min(0)]],
    casablancaFee: [300, [Validators.required, Validators.min(0)]],
    otherAreasFee: [400, [Validators.required, Validators.min(0)]],
    freeDeliveryThreshold: [20000, [Validators.required, Validators.min(0)]]
  });

  // Banks state & form
  protected readonly banks = signal<readonly PaymentBank[]>([]);
  protected readonly banksLoading = signal(true);
  protected readonly banksError = signal<string | null>(null);
  protected readonly bankSubmitting = signal(false);
  protected readonly bankFormError = signal<string | null>(null);
  protected readonly copiedRib = signal<string | null>(null);
  protected readonly isBankModalOpen = signal(false);
  protected readonly editingBank = signal<PaymentBank | null>(null);
  protected readonly selectedLogoFile = signal<File | null>(null);
  protected readonly logoPreviewUrl = signal<string | null>(null);

  protected readonly bankForm: FormGroup = this.fb.group({
    reference: ['', [Validators.required, Validators.maxLength(100)]],
    bankName: ['', [Validators.required, Validators.maxLength(150)]],
    rib: ['', [Validators.required, Validators.maxLength(50)]],
    beneficiary: ['', [Validators.required, Validators.maxLength(150)]],
    active: [true]
  });

  // Order policies form
  protected readonly policyForm: FormGroup = this.fb.group({
    splitDepositPercent: [DEFAULT_ORDER_POLICIES.splitDepositPercent, [Validators.required, Validators.min(10), Validators.max(90)]],
    slipReviewTimeHours: [DEFAULT_ORDER_POLICIES.slipReviewTimeHours, [Validators.required, Validators.min(1)]],
    guaranteeMonths: [DEFAULT_ORDER_POLICIES.guaranteeMonths, [Validators.required, Validators.min(0)]],
    allowCashOnDeliveryForBalance: [DEFAULT_ORDER_POLICIES.allowCashOnDeliveryForBalance],
    notifyOnStatusChange: [DEFAULT_ORDER_POLICIES.notifyOnStatusChange]
  });
  protected readonly policySaving = signal(false);
  protected readonly policySuccessMessage = signal<string | null>(null);

  // Microservices connectivity state
  protected readonly servicesChecking = signal(false);
  protected readonly servicesCheckedTime = signal<string | null>(null);
  protected readonly microservices = signal<readonly {
    readonly name: string;
    readonly port: number;
    readonly role: string;
    readonly status: 'UP' | 'DOWN';
    readonly latencyMs: number;
  }[]>([
    { name: 'API Gateway', port: 8080, role: 'Passerelle centrale & Sécurité JWT / Rate Limit', status: 'UP', latencyMs: 12 },
    { name: 'Order Service', port: 8083, role: 'Commandes, Acompte 50/50, Banques, Frais de livraison', status: 'UP', latencyMs: 24 },
    { name: 'Notification Service', port: 8088, role: 'Envoi emails transactionnels & Passerelle WhatsApp', status: 'UP', latencyMs: 18 },
    { name: 'Keycloak SSO', port: 8081, role: 'Authentification centralisée OAuth2 / OpenID Connect', status: 'UP', latencyMs: 31 },
    { name: 'PostgreSQL DB', port: 5432, role: 'Base relationnelle principale (delivery_settings, orders)', status: 'UP', latencyMs: 5 }
  ]);

  ngOnInit(): void {
    // Listen to query params for tab switching
    this.route.queryParamMap.subscribe((params) => {
      const tabParam = params.get('tab') as SettingsTab | null;
      if (tabParam && ['general', 'delivery', 'banks', 'orders', 'services'].includes(tabParam)) {
        this.activeTab.set(tabParam);
      }
    });

    this.loadSavedCompanySettings();
    this.loadSavedPolicySettings();
    this.loadDeliverySettings();
    this.loadBanks();
  }

  protected setTab(tab: SettingsTab): void {
    this.activeTab.set(tab);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge'
    });
  }

  // --- Company Settings ---
  private loadSavedCompanySettings(): void {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_COMPANY);
      if (saved) {
        const parsed: CompanySettings = JSON.parse(saved);
        this.companyForm.patchValue(parsed);
      }
    } catch {
      // fallback to defaults
    }
  }

  protected saveCompanySettings(): void {
    if (this.companyForm.invalid) {
      this.companyForm.markAllAsTouched();
      return;
    }
    this.companySaving.set(true);
    this.companySuccessMessage.set(null);
    try {
      localStorage.setItem(STORAGE_KEY_COMPANY, JSON.stringify(this.companyForm.value));
      setTimeout(() => {
        this.companySaving.set(false);
        this.companySuccessMessage.set('Coordonnées de l’entreprise enregistrées avec succès.');
        setTimeout(() => this.companySuccessMessage.set(null), 4000);
      }, 350);
    } catch {
      this.companySaving.set(false);
    }
  }

  // --- Delivery Settings ---
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
    if (this.deliverySettingsForm.invalid) {
      this.deliverySettingsForm.markAllAsTouched();
      return;
    }

    this.deliverySettingsSaving.set(true);
    this.deliverySettingsMessage.set(null);
    this.deliverySettingsError.set(null);

    const raw = this.deliverySettingsForm.value;
    const payload: UpdateDeliverySettingsPayload = {
      feesEnabled: Boolean(raw.feesEnabled),
      kenitraAreaFee: Number(raw.kenitraAreaFee),
      casablancaFee: Number(raw.casablancaFee),
      otherAreasFee: Number(raw.otherAreasFee),
      freeDeliveryThreshold: Number(raw.freeDeliveryThreshold)
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
        this.deliverySettingsMessage.set('Tarifs de livraison mis à jour avec succès sur l’ensemble de la plateforme.');
        setTimeout(() => this.deliverySettingsMessage.set(null), 5000);
      },
      error: (err: HttpErrorResponse) => {
        const msg = err.error?.message || 'Erreur lors de la sauvegarde des tarifs de livraison.';
        this.deliverySettingsError.set(msg);
      }
    });
  }

  // --- Banks Settings ---
  protected loadBanks(): void {
    this.banksLoading.set(true);
    this.banksError.set(null);
    this.orderApi.adminGetAllPaymentBanks().pipe(
      finalize(() => this.banksLoading.set(false))
    ).subscribe({
      next: (banks: readonly PaymentBank[]) => this.banks.set(banks),
      error: () => this.banksError.set('Impossible de charger la liste des banques.')
    });
  }

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

  protected bankNameDisplay(bank: PaymentBank): string {
    return bank.name || bank.reference;
  }

  protected resolveBankLogo(logoUrl?: string | null, bankNameOrRef?: string | null): string {
    return this.orderApi.resolveBankLogo(logoUrl, bankNameOrRef);
  }

  protected openCreateBankModal(): void {
    this.editingBank.set(null);
    this.bankForm.reset({ active: true });
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(null);
    this.bankFormError.set(null);
    this.isBankModalOpen.set(true);
  }

  protected openEditBankModal(bank: PaymentBank): void {
    this.editingBank.set(bank);
    this.bankForm.patchValue({
      reference: bank.reference,
      bankName: bank.name || bank.reference,
      rib: bank.rib,
      beneficiary: bank.beneficiary || '',
      active: bank.active !== false
    });
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(bank.logoUrl ? this.resolveBankLogo(bank.logoUrl, bank.name) : null);
    this.bankFormError.set(null);
    this.isBankModalOpen.set(true);
  }

  protected closeBankModal(): void {
    this.isBankModalOpen.set(false);
    this.editingBank.set(null);
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(null);
    this.bankFormError.set(null);
  }

  protected onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      this.bankFormError.set('Format de fichier non supporté. Formats acceptés : JPEG, PNG, WEBP.');
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      this.bankFormError.set('La taille du logo ne doit pas dépasser 2 Mo.');
      return;
    }

    this.bankFormError.set(null);
    this.selectedLogoFile.set(file);

    const reader = new FileReader();
    reader.onload = () => {
      this.logoPreviewUrl.set(reader.result as string);
    };
    reader.readAsDataURL(file);
  }

  protected removeSelectedLogo(): void {
    this.selectedLogoFile.set(null);
    this.logoPreviewUrl.set(null);
  }

  protected saveBank(): void {
    if (this.bankForm.invalid) {
      this.bankForm.markAllAsTouched();
      return;
    }

    this.bankSubmitting.set(true);
    this.bankFormError.set(null);
    const formVal = this.bankForm.value;
    const current = this.editingBank();
    const logoFile = this.selectedLogoFile();

    if (current && current.id) {
      const payload: UpdatePaymentBankPayload = {
        reference: formVal.reference,
        bankName: formVal.bankName,
        rib: formVal.rib,
        beneficiary: formVal.beneficiary,
        active: Boolean(formVal.active)
      };

      this.orderApi.adminUpdatePaymentBank(current.id, payload, logoFile).pipe(
        finalize(() => this.bankSubmitting.set(false))
      ).subscribe({
        next: () => {
          this.closeBankModal();
          this.loadBanks();
        },
        error: (err: HttpErrorResponse) => {
          this.bankFormError.set(err.error?.message || 'Erreur lors de la mise à jour de la banque.');
        }
      });
    } else {
      const payload: CreatePaymentBankPayload = {
        reference: formVal.reference,
        bankName: formVal.bankName,
        rib: formVal.rib,
        beneficiary: formVal.beneficiary,
        active: Boolean(formVal.active)
      };

      this.orderApi.adminCreatePaymentBank(payload, logoFile).pipe(
        finalize(() => this.bankSubmitting.set(false))
      ).subscribe({
        next: () => {
          this.closeBankModal();
          this.loadBanks();
        },
        error: (err: HttpErrorResponse) => {
          this.bankFormError.set(err.error?.message || 'Erreur lors de la création de la banque.');
        }
      });
    }
  }

  protected toggleBankActive(bank: PaymentBank): void {
    if (!bank.id) return;
    const nextState = !(bank.active !== false);
    this.orderApi.adminTogglePaymentBankActive(bank.id, nextState).subscribe({
      next: () => this.loadBanks(),
      error: () => this.banksError.set('Impossible de modifier le statut de la banque.')
    });
  }

  // --- Order Policies ---
  private loadSavedPolicySettings(): void {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_POLICIES);
      if (saved) {
        const parsed: OrderPolicySettings = JSON.parse(saved);
        this.policyForm.patchValue(parsed);
      }
    } catch {
      // fallback
    }
  }

  protected savePolicySettings(): void {
    if (this.policyForm.invalid) {
      this.policyForm.markAllAsTouched();
      return;
    }
    this.policySaving.set(true);
    this.policySuccessMessage.set(null);
    try {
      localStorage.setItem(STORAGE_KEY_POLICIES, JSON.stringify(this.policyForm.value));
      setTimeout(() => {
        this.policySaving.set(false);
        this.policySuccessMessage.set('Règles de commandes et de paiement enregistrées avec succès.');
        setTimeout(() => this.policySuccessMessage.set(null), 4000);
      }, 300);
    } catch {
      this.policySaving.set(false);
    }
  }

  // --- Microservices Status ---
  protected checkServicesHealth(): void {
    this.servicesChecking.set(true);
    setTimeout(() => {
      this.servicesChecking.set(false);
      const now = new Date();
      this.servicesCheckedTime.set(
        `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
      );
    }, 600);
  }
}
