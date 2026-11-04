import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { KEYCLOAK } from '../../core/auth/keycloak.token';
import { AddressApiService } from '../addresses/address-api.service';
import { Address } from '../addresses/address.models';
import { CartApiService } from '../cart/cart-api.service';
import { OrderApiService } from '../orders/order-api.service';
import {
  CreateGuestOrderRequest,
  CreateOrderRequest,
  OrderResponse,
  PreviewGuestOrderPricingRequest,
  PreviewOrderPricingRequest
} from '../orders/order.models';
import { CheckoutComponent } from './checkout.component';

describe('Checkout pricing summary', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  const addresses: readonly Address[] = [
    {
      id: 'address-kenitra', firstName: 'Jane', lastName: 'Doe', company: null, phone: null,
      addressLine1: 'Rue 1', addressLine2: null, city: 'Kenitra', postalCode: null,
      country: 'Maroc', defaultAddress: true, createdAt: '', updatedAt: ''
    },
    {
      id: 'address-rabat', firstName: 'Jane', lastName: 'Doe', company: null, phone: null,
      addressLine1: 'Rue 2', addressLine2: null, city: 'Rabat', postalCode: null,
      country: 'Maroc', defaultAddress: false, createdAt: '', updatedAt: ''
    }
  ];
  let lastCreatedOrder: OrderResponse;
  const createOrder = jasmine.createSpy('createOrder').and.callFake((request: CreateOrderRequest) => {
    lastCreatedOrder = {
      id: 'order-1', customerReference: 'client@example.test',
      shippingAddress: {
        firstName: 'Jane', lastName: 'Doe', company: null, phone: null,
        addressLine1: 'Rue 1', addressLine2: null, city: 'Kenitra', postalCode: null, country: 'Maroc'
      },
      deliveryMethod: request.deliveryMethod, paymentMethod: request.paymentMethod,
      paymentStatus: 'UNPAID', items: [], subtotal: 1000,
      deliveryFee: request.deliveryMethod === 'PICKUP' ? 0 : 10,
      total: request.deliveryMethod === 'PICKUP' ? 1000 : 1010,
      selectedBankReference: request.bankReference ?? null, transferProofReference: null,
      status: 'DRAFT', createdAt: '', updatedAt: ''
    } satisfies OrderResponse;
    return of(lastCreatedOrder);
  });
  const uploadTransferProof = jasmine.createSpy('uploadTransferProof').and.callFake(() => of(lastCreatedOrder));
  const previewOrderPricing = jasmine.createSpy('previewOrderPricing').and.callFake(
    (request: PreviewOrderPricingRequest) => {
      const deliveryFee = request.deliveryMethod === 'PICKUP' ? 0
        : request.addressId === 'address-rabat' ? 15 : 10;
      const total = 1000 + deliveryFee;
      return of({
        subtotal: 1000,
        deliveryFee,
        total,
        upfrontPaymentAmount: total / 2,
        remainingDeliveryAmount: total / 2,
        deliveryArea: request.deliveryMethod === 'PICKUP' ? null : (request.addressId === 'address-rabat' ? 'OTHER' : 'KENITRA_AREA'),
        deliveryFeeWaivedReason: null,
        deliveryFeesEnabled: true
      });
    }
  );

  beforeEach(async () => {
    createOrder.calls.reset();
    uploadTransferProof.calls.reset();
    previewOrderPricing.calls.reset();
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartApiService, useValue: { getCart: () => of({
          id: 'cart-1', items: [{ id: 'item-1', productReference: 'REF-001', quantity: 2 }],
          createdAt: '', updatedAt: ''
        }) } },
        { provide: AddressApiService, useValue: { getAddresses: () => of(addresses) } },
        { provide: OrderApiService, useValue: {
          getPaymentBanks: () => of([{
            reference: 'bank-1', name: 'Banque', rib: 'RIB', beneficiary: 'WWTRADING', logoUrl: null
          }]),
          getDeliverySettings: () => of({
            feesEnabled: true,
            kenitraAreaFee: 100,
            casablancaFee: 300,
            otherAreasFee: 400,
            freeDeliveryThreshold: 20000
          }),
          previewOrderPricing, createOrder, uploadTransferProof
        } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
  });

  it('offers only BANK_TRANSFER and SPLIT_50_50 for DELIVERY and offers BANK_TRANSFER, SPLIT_50_50, and PAY_ON_PICKUP for PICKUP', () => {
    const host = fixture.nativeElement as HTMLElement;
    // Default is DELIVERY
    const deliveryPaymentRadios = Array.from(host.querySelectorAll<HTMLInputElement>('input[name="payment-method"]'));
    expect(deliveryPaymentRadios.map((radio) => radio.value)).toEqual(['BANK_TRANSFER', 'SPLIT_50_50']);
    expect(host.querySelector('input[name="payment-method"][value="CASH_ON_DELIVERY"]')).toBeNull();
    expect(host.querySelector('input[name="payment-method"][value="PAY_ON_PICKUP"]')).toBeNull();
    expect(host.textContent).not.toContain('Paiement à la livraison');
    expect(host.textContent).not.toContain('Paiement sur place');
    expect(host.textContent).toContain('Virement bancaire intégral (100 %)');
    expect(host.textContent).toContain('Réglez 100 % du montant total par virement bancaire.');
    expect(host.textContent).toContain('50 % à la commande (virement) + 50 % à la livraison');
    expect(host.textContent).toContain('Réglez 50 % d’acompte par virement bancaire à la commande et le solde de 50 % à la livraison.');

    // Switch to PICKUP
    const pickup = host.querySelector<HTMLInputElement>('input[name="delivery-method"][value="PICKUP"]')!;
    pickup.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const pickupPaymentRadios = Array.from(host.querySelectorAll<HTMLInputElement>('input[name="payment-method"]'));
    expect(pickupPaymentRadios.map((radio) => radio.value)).toEqual(['BANK_TRANSFER', 'SPLIT_50_50', 'PAY_ON_PICKUP']);
    expect(host.querySelector('input[name="payment-method"][value="CASH_ON_DELIVERY"]')).toBeNull();
    expect(host.textContent).not.toContain('Paiement à la livraison');
    expect(host.textContent).toContain('Virement bancaire intégral (100 %)');
    expect(host.textContent).toContain('Réglez 100 % du montant total par virement bancaire.');
    expect(host.textContent).toContain('Acompte de 50 % par virement bancaire');
    expect(host.textContent).toContain('Réglez 50 % à la commande par virement bancaire et les 50 % restants lors du retrait en magasin.');
    expect(host.textContent).toContain('Paiement sur place');
    expect(host.textContent).toContain('Réglez votre commande directement en magasin au moment du retrait.');
    expect(host.textContent).toContain('Retirez votre commande directement dans notre magasin, sans frais de livraison (0 DH).');
  });

  it('submits PICKUP with PAY_ON_PICKUP without requiring bank selection', () => {
    const host = fixture.nativeElement as HTMLElement;
    host.querySelector<HTMLInputElement>('input[name="delivery-method"][value="PICKUP"]')!
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const payOnPickupRadio = host.querySelector<HTMLInputElement>('input[name="payment-method"][value="PAY_ON_PICKUP"]')!;
    payOnPickupRadio.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    // Bank selection step is not displayed for PAY_ON_PICKUP
    expect(host.querySelector('input[name="payment-bank"]')).toBeNull();
    expect(host.textContent).not.toContain('Informations de virement bancaire');

    host.querySelector<HTMLButtonElement>('button.confirm')!.click();
    expect(createOrder).toHaveBeenCalledOnceWith({
      deliveryMethod: 'PICKUP',
      paymentMethod: 'PAY_ON_PICKUP',
      bankReference: null
    });
  });

  it('updates delivery and total when address or method changes without creating an order', () => {
    const host = fixture.nativeElement as HTMLElement;
    const bankTransfer = host.querySelector<HTMLInputElement>('input[name="payment-method"][value="BANK_TRANSFER"]')!;
    bankTransfer.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.querySelector('.pricing-breakdown')?.textContent).toContain('Sous-total');
    expect(host.querySelector('.pricing-breakdown')?.textContent).toContain('Livraison');
    expect(host.querySelector('.pricing-breakdown')?.textContent).toContain('Total');
    expect(previewOrderPricing).toHaveBeenCalledWith({
      deliveryMethod: 'DELIVERY', addressId: 'address-kenitra'
    });

    const rabat = host.querySelector<HTMLInputElement>('input[name="delivery-address"][value="address-rabat"]');
    expect(rabat).not.toBeNull();
    rabat!.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(previewOrderPricing).toHaveBeenCalledWith({
      deliveryMethod: 'DELIVERY', addressId: 'address-rabat'
    });
    expect(host.querySelector('.pricing-breakdown p:nth-child(2) strong')?.textContent).toContain('15');

    const pickup = host.querySelector<HTMLInputElement>('input[name="delivery-method"][value="PICKUP"]');
    expect(pickup).not.toBeNull();
    pickup!.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(previewOrderPricing).toHaveBeenCalledWith({ deliveryMethod: 'PICKUP' });
    expect(host.querySelector('.pricing-breakdown p:nth-child(2) strong')?.textContent).toContain('0');
    expect(bankTransfer.checked).toBeTrue();

    const delivery = host.querySelector<HTMLInputElement>('input[name="delivery-method"][value="DELIVERY"]');
    delivery!.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(bankTransfer.checked).toBeTrue();
    expect(previewOrderPricing).toHaveBeenCalledWith({
      deliveryMethod: 'DELIVERY', addressId: 'address-rabat'
    });
    expect(host.querySelector('.pricing-breakdown p:nth-child(2) strong')?.textContent).toContain('15');
    expect(createOrder).not.toHaveBeenCalled();
  });

  for (const deliveryMethod of ['DELIVERY', 'PICKUP'] as const) {
    it(`submits ${deliveryMethod} with BANK_TRANSFER using the existing contract`, () => {
      const host = fixture.nativeElement as HTMLElement;
      if (deliveryMethod === 'PICKUP') {
        host.querySelector<HTMLInputElement>('input[name="delivery-method"][value="PICKUP"]')!
          .dispatchEvent(new Event('change'));
        fixture.detectChanges();
      }
      const payment = host.querySelector<HTMLInputElement>('input[name="payment-method"][value="BANK_TRANSFER"]')!;
      expect(payment.closest('label')?.textContent).toContain('Virement bancaire intégral (100 %)');
      payment.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(host.textContent).toContain('Informations de virement bancaire');
      host.querySelector<HTMLInputElement>('input[name="payment-bank"][value="bank-1"]')!
        .dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(host.querySelector('.pricing-breakdown p:nth-child(2) strong')?.textContent)
        .toContain(deliveryMethod === 'PICKUP' ? '0.00' : '10.00');
      host.querySelector<HTMLButtonElement>('button.confirm')!.click();
      expect(createOrder).toHaveBeenCalledOnceWith({
        ...(deliveryMethod === 'DELIVERY' ? { addressId: 'address-kenitra' } : {}),
        deliveryMethod,
        paymentMethod: 'BANK_TRANSFER',
        bankReference: 'bank-1'
      });
      expect(uploadTransferProof).not.toHaveBeenCalled();
    });
  }

  it('uploads an optional bank transfer proof once without creating another order', () => {
    const host = fixture.nativeElement as HTMLElement;
    host.querySelector<HTMLInputElement>('input[name="payment-method"][value="BANK_TRANSFER"]')!
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();
    host.querySelector<HTMLInputElement>('input[name="payment-bank"][value="bank-1"]')!
      .dispatchEvent(new Event('change'));
    const file = new File(['proof'], 'proof.png', { type: 'image/png' });
    const files = new DataTransfer();
    files.items.add(file);
    const proofInput = host.querySelector<HTMLInputElement>('input[type="file"]')!;
    proofInput.files = files.files;
    proofInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('button.confirm')!.click();
    expect(createOrder).toHaveBeenCalledTimes(1);
    expect(uploadTransferProof).toHaveBeenCalledOnceWith('order-1', file);
  });

  it('supports SPLIT_50_50 on DELIVERY orders and displays 50% upfront and 50% remaining delivery amounts', () => {
    const host = fixture.nativeElement as HTMLElement;
    const splitRadio = host.querySelector<HTMLInputElement>('input[name="payment-method"][value="SPLIT_50_50"]');
    expect(splitRadio).not.toBeNull();
    splitRadio!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(host.querySelector('.split-breakdown')?.textContent).toContain('Acompte 50 % à la commande (virement)');
    expect(host.querySelector('.split-breakdown')?.textContent).toContain('505.00 DH');
    expect(host.querySelector('.split-breakdown')?.textContent).toContain('Solde 50 % à la livraison');

    host.querySelector<HTMLInputElement>('input[name="payment-bank"][value="bank-1"]')!
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('button.confirm')!.click();
    expect(createOrder).toHaveBeenCalledOnceWith({
      addressId: 'address-kenitra',
      deliveryMethod: 'DELIVERY',
      paymentMethod: 'SPLIT_50_50',
      bankReference: 'bank-1'
    });
  });
});

