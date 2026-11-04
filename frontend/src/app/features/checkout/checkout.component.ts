import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, Observable, of, Subscription, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { KEYCLOAK } from '../../core/auth/keycloak.token';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { AddressApiService } from '../addresses/address-api.service';
import { Address } from '../addresses/address.models';
import { CartApiService } from '../cart/cart-api.service';
import { Cart } from '../cart/cart.models';
import { OrderApiService } from '../orders/order-api.service';
import {
  CheckoutPaymentMethod,
  CreateGuestOrderRequest,
  DeliveryMethod,
  DeliverySettings,
  OrderPricingResponse,
  OrderResponse,
  PaymentBank,
  deliveryAreaLabel,
  deliveryFeeWaivedLabel,
  orderStatusLabel,
  paymentPlanLabel,
  paymentStatusLabel
} from '../orders/order.models';
import { QuotationApiService } from '../quotations/quotation-api.service';
import { Quotation } from '../quotations/quotation.models';

interface CheckoutMethod {
  readonly code: string;
  readonly title: string;
  readonly description: string;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'wwt-checkout',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss'
})
export class CheckoutComponent implements OnInit, OnDestroy {
  private readonly cartApi = inject(CartApiService);
  private readonly addressApi = inject(AddressApiService);
  private readonly orderApi = inject(OrderApiService);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly runtimeConfig = inject(RUNTIME_CONFIG, { optional: true });
  private readonly hasKeycloak = inject(KEYCLOAK, { optional: true }) !== null;
  private readonly auth = this.hasKeycloak ? inject(AuthService, { optional: true }) : null;

  protected readonly mobileSummaryOpen = signal<boolean>(false);
  protected readonly copiedRib = signal<string | null>(null);
  protected readonly guestMode = signal<boolean>(this.auth ? !this.auth.authenticated() : false);
  protected readonly cart = signal<Cart | null>(null);
  protected readonly quotation = signal<Quotation | null>(null);
  protected readonly quotationId = this.route.snapshot.queryParamMap.get('quotationId');
  protected readonly addresses = signal<readonly Address[]>([]);
  protected readonly selectedAddressId = signal<string | null>(null);
  protected readonly selectedDeliveryMethod = signal<DeliveryMethod | null>('DELIVERY');
  protected readonly selectedPaymentMethod = signal<CheckoutPaymentMethod | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submissionError = signal<string | null>(null);
  protected readonly proofUploadError = signal<string | null>(null);
  protected readonly createdOrder = signal<OrderResponse | null>(null);
  protected readonly pricing = signal<OrderPricingResponse | null>(null);
  protected readonly pricingLoading = signal(false);
  protected readonly pricingError = signal<string | null>(null);
  private pricingSubscription: Subscription | null = null;
  protected readonly paymentBanks = signal<readonly PaymentBank[]>([]);
  protected readonly selectedBankReference = signal<string | null>(null);
  protected readonly transferProof = signal<File | null>(null);
  protected readonly transferProofPreview = signal<string | null>(null);
  protected readonly trackingLinkCopied = signal(false);
  private readonly idempotencyKey = signal<string>(this.generateIdempotencyKey());

  protected toggleMobileSummary(): void {
    this.mobileSummaryOpen.update((v) => !v);
  }

