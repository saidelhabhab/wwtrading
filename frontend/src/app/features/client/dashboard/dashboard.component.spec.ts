import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DashboardComponent } from './dashboard.component';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { OrderApiService } from '../../orders/order-api.service';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { SupportApiService } from '../../support/support-api.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthService, useValue: { user: signal({ displayName: 'Client Test' }), roles: signal(['CLIENT']) } },
        { provide: OrderApiService, useValue: { getMyOrders: () => of({ content: [], totalPages: 0 }) } },
        { provide: QuotationApiService, useValue: { getMyQuotations: () => of({ content: [], totalPages: 0 }) } },
        { provide: MaintenanceApiService, useValue: { getMyRequests: () => of({ content: [], totalPages: 0 }) } },
        { provide: NotificationApiService, useValue: { getUnreadCount: () => of(0) } },
        { provide: SupportApiService, useValue: { getMyConversations: () => of({ content: [], totalElements: 0 }) } }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
