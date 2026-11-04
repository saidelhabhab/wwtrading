import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { SidebarComponent } from './sidebar.component';

describe('Shared SidebarComponent', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['roles', 'logout']);
    authSpy.roles.and.returnValue(['ADMIN']);

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders Clients nav item for ADMIN pointing to /admin/clients', () => {
    authSpy.roles.and.returnValue(['ADMIN']);
    fixture.detectChanges();

    const items = (component as any).navItems();
    const clientsItem = items.find((i: any) => i.label === 'Clients');

    expect(clientsItem).toBeTruthy();
    expect(clientsItem.available).toBeTrue();
    expect(clientsItem.route).toBe('/admin/clients');
  });

  it('renders Clients nav item for MANAGER pointing to /manager/clients', () => {
    authSpy.roles.and.returnValue(['MANAGER']);
    fixture.detectChanges();

    const items = (component as any).navItems();
    const clientsItem = items.find((i: any) => i.label === 'Clients');

    expect(clientsItem).toBeTruthy();
    expect(clientsItem.available).toBeTrue();
    expect(clientsItem.route).toBe('/manager/clients');
  });

  it('renders Clients nav item for COMMERCIAL pointing to /commercial/clients', () => {
    authSpy.roles.and.returnValue(['COMMERCIAL']);
    fixture.detectChanges();

    const items = (component as any).navItems();
    const clientsItem = items.find((i: any) => i.label === 'Clients');

    expect(clientsItem).toBeTruthy();
    expect(clientsItem.available).toBeTrue();
    expect(clientsItem.route).toBe('/commercial/clients');
  });

  it('does NOT render Clients nav item for TECHNICIAN', () => {
    authSpy.roles.and.returnValue(['TECHNICIAN']);
    fixture.detectChanges();

    const items = (component as any).navItems();
    const clientsItem = items.find((i: any) => i.label === 'Clients');

    expect(clientsItem).toBeUndefined();
  });

  it('does NOT render Clients nav item for CLIENT', () => {
    authSpy.roles.and.returnValue(['CLIENT']);
    fixture.detectChanges();

    const items = (component as any).navItems();
    const clientsItem = items.find((i: any) => i.label === 'Clients');

    expect(clientsItem).toBeUndefined();
  });
});