  protected copyRib(rib: string): void {
    if (!rib || typeof navigator === 'undefined' || !navigator.clipboard) return;
    navigator.clipboard.writeText(rib).then(() => {
      this.copiedRib.set(rib);
      setTimeout(() => this.copiedRib.set(null), 2500);
    }).catch(() => undefined);
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

  protected formatRib(rib: string): string {
    if (!rib) return '';
    const clean = rib.replace(/\s+/g, '');
    if (clean.length === 24) {
      return `${clean.slice(0, 3)} ${clean.slice(3, 6)} ${clean.slice(6, 22)} ${clean.slice(22, 24)}`;
    }
    return rib;
  }

  // Guest checkout contact & delivery address signals
  protected readonly guestFullName = signal('');
  protected readonly guestEmail = signal('');
  protected readonly guestPhone = signal('');
  protected readonly guestCompany = signal('');
  protected readonly guestCity = signal('');
  protected readonly guestAddressLine1 = signal('');
  protected readonly guestAddressLine2 = signal('');
  protected readonly guestPostalCode = signal('');

  protected readonly popularCities: readonly string[] = [
    'Kénitra',
    'Mehdia',
    'Sidi Taibi',
    'Sidi Yahya El Gharb',
    'Souk El Arbaa',
    'Casablanca',
    'Rabat',
    'Salé',
    'Témara',
    'Tanger',
    'Marrakech',
    'Fès',
    'Meknès',
    'Agadir',
    'Oujda'
  ];

  protected readonly deliverySettings = signal<DeliverySettings | null>(null);

  protected readonly deliveryMethods = computed<readonly CheckoutMethod[]>(() => {
    const settings = this.deliverySettings();
    let deliveryDescription: string;
    if (settings) {
      if (!settings.feesEnabled) {
        deliveryDescription = 'Recevez votre commande à l’adresse sélectionnée (Frais de livraison offerts sur toutes les destinations).';
      } else {
        const kenitra = `${settings.kenitraAreaFee} DH`;
        const casa = `${settings.casablancaFee} DH`;
        const other = `${settings.otherAreasFee} DH`;
        const free = settings.freeDeliveryThreshold > 0
          ? `, gratuit > ${settings.freeDeliveryThreshold.toLocaleString('fr-FR')} DH`
          : '';
        deliveryDescription = `Recevez votre commande à l’adresse sélectionnée (Kenitra & environs : ${kenitra}, Casablanca : ${casa}, autres villes : ${other}${free}).`;
      }
    } else {
      deliveryDescription = 'Recevez votre commande à l’adresse sélectionnée.';
    }

    return [
      {
        code: 'DELIVERY',
        title: 'Livraison',
        description: deliveryDescription
      },
      {
        code: 'PICKUP',
        title: 'Retrait sur place',
        description: 'Retirez votre commande directement dans notre magasin, sans frais de livraison (0 DH).'
      }
    ];
  });
  protected readonly visiblePaymentMethods = computed<readonly CheckoutMethod[]>(() => {
    if (this.selectedDeliveryMethod() === 'PICKUP') {
      return [
        {
          code: 'BANK_TRANSFER',
          title: 'Virement bancaire intégral (100 %)',
          description: 'Réglez 100 % du montant total par virement bancaire.'
        },
        {
          code: 'SPLIT_50_50',
          title: 'Acompte de 50 % par virement bancaire',
          description: 'Réglez 50 % à la commande par virement bancaire et les 50 % restants lors du retrait en magasin.'
        },
        {
          code: 'PAY_ON_PICKUP',
          title: 'Paiement sur place',
          description: 'Réglez votre commande directement en magasin au moment du retrait.'
        }
      ];
    }
    return [
      {
        code: 'BANK_TRANSFER',
        title: 'Virement bancaire intégral (100 %)',
        description: 'Réglez 100 % du montant total par virement bancaire.'
      },
      {
        code: 'SPLIT_50_50',
        title: '50 % à la commande (virement) + 50 % à la livraison',
        description: 'Réglez 50 % d’acompte par virement bancaire à la commande et le solde de 50 % à la livraison.'
      }
    ];
  });
  protected readonly selectedAddress = computed(() =>
    this.addresses().find((address) => address.id === this.selectedAddressId()) ?? null
  );
  protected readonly requiresBankSelection = computed(() =>
    this.selectedPaymentMethod() === 'BANK_TRANSFER' || this.selectedPaymentMethod() === 'SPLIT_50_50'
  );
  protected readonly isGuestContactValid = computed(() => {
    const fullName = this.guestFullName().trim();
    const email = this.guestEmail().trim();
    const phoneDigits = this.guestPhone().replace(/[^0-9]/g, '');
    if (fullName.length < 2 || !EMAIL_REGEX.test(email) || phoneDigits.length < 8 || phoneDigits.length > 15) {
      return false;
    }
    if (this.selectedDeliveryMethod() === 'DELIVERY') {
      return this.guestCity().trim().length >= 2 && this.guestAddressLine1().trim().length >= 3;
    }
    return true;
  });
  protected readonly canConfirm = computed(() => {
    const hasItems = this.quotationId
      ? (this.quotation()?.status === 'ACCEPTED' && !!this.quotation()?.lines?.length)
      : (this.cart()?.items.length ?? 0) > 0 && !this.hasStockConflict();
    const deliveryReady = this.guestMode()
      ? this.isGuestContactValid()
      : (this.selectedDeliveryMethod() === 'PICKUP' || this.selectedAddress() !== null);
    return hasItems
      && deliveryReady
      && this.selectedDeliveryMethod() !== null
      && this.selectedPaymentMethod() !== null
      && (!this.requiresBankSelection() || this.selectedBankReference() !== null)
      && this.pricing() !== null
      && !this.pricingLoading()
      && !this.submitting();
  });

  ngOnInit(): void { this.load(); }

  ngOnDestroy(): void { this.pricingSubscription?.unsubscribe(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orderApi.getPaymentBanks().subscribe({
      next: (banks) => this.paymentBanks.set(banks),
      error: () => this.paymentBanks.set([])
    });
    this.orderApi.getDeliverySettings().subscribe({
      next: (settings) => this.deliverySettings.set(settings),
      error: () => this.deliverySettings.set(null)
    });

    if (this.guestMode()) {
      if (this.quotationId) {
        this.loading.set(false);
        this.error.set('Veuillez vous connecter à votre compte client pour transformer un devis en commande.');
        return;
      }
      this.cartApi.getCart().pipe(
        finalize(() => this.loading.set(false))
      ).subscribe({
        next: (cart) => {
          this.cart.set(cart);
          const stockConflict = cart.items.find((item) =>
            item.availableQuantity != null && item.quantity > item.availableQuantity
          );
          if (stockConflict) {
            this.error.set(this.stockConflictMessage(stockConflict.availableQuantity));
          }
          this.refreshPricing();
        },
        error: (error: unknown) => this.error.set(this.preparationErrorMessage(error))
      });
      return;
    }

    const source: Observable<
      { quotation: Quotation; addresses: readonly Address[] } |
      { cart: Cart; addresses: readonly Address[] }
    > = this.quotationId
      ? forkJoin({ quotation: this.quotationApi.getQuotation(this.quotationId), addresses: this.addressApi.getAddresses() })
      : forkJoin({ cart: this.cartApi.getCart(), addresses: this.addressApi.getAddresses() });
    source.pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (result) => {
        if ('quotation' in result) {
          if (result.quotation.status !== 'ACCEPTED' || !result.quotation.lines?.length) {
            this.error.set('Ce devis ne peut pas être transformé en commande.');
            return;
          }
          this.quotation.set(result.quotation);
        } else {
          this.cart.set(result.cart);
          const stockConflict = result.cart.items.find((item) =>
            item.availableQuantity != null && item.quantity > item.availableQuantity
          );
          if (stockConflict) this.error.set(this.stockConflictMessage(stockConflict.availableQuantity));
        }
        this.addresses.set(result.addresses);
        const currentSelection = result.addresses.find((address) => address.id === this.selectedAddressId());
        const defaultAddress = result.addresses.find((address) => address.defaultAddress);
        this.selectedAddressId.set(currentSelection?.id ?? defaultAddress?.id ?? null);
        this.refreshPricing();
      },
      error: (error: unknown) => this.error.set(this.preparationErrorMessage(error))
    });
  }

