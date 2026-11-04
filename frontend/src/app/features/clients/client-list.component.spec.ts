import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { ClientApiService } from './client-api.service';
import { ClientListComponent } from './client-list.component';
import { Client, PageResponse } from './client.models';

describe('ClientListComponent', () => {
  let component: ClientListComponent;
  let fixture: ComponentFixture<ClientListComponent>;
  let clientApiSpy: jasmine.SpyObj<ClientApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;

  const mockClientsPage: PageResponse<Client> = {
    content: [
      { id: 'c-1', firstName: 'Ahmed', lastName: 'Alami', email: 'ahmed@test.ma', phone: '+212600000000', enabled: true },
      { id: 'c-2', firstName: 'Fatima', lastName: 'Bennani', email: 'fatima@test.ma', phone: null, enabled: false }
    ],
    number: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true
  };

  beforeEach(async () => {
    clientApiSpy = jasmine.createSpyObj('ClientApiService', ['getClients']);
    clientApiSpy.getClients.and.returnValue(of(mockClientsPage));

    authSpy = jasmine.createSpyObj('AuthService', ['roles']);
    authSpy.roles.and.returnValue(['ADMIN']);

    await TestBed.configureTestingModule({
      imports: [ClientListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ClientApiService, useValue: clientApiSpy },
        { provide: AuthService, useValue: authSpy },
        { provide: RUNTIME_CONFIG, useValue: { apiBaseUrl: 'http://localhost:8080' } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ClientListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load clients on init', () => {
    expect(component).toBeTruthy();
    expect(clientApiSpy.getClients).toHaveBeenCalledWith(0, 20, undefined);
    expect(component.clients().length).toBe(2);
  });

  it('computes correct detailBasePath based on user role', () => {
    expect(component.detailBasePath()).toBe('/admin/clients');

    authSpy.roles.and.returnValue(['MANAGER']);
    fixture.detectChanges();
    expect(component.detailBasePath()).toBe('/manager/clients');

    authSpy.roles.and.returnValue(['COMMERCIAL']);
    fixture.detectChanges();
    expect(component.detailBasePath()).toBe('/commercial/clients');
  });

  it('updates search query and reloads page 0 on search input', () => {
    component.onSearchInput('Alami');
    expect(component.searchQuery()).toBe('Alami');
    expect(clientApiSpy.getClients).toHaveBeenCalledWith(0, 20, 'Alami');
  });

  it('resets search query and reloads on resetSearch()', () => {
    component.onSearchInput('Alami');
    component.resetSearch();
    expect(component.searchQuery()).toBe('');
    expect(clientApiSpy.getClients).toHaveBeenCalledWith(0, 20, undefined);
  });

  it('navigates to specified page on goToPage()', () => {
    component.goToPage(1);
    expect(clientApiSpy.getClients).toHaveBeenCalledWith(1, 20, undefined);
  });

  it('handles error response and sets user-facing error message', () => {
    clientApiSpy.getClients.and.returnValue(throwError(() => ({ status: 500 })));
    component.load();
    expect(component.error()).toBe('Impossible de charger les données clients. Veuillez réessayer.');
  });

  it('handles 403 error message', () => {
    clientApiSpy.getClients.and.returnValue(throwError(() => ({ status: 403 })));
    component.load();
    expect(component.error()).toBe('Vous n’êtes pas autorisé à consulter l’annuaire des clients.');
  });

  it('correctly calculates client initials', () => {
    expect(component.initials({ id: '1', firstName: 'Ahmed', lastName: 'Alami', email: 'a@a.com', enabled: true })).toBe('AA');
    expect(component.initials({ id: '2', firstName: '', lastName: '', email: 'a@a.com', enabled: true })).toBe('C');
  });
});

