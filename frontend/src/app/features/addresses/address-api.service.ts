import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { Address, AddressRequest } from './address.models';

@Injectable({ providedIn: 'root' })
export class AddressApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/v1/users/me/addresses`;

  getAddresses(): Observable<readonly Address[]> {
    return this.http.get<readonly Address[]>(this.resourceUrl);
  }

  createAddress(request: AddressRequest): Observable<Address> {
    return this.http.post<Address>(this.resourceUrl, request);
  }

  updateAddress(addressId: string, request: AddressRequest): Observable<Address> {
    return this.http.put<Address>(`${this.resourceUrl}/${encodeURIComponent(addressId)}`, request);
  }

  deleteAddress(addressId: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${encodeURIComponent(addressId)}`);
  }

  setDefaultAddress(addressId: string): Observable<Address> {
    return this.http.put<Address>(`${this.resourceUrl}/${encodeURIComponent(addressId)}/default`, null);
  }
}
