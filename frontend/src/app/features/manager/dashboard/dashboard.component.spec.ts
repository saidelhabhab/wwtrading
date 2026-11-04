import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DashboardComponent } from './dashboard.component';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CommercialReportsApiService } from '../../commercial/reports/commercial-reports-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';
import { SupportApiService } from '../../support/support-api.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthService, useValue: { roles: signal(['MANAGER']) } },
        { provide: CommercialReportsApiService, useValue: {
          getOrders: () => of({ totalOrders: 0, totalValue: 0, paidRevenue: 0, recognizedRevenue: 0, averageOrderValue: 0, orderValueOrders: 0, byStatus: [], overTime: [], revenueEvolution: [] }),
          getQuotations: () => of({ totalQuotations: 0, byStatus: [], overTime: [] }),
          getMaintenance: () => of({ totalRequests: 0, byStatus: [], overTime: [] }),
          getClients: () => of({ totalClients: 0, activeClients: 0, newClients: 0 }),
          getReport: () => of({ period: { from: '', to: '' }, kpis: { prospects: 0, activeProspects: 0, wonProspects: 0, lostProspects: 0 }, prospectsByStatus: [], activityOverTime: [], recentProspects: [] })
        } },
        { provide: NotificationApiService, useValue: { getUnreadCount: () => of(0) } },
        { provide: SupportApiService, useValue: { getAgentConversations: () => of({ content: [], totalElements: 0 }) } }
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
