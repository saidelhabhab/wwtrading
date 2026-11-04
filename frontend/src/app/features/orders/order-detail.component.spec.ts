import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ProductApiService } from '../products/product-api.service';
import { OrderApiService } from './order-api.service';
import { OrderDetailComponent } from './order-detail.component';
import { OrderResponse } from './order.models';

@Component({ selector: 'wwt-dashboard-layout', standalone: true, template: '<ng-content />' })
class TestDashboardLayoutComponent { @Input() title = ''; }

@Component({ selector: 'wwt-page-header', standalone: true, template: '' })
class TestPageHeaderComponent {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() breadcrumb = '';
}

describe('Order Detail persisted pricing', () => {
  let fixture: ComponentFixture<OrderDetailComponent>;
  let response: OrderResponse;
  const order: OrderResponse = {
    id: 'order-1', customerReference: 'client@example.test',
    shippingAddress: {
      firstName: 'Jane', lastName: 'Doe', company: null, phone: null,
      addressLine1: 'Rue 1', addressLine2: null, city: 'Kenitra', postalCode: null, country: 'Maroc'
    },
    deliveryMethod: 'DELIVERY', paymentMethod: 'CASH_ON_DELIVERY', paymentStatus: 'UNPAID',
    items: [], subtotal: 1000, deliveryFee: 10, total: 1010,
    selectedBankReference: null, transferProofReference: null, status: 'DRAFT',
    createdAt: '2026-09-16T10:00:00Z', updatedAt: '2026-09-16T10:00:00Z'
  };

  beforeEach(async () => {
    response = order;
    await TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { roles: signal(['CLIENT']) } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ orderId: order.id }) } } },
        { provide: OrderApiService, useValue: { getOrder: () => of(response), resolveBankLogo: () => '/assets/banks/cih.svg' } },
        { provide: ProductApiService, useValue: {} }
      ]
    })
      .overrideComponent(OrderDetailComponent, {
        remove: { imports: [DashboardLayoutComponent, PageHeaderComponent] },
        add: { imports: [TestDashboardLayoutComponent, TestPageHeaderComponent] }
      })
      .compileComponents();
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();
  });

  it('renders subtotal, delivery fee and total from the order API response', () => {
    const host = fixture.nativeElement as HTMLElement;
    const totals = host.querySelector('.totals');
    expect(totals?.textContent).toContain('Sous-total');
    expect(totals?.textContent).toContain('Frais de livraison');
    expect(totals?.textContent).toContain('Total final');
    expect(totals?.querySelector('p:first-child strong')?.textContent).toContain('1,000.00');
    expect(totals?.querySelector('p:nth-child(2) strong')?.textContent).toContain('10.00');
    expect(totals?.querySelector('p:last-child strong')?.textContent).toContain('1,010.00');
    expect(host.querySelector('.panel:last-child')?.textContent).toContain('Livraison');
    expect(host.querySelector('.panel:last-child')?.textContent).toContain('Paiement à la livraison');
  });

  it('labels pickup and cash payment without changing the persisted amounts', () => {
    response = { ...order, deliveryMethod: 'PICKUP', deliveryFee: 0, total: 1000 };
    fixture.destroy();
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.panel:last-child')?.textContent).toContain('Retrait sur place');
    expect(host.querySelector('.panel:last-child')?.textContent).toContain('Paiement sur place');
    expect(host.querySelector('.totals p:nth-child(2) strong')?.textContent).toContain('0.00');
  });

  it('displays split 50/50 payment amounts, paid amount, outstanding balance, and waiver reason', () => {
    response = {
      ...order,
      paymentMethod: 'SPLIT_50_50',
      paymentPlan: 'SPLIT_50_50',
      paymentStatus: 'PARTIALLY_PAID',
      subtotal: 21000,
      deliveryFee: 0,
      total: 21000,
      upfrontPaymentAmount: 10500,
      remainingDeliveryAmount: 10500,
      amountPaid: 10500,
      outstandingBalance: 10500,
      deliveryArea: 'KENITRA_AREA',
      deliveryFeeWaivedReason: 'FREE_THRESHOLD'
    };
    fixture.destroy();
    fixture = TestBed.createComponent(OrderDetailComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const rightPanel = host.querySelector('.panel:last-child')?.textContent ?? '';
    expect(rightPanel).toContain('50 % à la commande (virement) + 50 % à la livraison');
    expect(rightPanel).toContain('Partiellement payé (50 %)');
    expect(rightPanel).toContain('Livraison offerte');
    expect(rightPanel).toContain('10,500.00 DH');
  });
});
