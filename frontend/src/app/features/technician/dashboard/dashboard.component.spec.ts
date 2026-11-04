import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DashboardComponent } from './dashboard.component';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CommercialReportsApiService } from '../../commercial/reports/commercial-reports-api.service';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { NotificationApiService } from '../../notifications/notification-api.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthService, useValue: { roles: signal(['TECHNICIAN']) } },
        { provide: CommercialReportsApiService, useValue: { getMaintenance: () => of({ totalRequests: 0, byStatus: [], overTime: [] }) } },
        { provide: MaintenanceApiService, useValue: { getMyAssignments: () => of({ content: [], totalPages: 0 }) } },
        { provide: NotificationApiService, useValue: { getUnreadCount: () => of(0) } }
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