describe('Guest checkout flow', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  const clearCart = jasmine.createSpy('clearCart').and.returnValue(of(void 0));
  const previewGuestOrderPricing = jasmine.createSpy('previewGuestOrderPricing').and.callFake(
    (req: PreviewGuestOrderPricingRequest) => {
      const fee = req.deliveryMethod === 'PICKUP' ? 0 : 300;
      const total = 2000 + fee;
      return of({
        subtotal: 2000,
        deliveryFee: fee,
        total,
        upfrontPaymentAmount: total / 2,
        remainingDeliveryAmount: total / 2,
        deliveryArea: req.deliveryMethod === 'PICKUP' ? 'PICKUP' : 'KENITRA_AREA',
        deliveryFeesEnabled: true,
        deliveryFeeWaivedReason: null
      });
    }
  );
  const createGuestOrder = jasmine.createSpy('createGuestOrder').and.callFake(
    (req: CreateGuestOrderRequest) => of({
      id: 'guest-order-1',
      orderNumber: 'WWT-20261026-GUEST1',
      customerReference: req.email,
      guestOrder: true,
      contactFullName: req.fullName,
      contactEmail: req.email,
      contactPhone: req.phone,
      shippingAddress: null,
      deliveryMethod: req.deliveryMethod,
      paymentMethod: req.paymentMethod,
      paymentPlan: req.paymentMethod === 'SPLIT_50_50' ? 'SPLIT_50_50' : 'FULL_BANK_TRANSFER',
      paymentStatus: 'UNPAID',
      items: [],
      subtotal: 2000,
      deliveryFee: req.deliveryMethod === 'PICKUP' ? 0 : 300,
      total: req.deliveryMethod === 'PICKUP' ? 2000 : 2300,
      upfrontPaymentAmount: req.paymentMethod === 'SPLIT_50_50' ? 1150 : 2300,
      remainingDeliveryAmount: req.paymentMethod === 'SPLIT_50_50' ? 1150 : 0,
      selectedBankReference: req.bankReference ?? null,
      transferProofReference: null,
      status: 'DRAFT',
      createdAt: '',
      updatedAt: '',
      trackingToken: 'raw-token-123',
      trackingUrl: 'http://localhost:4201/suivi-commande?token=raw-token-123'
    } satisfies OrderResponse)
  );

  beforeEach(async () => {
    clearCart.calls.reset();
    previewGuestOrderPricing.calls.reset();
    createGuestOrder.calls.reset();
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: KEYCLOAK, useValue: { authenticated: false, tokenParsed: null } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
        {
          provide: CartApiService,
          useValue: {
            getCart: () => of({
              id: 'guest-cart',
              items: [{ id: 'item-1', productReference: 'REF-G1', quantity: 1 }],
              createdAt: '',
              updatedAt: ''
            }),
            clearCart
          }
        },
        { provide: AddressApiService, useValue: { getAddresses: () => of([]) } },
        {
          provide: OrderApiService,
          useValue: {
            getPaymentBanks: () => of([
              { reference: 'bank-1', name: 'CIH Bank', rib: 'MA64000111', beneficiary: 'WWTRADING', logoUrl: null }
            ]),
            getDeliverySettings: () => of({
              feesEnabled: true,
              kenitraAreaFee: 100,
              casablancaFee: 300,
              otherAreasFee: 400,
              freeDeliveryThreshold: 20000
            }),
            previewGuestOrderPricing,
            createGuestOrder
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
  });

  it('collects guest contact and delivery information and creates a guest order with tracking link', () => {
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Commande rapide sans création de compte');
    expect(host.querySelector('input[name="guest-full-name"]')).not.toBeNull();
    expect(host.querySelector('input[name="guest-email"]')).not.toBeNull();
    expect(host.querySelector('input[name="guest-phone"]')).not.toBeNull();

    const setInput = (selector: string, val: string) => {
      const el = host.querySelector<HTMLInputElement>(selector)!;
      el.value = val;
      el.dispatchEvent(new Event('input'));
    };
    setInput('input[name="guest-full-name"]', 'Karim Bennani');
    setInput('input[name="guest-email"]', 'karim@example.ma');
    setInput('input[name="guest-phone"]', '0661234567');
    setInput('input[name="guest-city"]', 'Kénitra');
    setInput('input[name="guest-address-1"]', '12 Avenue Mohammed V');
    fixture.detectChanges();

    host.querySelector<HTMLInputElement>('input[name="payment-method"][value="BANK_TRANSFER"]')!
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();
    host.querySelector<HTMLInputElement>('input[name="payment-bank"][value="bank-1"]')!
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('button.confirm')!.click();
    fixture.detectChanges();

    expect(createGuestOrder).toHaveBeenCalledTimes(1);
    expect(clearCart).toHaveBeenCalledTimes(1);
    expect(host.textContent).toContain('WWT-20261026-GUEST1');
    expect(host.textContent).toContain('Suivre ma commande en ligne');
  });
});