  protected selectAddress(addressId: string): void {
    if (this.selectedAddressId() === addressId) return;
    this.selectedAddressId.set(addressId);
    this.refreshPricing();
  }

  protected updateGuestCity(city: string): void {
    this.guestCity.set(city);
    this.refreshPricing();
  }

  protected selectDeliveryMethod(method: string): void {
    if ((method === 'DELIVERY' || method === 'PICKUP') && this.selectedDeliveryMethod() !== method) {
      this.selectedDeliveryMethod.set(method);
      if (method === 'DELIVERY' && this.selectedPaymentMethod() === 'PAY_ON_PICKUP') {
        this.selectedPaymentMethod.set(null);
        this.selectedBankReference.set(null);
      }
      this.refreshPricing();
    }
  }

  protected refreshPricing(): void {
    this.pricingSubscription?.unsubscribe();
    this.pricing.set(null);
    this.pricingError.set(null);
    const deliveryMethod = this.selectedDeliveryMethod();
    if (!deliveryMethod) {
      this.pricingLoading.set(false);
      return;
    }

    if (this.guestMode()) {
      const cartItems = this.cart()?.items ?? [];
      const city = this.guestCity().trim();
      if (!cartItems.length || (deliveryMethod === 'DELIVERY' && city.length < 2)) {
        this.pricingLoading.set(false);
        return;
      }
      this.pricingLoading.set(true);
      this.pricingSubscription = this.orderApi.previewGuestOrderPricing({
        items: cartItems.map((item) => ({ productReference: item.productReference, quantity: item.quantity })),
        deliveryMethod,
        ...(deliveryMethod === 'DELIVERY' ? { city } : {})
      }).pipe(finalize(() => this.pricingLoading.set(false))).subscribe({
        next: (pricing) => this.pricing.set(pricing),
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 409) {
            this.pricingError.set(this.stockConflictMessage(this.availableQuantityFrom(err)));
          } else {
            this.pricingError.set('Impossible de calculer le montant actuel. Vérifiez la ville saisie.');
          }
        }
      });
      return;
    }

    const addressId = this.selectedAddressId();
    if ((this.quotationId ? !this.quotation()?.lines?.length : !this.cart()?.items.length)
        || (deliveryMethod === 'DELIVERY' && !addressId)) {
      this.pricingLoading.set(false);
      return;
    }
    this.pricingLoading.set(true);
    this.pricingSubscription = this.orderApi.previewOrderPricing({
      deliveryMethod,
      ...(this.quotationId ? { quotationId: this.quotationId } : {}),
      ...(deliveryMethod === 'DELIVERY' ? { addressId } : {})
    }).pipe(finalize(() => this.pricingLoading.set(false))).subscribe({
      next: (pricing) => this.pricing.set(pricing),
      error: () => this.pricingError.set('Impossible de calculer le montant actuel. Réessayez avant de confirmer.')
    });
  }

  protected selectPaymentMethod(method: string): void {
    if (
      method === 'BANK_TRANSFER' ||
      method === 'SPLIT_50_50' ||
      (method === 'PAY_ON_PICKUP' && this.selectedDeliveryMethod() === 'PICKUP')
    ) {
      this.selectedPaymentMethod.set(method as CheckoutPaymentMethod);
      if (method === 'PAY_ON_PICKUP') {
        this.selectedBankReference.set(null);
        this.transferProof.set(null);
        this.transferProofPreview.set(null);
      }
    }
  }

  protected orderStatusLabel = orderStatusLabel;
  protected paymentPlanLabel = paymentPlanLabel;
  protected paymentStatusLabel = paymentStatusLabel;
  protected deliveryAreaLabel = deliveryAreaLabel;
  protected deliveryFeeWaivedLabel = deliveryFeeWaivedLabel;

  protected selectBank(reference: string): void { this.selectedBankReference.set(reference); }

  protected onTransferProofSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      this.submissionError.set('La preuve doit être une image JPG, JPEG ou PNG.');
      input.value = '';
      return;
    }
    this.submissionError.set(null);
    this.transferProofPreview.set(URL.createObjectURL(file));
    this.transferProof.set(file);
  }

  protected isDelivery(): boolean { return this.selectedDeliveryMethod() === 'DELIVERY'; }

  protected submitOrder(): void {
    const deliveryMethod = this.selectedDeliveryMethod();
    const paymentMethod = this.selectedPaymentMethod();
    if (!this.canConfirm() || !deliveryMethod || !paymentMethod) return;
    const bankReference = this.selectedBankReference();
    const needsBank = paymentMethod === 'BANK_TRANSFER' || paymentMethod === 'SPLIT_50_50';
    if (needsBank && !bankReference) {
      this.submissionError.set('Sélectionnez une banque pour le virement.');
      return;
    }

    if (this.guestMode()) {
      this.submitGuestOrder(deliveryMethod, paymentMethod, bankReference);
      return;
    }

    const addressId = this.selectedAddressId();
    if (deliveryMethod === 'DELIVERY' && !addressId) return;

    this.submitting.set(true);
    this.submissionError.set(null);
    this.proofUploadError.set(null);
    const request = deliveryMethod === 'DELIVERY'
      ? { addressId, deliveryMethod, paymentMethod, bankReference, ...(this.quotationId ? { quotationId: this.quotationId } : {}) }
      : { deliveryMethod, paymentMethod, bankReference, ...(this.quotationId ? { quotationId: this.quotationId } : {}) };
    this.orderApi.createOrder(request).pipe(
      switchMap((order) => {
        this.createdOrder.set(order);
        const proof = this.transferProof();
        return proof && needsBank
          ? this.orderApi.uploadTransferProof(order.id, proof).pipe(
              catchError((error: unknown) => {
                this.proofUploadError.set(this.proofErrorMessage(error));
                return of(order);
              })
            )
          : of(order);
      }),
      finalize(() => this.submitting.set(false))
    ).subscribe({
      next: (order) => {
        this.createdOrder.set(order);
      },
      error: (error: unknown) => this.submissionError.set(this.orderErrorMessage(error))
    });
  }

  private submitGuestOrder(
    deliveryMethod: DeliveryMethod,
    paymentMethod: CheckoutPaymentMethod,
    bankReference: string | null
  ): void {
    const cartItems = this.cart()?.items ?? [];
    if (!cartItems.length) return;
    this.submitting.set(true);
    this.submissionError.set(null);
    this.proofUploadError.set(null);

    const key = this.idempotencyKey();
    const guestRequest: CreateGuestOrderRequest = {
      fullName: this.guestFullName().trim(),
      email: this.guestEmail().trim(),
      phone: this.guestPhone().trim(),
      company: this.guestCompany().trim() || null,
      addressLine1: deliveryMethod === 'DELIVERY' ? this.guestAddressLine1().trim() : null,
      addressLine2: deliveryMethod === 'DELIVERY' ? (this.guestAddressLine2().trim() || null) : null,
      city: deliveryMethod === 'DELIVERY' ? this.guestCity().trim() : null,
      postalCode: deliveryMethod === 'DELIVERY' ? (this.guestPostalCode().trim() || null) : null,
      country: 'Maroc',
      deliveryMethod,
      paymentMethod,
      bankReference,
      items: cartItems.map((item) => ({
        productReference: item.productReference,
        quantity: item.quantity
      })),
      idempotencyKey: key
    };

    this.orderApi.createGuestOrder(guestRequest, key).pipe(
      switchMap((order) => {
        this.createdOrder.set(order);
        this.cartApi.clearCart().subscribe({ error: () => undefined });
        const proof = this.transferProof();
        if (proof && order.trackingToken) {
          return this.orderApi.uploadGuestTransferProof(order.trackingToken, proof).pipe(
            map(() => ({ ...order, transferProofReference: 'submitted' })),
            catchError((error: unknown) => {
              this.proofUploadError.set(this.proofErrorMessage(error));
              return of(order);
            })
          );
        }
        return of(order);
      }),
      finalize(() => this.submitting.set(false))
    ).subscribe({
      next: (order) => {
        this.createdOrder.set(order);
      },
      error: (error: unknown) => this.submissionError.set(this.orderErrorMessage(error))
    });
  }

  protected retryTransferProof(): void {
    const order = this.createdOrder();
    const proof = this.transferProof();
    const needsBank = order?.paymentMethod === 'BANK_TRANSFER' || order?.paymentMethod === 'SPLIT_50_50';
    if (!order || !proof || !needsBank || this.submitting()) return;
    this.submitting.set(true);
    this.proofUploadError.set(null);
    if (order.guestOrder && order.trackingToken) {
      this.orderApi.uploadGuestTransferProof(order.trackingToken, proof).pipe(
        finalize(() => this.submitting.set(false))
      ).subscribe({
        next: () => this.createdOrder.set({ ...order, transferProofReference: 'submitted' }),
        error: (error: unknown) => this.proofUploadError.set(this.proofErrorMessage(error))
      });
      return;
    }
    this.orderApi.uploadTransferProof(order.id, proof).pipe(
      finalize(() => this.submitting.set(false))
    ).subscribe({
      next: (updated) => this.createdOrder.set(updated),
      error: (error: unknown) => this.proofUploadError.set(this.proofErrorMessage(error))
    });
  }

  protected copyTrackingLink(url: string | null | undefined): void {
    const effectiveUrl = this.resolveTrackingUrl(url);
    if (!effectiveUrl || typeof navigator === 'undefined' || !navigator.clipboard) return;
    navigator.clipboard.writeText(effectiveUrl).then(() => {
      this.trackingLinkCopied.set(true);
      setTimeout(() => this.trackingLinkCopied.set(false), 3000);
    }).catch(() => undefined);
  }

  protected resolveTrackingUrl(url: string | null | undefined): string {
    const order = this.createdOrder();
    const token = order?.trackingToken;
    if (!token && !url) return '';

    // If backend provided an URL that does not use the legacy 4200 port, use it
    if (url && !url.includes('localhost:4200')) {
      return url;
    }

    const configuredBase = this.runtimeConfig?.trackingBaseUrl?.trim();
    const origin = typeof window !== 'undefined' && window.location?.origin
      ? window.location.origin
      : 'http://localhost:4201';
    const base = (configuredBase && !configuredBase.includes('localhost:4200'))
      ? configuredBase.replace(/\/+$/, '')
      : origin;

    return `${base}/suivi-commande?token=${token || ''}`;
  }

  private generateIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return `checkout-${crypto.randomUUID()}`;
    }
    return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }

  private proofErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Votre session a expiré. Reconnectez-vous pour envoyer la preuve de virement.';
      if (error.status === 403) return 'Vous n’êtes pas autorisé à envoyer une preuve pour cette commande.';
    }
    return 'La commande a été créée, mais la preuve de virement n’a pas pu être envoyée.';
  }

  private hasStockConflict(): boolean {
    return this.cart()?.items.some((item) =>
      item.availableQuantity != null && item.quantity > item.availableQuantity
    ) ?? false;
  }

  private preparationErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Impossible de préparer le checkout pour le moment.';
    switch (error.status) {
      case 400: return 'Votre panier est vide ou contient une référence invalide.';
      case 401: return 'Votre session a expiré. Reconnectez-vous pour continuer.';
      case 403: return 'Vous n’êtes pas autorisé à accéder au checkout.';
      case 404: return 'Une référence du panier ou une adresse est introuvable.';
      case 409: return this.isStockConflict(error)
        ? this.stockConflictMessage(this.availableQuantityFrom(error))
        : 'Le checkout a changé entre-temps. Vérifiez votre panier puis réessayez.';
      case 502:
      case 503: return 'Un service nécessaire au checkout est momentanément indisponible.';
      default: return 'Impossible de préparer le checkout pour le moment.';
    }
  }

  private stockConflictMessage(availableQuantity: number | null | undefined): string {
    return availableQuantity == null
      ? 'La quantité demandée pour ce produit n’est plus disponible.'
      : `La quantité demandée pour ce produit n’est plus disponible. Stock disponible : ${availableQuantity}.`;
  }

  private isStockConflict(error: HttpErrorResponse): boolean {
    return error.status === 409 && this.availableQuantityFrom(error) !== null;
  }

  private availableQuantityFrom(error: HttpErrorResponse): number | null {
    if (!this.isRecord(error.error)) return null;
    const availableQuantity = error.error['availableQuantity'];
    return typeof availableQuantity === 'number' && Number.isFinite(availableQuantity)
      ? availableQuantity
      : null;
  }

  private isRecord(value: unknown): boolean {
    return typeof value === 'object' && value !== null;
  }

  private orderErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'La commande n’a pas pu être créée.';
    const backendDetail = this.problemDetail(error);
    switch (error.status) {
      case 400: return backendDetail ?? 'La demande de commande est invalide.';
      case 401: return 'Votre session a expiré. Reconnectez-vous pour continuer.';
      case 403: return 'Vous n’êtes pas autorisé à créer cette commande.';
      case 404: return this.quotationId
        ? 'Le devis ou l’adresse sélectionnée est introuvable ou n’est plus disponible.'
        : 'L’adresse ou la référence sélectionnée est introuvable ou n’est plus disponible.';
      case 409: return this.isStockConflict(error)
        ? this.stockConflictMessage(this.availableQuantityFrom(error))
        : this.quotationId ? 'Ce devis ne peut plus être transformé en commande.'
          : (backendDetail ?? 'La commande ne peut pas être créée dans son état actuel. Vérifiez votre panier puis réessayez.');
      case 429: return backendDetail ?? 'Trop de tentatives. Veuillez patienter une minute avant de réessayer.';
      case 502:
      case 503: return 'Le service de commande est momentanément indisponible.';
      default: return 'La commande n’a pas pu être créée.';
    }
  }

  private problemDetail(error: HttpErrorResponse): string | null {
    if (!this.isRecord(error.error)) return null;
    const errObj = error.error as Record<string, unknown>;
    const detail = errObj['detail'] ?? errObj['message'];
    return typeof detail === 'string' && detail.trim() ? detail : null;
  }
}
