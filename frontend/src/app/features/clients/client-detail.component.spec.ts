import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { ClientApiService } from './client-api.service';
import { ClientDetailComponent } from './client-detail.component';
import { Client } from './client.models';

describe('ClientDetailComponent', () => {
  let component: ClientDetailComponent;
  let fixture: ComponentFixture<ClientDetailComponent>;
  let clientApiSpy: jasmine.SpyObj<ClientApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;

  const mockClient: Client = {
    id: 'client-uuid-1',
    firstName: 'Ahmed',
    lastName: 'Alami',
    email: 'ahmed@test.ma',
    phone: '+212612345678',
    enabled: true
  };

  beforeEach(async () => {
    clientApiSpy = jasmine.createSpyObj('ClientApiService', ['getClient']);
    clientApiSpy.getClient.and.returnValue(of(mockClient));

    authSpy = jasmine.createSpyObj('AuthService', ['roles']);
    authSpy.roles.and.returnValue(['ADMIN']);

    await TestBed.configureTestingModule({
      imports: [ClientDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ClientApiService, useValue: clientApiSpy },
        { provide: AuthService, useValue: authSpy },
        { provide: RUNTIME_CONFIG, useValue: { apiBaseUrl: 'http://localhost:8080' } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? 'client-uuid-1' : null)
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ClientDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load client details', () => {
    expect(component).toBeTruthy();
    expect(clientApiSpy.getClient).toHaveBeenCalledWith('client-uuid-1');
    expect(component.client()).toEqual(mockClient);
    expect(component.loading()).toBeFalse();
    expect(component.isNotFound()).toBeFalse();
  });

  it('computes correct backPath based on user role', () => {
    expect(component.backPath()).toBe('/admin/clients');

    authSpy.roles.and.returnValue(['MANAGER']);
    fixture.detectChanges();
    expect(component.backPath()).toBe('/manager/clients');

    authSpy.roles.and.returnValue(['COMMERCIAL']);
    fixture.detectChanges();
    expect(component.backPath()).toBe('/commercial/clients');
  });

  it('handles 404 error and sets isNotFound flag', () => {
    clientApiSpy.getClient.and.returnValue(throwError(() => ({ status: 404 })));
    component.loadClient();
    expect(component.isNotFound()).toBeTrue();
    expect(component.client()).toBeNull();
  });

  it('handles 403 error and sets forbidden error message', () => {
    clientApiSpy.getClient.and.returnValue(throwError(() => ({ status: 403 })));
    component.loadClient();
    expect(component.error()).toBe('Vous n’êtes pas autorisé à consulter ce client.');
  });

  it('handles generic error and sets error message', () => {
    clientApiSpy.getClient.and.returnValue(throwError(() => ({ status: 500 })));
    component.loadClient();
    expect(component.error()).toBe('Impossible de charger les informations du client.');
  });
});

