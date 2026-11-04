import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { StaffUserApiService } from './staff-user-api.service';
import { StaffUserManagementComponent } from './staff-user-management.component';
import { StaffUser } from './staff-user.models';

describe('StaffUserManagementComponent - Password Reset Feature', () => {
  let component: StaffUserManagementComponent;
  let fixture: ComponentFixture<StaffUserManagementComponent>;
  let staffUserApiSpy: jasmine.SpyObj<StaffUserApiService>;
  let authServiceMock: { roles: jasmine.Spy };

  const mockAdminUser: StaffUser = {
    id: 'kc-uuid-1234',
    firstName: 'Amine',
    lastName: 'Bennani',
    email: 'amine.bennani@wwtrading.ma',
    phone: '+212612345678',
    role: 'ADMIN',
    enabled: true
  };

  const mockTechnicianUser: StaffUser = {
    id: 'kc-uuid-5678',
    firstName: 'Youssef',
    lastName: 'Alami',
    email: 'youssef.alami@wwtrading.ma',
    phone: '+212698765432',
    role: 'TECHNICIAN',
    enabled: true
  };

  beforeEach(async () => {
    staffUserApiSpy = jasmine.createSpyObj<StaffUserApiService>('StaffUserApiService', [
      'getStaffUsers',
      'createStaffUser',
      'resetUserPassword'
    ]);
    staffUserApiSpy.getStaffUsers.and.returnValue(of([mockAdminUser, mockTechnicianUser]));
    staffUserApiSpy.resetUserPassword.and.returnValue(
      of({ success: true, message: 'Le mot de passe a été réinitialisé avec succès.' })
    );

    authServiceMock = {
      roles: jasmine.createSpy('roles').and.returnValue(['ADMIN'])
    };

    await TestBed.configureTestingModule({
      imports: [StaffUserManagementComponent],
      providers: [
        { provide: StaffUserApiService, useValue: staffUserApiSpy },
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StaffUserManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create component and load staff users', () => {
    expect(component).toBeTruthy();
    expect(staffUserApiSpy.getStaffUsers).toHaveBeenCalled();
  });

  it('should open password reset modal with selected user', () => {
    expect(component['resetModalOpen']()).toBeFalse();
    expect(component['selectedUser']()).toBeNull();

    component['openResetModal'](mockTechnicianUser);

    expect(component['resetModalOpen']()).toBeTrue();
    expect(component['selectedUser']()).toEqual(mockTechnicianUser);
    expect(component['resetMode']()).toBe('DIRECT');
    expect(component['resetConfirmed']()).toBeFalse();
  });

  it('should close reset modal and clean up state', () => {
    component['openResetModal'](mockTechnicianUser);
    expect(component['resetModalOpen']()).toBeTrue();

    component['closeResetModal']();

    expect(component['resetModalOpen']()).toBeFalse();
    expect(component['selectedUser']()).toBeNull();
  });

  it('should switch between DIRECT and EMAIL modes', () => {
    component['openResetModal'](mockTechnicianUser);

    component['setResetMode']('EMAIL');
    expect(component['resetMode']()).toBe('EMAIL');

    component['setResetMode']('DIRECT');
    expect(component['resetMode']()).toBe('DIRECT');
  });

  it('should validate password criteria correctly in DIRECT mode', () => {
    component['openResetModal'](mockTechnicianUser);

    const newPwdControl = component['resetForm'].get('newPassword');
    newPwdControl?.setValue('weak');

    expect(component['pwdHasMinLength']()).toBeFalse();
    expect(component['pwdHasUpper']()).toBeFalse();
    expect(component['pwdHasLower']()).toBeTrue();
    expect(component['pwdHasNumber']()).toBeFalse();
    expect(component['pwdHasSpecial']()).toBeFalse();

    newPwdControl?.setValue('StrongPassword123!');
    expect(component['pwdHasMinLength']()).toBeTrue();
    expect(component['pwdHasUpper']()).toBeTrue();
    expect(component['pwdHasLower']()).toBeTrue();
    expect(component['pwdHasNumber']()).toBeTrue();
    expect(component['pwdHasSpecial']()).toBeTrue();
  });

  it('should detect password mismatch between new and confirm passwords', () => {
    component['openResetModal'](mockTechnicianUser);

    component['resetForm'].patchValue({
      newPassword: 'ValidPassword123!',
      confirmPassword: 'DifferentPassword123!'
    });

    expect(component['resetForm'].hasError('passwordMismatch')).toBeTrue();
    expect(component['isResetFormInvalid']()).toBeTrue();
  });

  it('should submit DIRECT password reset when form is valid and confirmed', () => {
    component['openResetModal'](mockTechnicianUser);

    component['resetForm'].patchValue({
      newPassword: 'ValidPassword123!',
      confirmPassword: 'ValidPassword123!'
    });
    component['toggleResetConfirmed']();

    expect(component['isResetFormInvalid']()).toBeFalse();

    component['submitResetPassword']();

    expect(staffUserApiSpy.resetUserPassword).toHaveBeenCalledWith('kc-uuid-5678', {
      mode: 'DIRECT',
      newPassword: 'ValidPassword123!',
      confirmPassword: 'ValidPassword123!'
    });
    expect(component['resetSuccess']()).toContain('réinitialisé avec succès');
  });

  it('should submit EMAIL password reset without requiring password fields', () => {
    component['openResetModal'](mockTechnicianUser);
    component['setResetMode']('EMAIL');
    component['toggleResetConfirmed']();

    expect(component['isResetFormInvalid']()).toBeFalse();

    component['submitResetPassword']();

    expect(staffUserApiSpy.resetUserPassword).toHaveBeenCalledWith('kc-uuid-5678', {
      mode: 'EMAIL'
    });
    expect(component['resetSuccess']()).toBeTruthy();
  });

  it('should handle API errors gracefully during reset', () => {
    staffUserApiSpy.resetUserPassword.and.returnValue(
      throwError(() => ({
        error: { message: 'Keycloak user not found' },
        status: 404
      }))
    );

    component['openResetModal'](mockTechnicianUser);
    component['setResetMode']('EMAIL');
    component['toggleResetConfirmed']();

    component['submitResetPassword']();

    expect(component['resetSubmitting']()).toBeFalse();
    expect(component['resetError']()).toContain('Keycloak user not found');
  });
});
