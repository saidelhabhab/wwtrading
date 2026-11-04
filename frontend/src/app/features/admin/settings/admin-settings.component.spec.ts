import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { OrderApiService } from '../../orders/order-api.service';
import { DeliverySettings, PaymentBank } from '../../orders/order.models';
import { AdminSettingsComponent } from './admin-settings.component';

describe('AdminSettingsComponent', () => {
  let component: AdminSettingsComponent;
  let fixture: ComponentFixture<AdminSettingsComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockDeliverySettings: DeliverySettings = {
    feesEnabled: true,
    kenitraAreaFee: 100,
    casablancaFee: 300,
    otherAreasFee: 400,
    freeDeliveryThreshold: 20000,
    currency: 'MAD',
    updatedAt: '2026-11-01T20:00:00Z'
  };

  const mockBanks: readonly PaymentBank[] = [
    {
      id: 'bank-1',
      reference: 'CIH_BANK',
      name: 'CIH Bank',
      rib: '230780000000000000000000',
      beneficiary: 'WWTRADING SARL',
      active: true,
      logoUrl: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z'
    }
  ];

  beforeEach(async () => {
    orderApiSpy = jasmine.createSpyObj('OrderApiService', [
      'adminGetDeliverySettings',
      'adminUpdateDeliverySettings',
      'adminGetAllPaymentBanks',
      'adminCreatePaymentBank',
      'adminUpdatePaymentBank',
      'adminTogglePaymentBankActive',
      'resolveBankLogo'
    ]);

    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      roles: () => ['ADMIN'],
      currentUser: () => ({ id: 'admin-1', username: 'admin' })
    });

    orderApiSpy.adminGetDeliverySettings.and.returnValue(of(mockDeliverySettings));
    orderApiSpy.adminGetAllPaymentBanks.and.returnValue(of(mockBanks));
    orderApiSpy.resolveBankLogo.and.returnValue('/assets/banks/cih.svg');

    await TestBed.configureTestingModule({
      imports: [AdminSettingsComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: orderApiSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load settings on init', () => {
    expect(component).toBeTruthy();
    expect(orderApiSpy.adminGetDeliverySettings).toHaveBeenCalled();
    expect(orderApiSpy.adminGetAllPaymentBanks).toHaveBeenCalled();
  });

  it('should switch tabs properly', () => {
    expect(component['activeTab']()).toBe('general');
    component['setTab']('delivery');
    expect(component['activeTab']()).toBe('delivery');
    component['setTab']('banks');
    expect(component['activeTab']()).toBe('banks');
  });

  it('should format RIB properly', () => {
    const formatted = component['formatRib']('230780000000000000000000');
    expect(formatted).toBe('230 780 0000000000000000 00');
  });
});
