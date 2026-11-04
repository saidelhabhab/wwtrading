import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import { CreateProductRequest, Product, ProductFilters, ProductImage, ProductPage, UpdateProductRequest } from './product.models';

@Injectable({ providedIn: 'root' })
export class ProductApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/products`;

  /**
   * Lists products. Filters are sent to the API so the database filters before
   * pagination; omitted filters leave the request unchanged.
   */
  list(page = 0, size = 20, sort = 'name,asc', filters: ProductFilters = {}): Observable<ProductPage> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    if (filters.search && filters.search.trim()) params = params.set('search', filters.search.trim());
    if (filters.type) params = params.set('type', filters.type);
    if (filters.machineCategory) params = params.set('machineCategory', filters.machineCategory);
    if (filters.colorMode) params = params.set('colorMode', filters.colorMode);
    return this.http.get<ProductPage>(this.resourceUrl, { params });
  }

  getById(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  getByReference(reference: string): Observable<Product> {
    return this.http.get<Product>(
      `${this.resourceUrl}/reference/${encodeURIComponent(reference)}`
    );
  }

  getImages(productId: string): Observable<readonly ProductImage[]> {
    return this.http.get<readonly ProductImage[]>(
      `${this.resourceUrl}/${encodeURIComponent(productId)}/images`
    );
  }

  resolveImageUrl(imageUrl: string): string {
    if (!imageUrl) return '';
    if (/^https?:\/\//i.test(imageUrl) || imageUrl.startsWith('data:') || imageUrl.startsWith('blob:')) return imageUrl;
    const baseUrl = this.config.apiBaseUrl.replace(/\/$/, '');
    return `${baseUrl}/${imageUrl.replace(/^\//, '')}`;
  }

  getImageContent(imageUrl: string): Observable<Blob> {
    return this.http.get(this.resolveImageUrl(imageUrl), { responseType: 'blob' });
  }

  create(request: CreateProductRequest): Observable<Product> {
    console.log("data product :",request);
    return this.http.post<Product>(this.resourceUrl, request);
  }

  update(id: string, request: UpdateProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.resourceUrl}/${encodeURIComponent(id)}`, request);
  }

  activateProduct(id: string): Observable<Product> {
    return this.http.post<Product>(`${this.resourceUrl}/${encodeURIComponent(id)}/activate`, null);
  }

  deactivateProduct(id: string): Observable<Product> {
    return this.http.post<Product>(`${this.resourceUrl}/${encodeURIComponent(id)}/deactivate`, null);
  }

  uploadImage(productId: string, file: File): Observable<ProductImage> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ProductImage>(
      `${this.resourceUrl}/${encodeURIComponent(productId)}/images`,
      formData
    );
  }

  uploadImageWithProgress(productId: string, file: File): Observable<HttpEvent<ProductImage>> {
    const formData = new FormData();
    formData.append('file', this.normalizeImageFile(file));
    return this.http.request<ProductImage>(
      'POST',
      `${this.resourceUrl}/${encodeURIComponent(productId)}/images`,
      {
        body: formData,
        reportProgress: true,
        observe: 'events'
      }
    );
  }

  private normalizeImageFile(file: File): File {
    const extension = file.name.split('.').pop()?.toLowerCase();
    let mediaType = file.type;
    switch (extension) {
      case 'jpg':
      case 'jpeg':
        mediaType = 'image/jpeg';
        break;
      case 'png':
        mediaType = 'image/png';
        break;
      case 'webp':
        mediaType = 'image/webp';
        break;
    }

    if (!mediaType || file.type === mediaType) {
      return file;
    }

    return new File([file], file.name, {
      type: mediaType,
      lastModified: file.lastModified
    });
  }

  setImageAsMain(productId: string, imageId: string): Observable<ProductImage> {
    return this.http.put<ProductImage>(
      `${this.resourceUrl}/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}/main`,
      null
    );
  }

  deleteImage(productId: string, imageId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.resourceUrl}/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`
    );
  }

  reorderImages(productId: string, imageIds: readonly string[]): Observable<readonly ProductImage[]> {
    return this.http.put<readonly ProductImage[]>(
      `${this.resourceUrl}/${encodeURIComponent(productId)}/images/order`,
      { imageIds }
    );
  }
}
