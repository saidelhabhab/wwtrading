import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { OrderApiService } from '../../orders/order-api.service';
import { ProductApiService } from '../../products/product-api.service';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import { DashboardComponent } from './dashboard.component';
import { CommercialReportsApiService } from '../../commercial/reports/commercial-reports-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { SupportApiService } from '../../support/support-api.service';

const orderPage = {
  content: [], number: 0, size: 5, totalElements: 12, totalPages: 1, first: true, last: true
};
const quotationPage = {
  content: [], number: 0, size: 5, totalElements: 8, totalPages: 1, first: true, last: true, empty: true
};
const productPage = {
  content: [], number: 0, size: 5, totalElements: 25, totalPages: 1,
  numberOfElements: 0, first: true, last: true, empty: true
};

describe('Admin DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthService, useValue: { roles: signal(['ADMIN']) } },
        { provide: OrderApiService, useValue: { getAllOrders: () => of(orderPage), getOrderAnalytics: () => of({ totalOrders: 12, totalValue: 0, paidRevenue: 0, recognizedRevenue: 0, averageOrderValue: 0, orderValueOrders: 0, byStatus: [], overTime: [], revenueEvolution: [] }) } },
        {
          provide: QuotationApiService,
          useValue: { getAllQuotations: (_page: number, _size: number, status?: string) => of(status ? { ...quotationPage, totalElements: 1 } : quotationPage) }
        },
        { provide: ProductApiService, useValue: { list: () => of(productPage) } },
        { provide: CommercialReportsApiService, useValue: { getClients: () => of({ totalClients: 4, activeClients: 3, newClients: 1 }), getMaintenance: () => of({ totalRequests: 2, byStatus: [], overTime: [] }) } },
        { provide: NotificationApiService, useValue: { getUnreadCount: () => of(1) } },
        { provide: SupportApiService, useValue: { getAgentConversations: () => of({ content: [], totalElements: 2 }) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('shows totals returned by the existing APIs', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('12');
    expect(text).toContain('8');
    expect(text).toContain('25');
    expect(text).not.toContain('124 850 DH');
  });
});
