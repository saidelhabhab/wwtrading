import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { Client, PageResponse } from './client.models';

@Injectable({ providedIn: 'root' })
export class ClientApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/v1/clients`;

  getClients(page = 0, size = 20, q?: string, sort = 'lastName,asc'): Observable<PageResponse<Client>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    if (q && q.trim().length > 0) {
      params = params.set('q', q.trim());
    }

    return this.http.get<PageResponse<Client>>(this.resourceUrl, { params });
  }

  getClient(id: string): Observable<Client> {
    return this.http.get<Client>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }
}

