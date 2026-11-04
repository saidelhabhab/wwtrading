import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { WishlistApiService } from './wishlist-api.service';
import { WishlistComponent } from './wishlist.component';
import { Wishlist } from './wishlist.models';

describe('WishlistApiService', () => {
  let service: WishlistApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        WishlistApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: { apiBaseUrl: 'http://localhost:8080', keycloak: {} } }
      ]
    });
    service = TestBed.inject(WishlistApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets the current wishlist without adding customer identity', () => {
    service.getCurrentWishlist().subscribe();
    const request = http.expectOne('http://localhost:8080/api/wishlist');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({ id: 'wishlist-1', items: [], createdAt: '', updatedAt: '' });
  });

  it('uses the existing wishlist item endpoints', () => {
    service.addProduct('PR-001').subscribe();
    const add = http.expectOne('http://localhost:8080/api/wishlist/items');
    expect(add.request.method).toBe('POST');
    expect(add.request.body).toEqual({ productReference: 'PR-001' });
    add.flush({ id: 'wishlist-1', items: [], createdAt: '', updatedAt: '' });

    service.removeProduct('item/1').subscribe();
    const remove = http.expectOne('http://localhost:8080/api/wishlist/items/item%2F1');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);

    service.clearWishlist().subscribe();
    const clear = http.expectOne('http://localhost:8080/api/wishlist/items');
    expect(clear.request.method).toBe('DELETE');
    clear.flush(null);
  });
});

describe('WishlistComponent actions', () => {
  let fixture: ComponentFixture<WishlistComponent>;
  let component: WishlistComponent;
  let api: jasmine.SpyObj<WishlistApiService>;
  const wishlist: Wishlist = {
    id: 'wishlist-1',
    items: [
      { id: 'item-1', productReference: 'PR-001' },
      { id: 'item-2', productReference: 'PR-002' }
    ],
    createdAt: '',
    updatedAt: ''
  };

  beforeEach(() => {
    api = jasmine.createSpyObj<WishlistApiService>('WishlistApiService', [
      'getCurrentWishlist', 'removeProduct', 'clearWishlist'
    ]);
    api.getCurrentWishlist.and.returnValue(of(wishlist));
    TestBed.configureTestingModule({
      imports: [WishlistComponent],
      providers: [{ provide: WishlistApiService, useValue: api }]
    });
    fixture = TestBed.createComponent(WishlistComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('removes an item after a successful response and prevents duplicate removal', () => {
    const response = new Subject<void>();
    api.removeProduct.and.returnValue(response.asObservable());
    const button = fixture.nativeElement.querySelector('.item .action') as HTMLButtonElement;

    button.click();
    fixture.detectChanges();
    expect(api.removeProduct).toHaveBeenCalledOnceWith('item-1');
    expect(button.disabled).toBeTrue();
    button.click();
    expect(api.removeProduct).toHaveBeenCalledOnceWith('item-1');

    response.next();
    response.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('PR-001');
  });

  it('clears the wishlist after confirmation and a successful response', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    api.clearWishlist.and.returnValue(of(void 0));

    (fixture.nativeElement.querySelector('.clear-action') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(window.confirm).toHaveBeenCalledOnceWith('Voulez-vous vraiment vider votre liste de souhaits ?');
    expect(api.clearWishlist).toHaveBeenCalledOnceWith();
    expect(fixture.nativeElement.textContent).toContain('Votre liste de souhaits est vide.');
  });

  it('does not clear when confirmation is declined', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    (fixture.nativeElement.querySelector('.clear-action') as HTMLButtonElement).click();

    expect(api.clearWishlist).not.toHaveBeenCalled();
  });

  it('shows safe messages for not found, conflict and unavailable errors', () => {
    api.removeProduct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    (fixture.nativeElement.querySelectorAll('.item .action')[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cette référence n’existe plus');

    api.removeProduct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    (fixture.nativeElement.querySelectorAll('.item .action')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cette action ne peut pas être effectuée');

    api.removeProduct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    (fixture.nativeElement.querySelectorAll('.item .action')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('service est momentanément indisponible');
    expect(fixture.nativeElement.textContent).not.toContain('Authorization');
  });
});
