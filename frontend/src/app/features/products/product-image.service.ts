import { HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ProductApiService } from './product-api.service';
import { ProductImage } from './product.models';

export interface ImageUploadProgress {
  readonly percentage: number;
  readonly loaded: number;
  readonly total: number;
}

export interface ImageUploadResult {
  readonly file: File;
  readonly image: ProductImage;
  readonly preview: string;
}

export interface ImagePreview {
  readonly file: File;
  readonly preview: string;
  readonly filename: string;
  readonly size: number;
  readonly error: string | null;
}

const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_FILE_SIZE = 5 * 1024 * 1024;

@Injectable({ providedIn: 'root' })
export class ProductImageService {
  private readonly productApi = inject(ProductApiService);
  private readonly objectUrls = new Set<string>();

  /**
   * Validate files before upload
   */
  validateFiles(files: File[]): ImagePreview[] {
    return files.map(file => {
      if (file.size === 0) {
        return {
          file,
          preview: '',
          filename: file.name,
          size: file.size,
          error: 'Le fichier est vide.'
        };
      }

      if (!ACCEPTED_TYPES.has(file.type)) {
        return {
          file,
          preview: '',
          filename: file.name,
          size: file.size,
          error: 'Format non accepté. Utilisez JPEG, PNG ou WebP.'
        };
      }

      if (file.size > MAX_FILE_SIZE) {
        return {
          file,
          preview: '',
          filename: file.name,
          size: file.size,
          error: `Fichier trop volumineux (${this.formatFileSize(file.size)}). Maximum : ${this.formatFileSize(MAX_FILE_SIZE)}.`
        };
      }

      const preview = URL.createObjectURL(file);
      this.objectUrls.add(preview);

      return {
        file,
        preview,
        filename: file.name,
        size: file.size,
        error: null
      };
    });
  }

  /**
   * Upload single image with progress tracking
   */
  uploadImageWithProgress(
    productId: string,
    file: File
  ): Observable<HttpEvent<ProductImage>> {
    return this.productApi.uploadImageWithProgress(productId, file).pipe(
      map((event: HttpEvent<ProductImage>) => {
        if (event.type === HttpEventType.Response && event.body) {
          return new HttpResponse<ProductImage>({
            body: event.body,
            headers: event.headers,
            status: event.status,
            statusText: event.statusText,
            url: event.url ?? undefined
          });
        }
        return event;
      })
    );
  }

  /**
   * Set image as main
   */
  setImageAsMain(productId: string, imageId: string): Observable<ProductImage> {
    return this.productApi.setImageAsMain(productId, imageId);
  }

  /**
   * Delete image
   */
  deleteImage(productId: string, imageId: string): Observable<void> {
    return this.productApi.deleteImage(productId, imageId);
  }

  /**
   * Reorder images
   */
  reorderImages(productId: string, imageIds: readonly string[]): Observable<readonly ProductImage[]> {
    return this.productApi.reorderImages(productId, imageIds);
  }

  /**
   * Load images for a product
   */
  loadImages(productId: string): Observable<readonly ProductImage[]> {
    return this.productApi.getImages(productId);
  }

  /**
   * Get resolved image URL
   */
  resolveImageUrl(imageUrl: string): string {
    return this.productApi.resolveImageUrl(imageUrl);
  }

  /**
   * Cleanup preview URLs to avoid memory leaks
   */
  revokePreview(url: string): void {
    if (this.objectUrls.has(url)) {
      URL.revokeObjectURL(url);
      this.objectUrls.delete(url);
    }
  }

  /**
   * Cleanup all preview URLs
   */
  revokeAllPreviews(): void {
    this.objectUrls.forEach(url => URL.revokeObjectURL(url));
    this.objectUrls.clear();
  }

  /**
   * Format file size for display
   */
  private formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }
}
