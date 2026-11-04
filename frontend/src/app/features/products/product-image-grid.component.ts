import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProductApiService } from './product-api.service';
import { ProductImage } from './product.models';

@Component({
  selector: 'wwt-product-image-grid',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="image-grid" aria-labelledby="images-title">
      <div class="section-heading">
        <h3 id="images-title">Gestion des images existantes</h3>
        <p>Organisez vos images, définissez une image principale et supprimez-les si nécessaire.</p>
      </div>

      @if (images().length === 0) {
        <div class="empty-state">
          <span aria-hidden="true">▧</span>
          <p>Aucune image n'a été associée à ce produit.</p>
        </div>
      } @else {
        <div class="grid">
          @for (
            image of images();
            track image.id;
            let index = $index;
            let first = $first;
            let last = $last
          ) {
            <div
              class="card"
              [class.loading]="deletingImageId() === image.id"
              [class.error]="imageFailed(image.id)"
              draggable="true"
              (dragstart)="onDragStart($event, index)"
              (dragover)="onDragOver($event)"
              (drop)="onDrop($event, index)"
              (dragend)="onDragEnd($event)"
              [attr.aria-label]="image.main ? 'Image principale : ' + (index + 1) : 'Image : ' + (index + 1)"
            >
              <div class="image-container">
                @if (!imageLoaded(image.id) && !imageFailed(image.id)) {
                  <div class="image-skeleton" role="status" aria-label="Chargement"></div>
                }
                @if (!imageFailed(image.id) && imageSource(image)) {
                  <img
                    [src]="imageSource(image)"
                    [alt]="'Produit image ' + (index + 1)"
                    [class.ready]="imageLoaded(image.id)"
                    loading="lazy"
                    decoding="async"
                    (load)="markImageLoaded(image.id)"
                    (error)="markImageFailed(image.id)"
                  />
                } @else if (imageFailed(image.id)) {
                  <div class="image-error">
                    <span aria-hidden="true">▧</span>
                    <p>Image indisponible</p>
                  </div>
                }
                @if (image.main) {
                  <div class="main-badge" aria-label="Image principale">★ PRINCIPALE</div>
                }
              </div>

              <div class="card-content">
                <p class="image-info">Image {{ index + 1 }} / {{ images().length }}</p>
                <div class="card-actions">
                  @if (!image.main) {
                    <button
                      type="button"
                      class="action-btn set-main"
                      [disabled]="deletingImageId() === image.id"
                      (click)="onSetMain(image)"
                      [attr.aria-label]="'Définir comme image principale'"
                    >
                      ★ Principale
                    </button>
                  }
                  <button
                    type="button"
                    class="action-btn delete"
                    [disabled]="deletingImageId() === image.id"
                    (click)="onDeleteClick(image)"
                    [attr.aria-label]="'Supprimer l’image ' + (index + 1)"
                  >
                    {{ deletingImageId() === image.id ? 'Suppression…' : '✕ Supprimer' }}
                  </button>
                </div>
              </div>
            </div>
          }
        </div>

        @if (dragHintVisible()) {
          <p class="drag-hint" role="status">
            ⟷ Glissez-déposez les images pour les réorganiser
          </p>
        }
      }

      @if (deleteConfirmation(); as confirm) {
        <div class="confirmation-overlay" role="alertdialog" aria-labelledby="confirm-title">
          <div class="confirmation-dialog">
            <h4 id="confirm-title">Supprimer cette image ?</h4>
            <p>Cette action supprimera définitivement l'image du produit.</p>
            <div class="confirmation-actions">
              <button
                type="button"
                class="btn-cancel"
                (click)="onConfirmCancel()"
              >
                Annuler
              </button>
              <button
                type="button"
                class="btn-confirm"
                (click)="onConfirmDelete()"
              >
                Supprimer
              </button>
            </div>
          </div>
        </div>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .image-grid { padding: clamp(1.25rem, 4vw, 2rem); border: 1px solid rgb(26 26 24 / 12%); border-radius: .75rem; background: #fff; box-shadow: 0 1rem 2.5rem rgb(26 26 24 / 6%); }
    .section-heading { margin-bottom: 1.5rem; }
    .section-heading h3 { margin: 0 0 .5rem; color: #1a1a18; font-size: 1rem; font-weight: 750; }
    .section-heading p { margin: 0; color: #6d6965; font-size: .88rem; line-height: 1.5; }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: .5rem;
      padding: 2rem;
      color: #999593;
      text-align: center;
    }
    .empty-state span { font-size: 2rem; }

    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 1rem;
    }

    .card {
      display: flex;
      flex-direction: column;
      border: 1px solid #aaa6a1;
      border-radius: .5rem;
      overflow: hidden;
      background: #fff;
      transition: all 0.2s ease;
      cursor: move;
    }
    .card:hover {
      box-shadow: 0 .5rem 1.5rem rgb(26 26 24 / 12%);
      border-color: #af0f50;
    }
    .card.loading {
      opacity: 0.6;
      pointer-events: none;
    }

    .image-container {
      position: relative;
      width: 100%;
      padding-bottom: 100%;
      background: #f5f5f3;
      overflow: hidden;
    }
    .image-container img, .image-skeleton, .image-error {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
    }
    .image-container img {
      object-fit: cover;
      opacity: 0;
      transition: opacity 0.2s ease;
    }
    .image-container img.ready {
      opacity: 1;
    }

    .image-skeleton {
      background: linear-gradient(90deg, #f0f0ee 25%, #e5e5e2 50%, #f0f0ee 75%);
      background-size: 200% 100%;
      animation: loading 1.5s infinite;
    }
    @keyframes loading {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .image-error {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #faf0f3;
      color: #74102f;
      font-size: .8rem;
      text-align: center;
    }

    .main-badge {
      position: absolute;
      top: .4rem;
      right: .4rem;
      padding: .3rem .6rem;
      border-radius: .3rem;
      background: #af0f50;
      color: #fff;
      font-size: .65rem;
      font-weight: 800;
      letter-spacing: .05em;
    }

    .card-content {
      padding: .75rem;
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .image-info {
      margin: 0 0 .5rem;
      color: #6d6965;
      font-size: .78rem;
      font-weight: 700;
    }
    .card-actions {
      display: flex;
      flex-direction: column;
      gap: .4rem;
      margin-top: auto;
    }

    .action-btn {
      padding: .4rem .6rem;
      border: 1px solid;
      border-radius: .3rem;
      background: #fff;
      font: inherit;
      font-size: .75rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .action-btn.set-main {
      border-color: #af0f50;
      color: #af0f50;
    }
    .action-btn.set-main:hover:not(:disabled) {
      background: rgb(175 15 80 / 5%);
    }
    .action-btn.delete {
      border-color: #c74a5f;
      color: #c74a5f;
    }
    .action-btn.delete:hover:not(:disabled) {
      background: rgb(199 74 95 / 5%);
    }
    .action-btn:disabled {
      cursor: not-allowed;
      opacity: 0.5;
    }
    .action-btn:focus-visible {
      outline: 2px solid rgb(175 15 80 / 20%);
    }

    .drag-hint {
      margin-top: 1rem;
      text-align: center;
      color: #999593;
      font-size: .88rem;
      font-style: italic;
    }

    .confirmation-overlay {
      position: fixed;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(26, 26, 24, 0.5);
      z-index: 1000;
    }
    .confirmation-dialog {
      padding: 1.5rem;
      border-radius: .5rem;
      background: #fff;
      box-shadow: 0 2rem 4rem rgb(26 26 24 / 20%);
      max-width: 400px;
    }
    .confirmation-dialog h4 {
      margin: 0 0 .75rem;
      color: #1a1a18;
      font-size: 1rem;
    }
    .confirmation-dialog p {
      margin: 0 0 1.25rem;
      color: #6d6965;
      font-size: .9rem;
      line-height: 1.5;
    }
    .confirmation-actions {
      display: flex;
      gap: .75rem;
      justify-content: flex-end;
    }
    .btn-cancel, .btn-confirm {
      padding: .6rem 1rem;
      border: 1px solid;
      border-radius: .4rem;
      font: inherit;
      font-weight: 750;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .btn-cancel {
      border-color: #aaa6a1;
      background: #fff;
      color: #1a1a18;
    }
    .btn-cancel:hover {
      background: #f5f5f3;
    }
    .btn-confirm {
      border-color: #c74a5f;
      background: #c74a5f;
      color: #fff;
    }
    .btn-confirm:hover {
      background: #b0404d;
    }

    @media (max-width: 640px) {
      .grid {
        grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
      }
    }
  `]
})
export class ProductImageGridComponent {
  private readonly productApi = inject(ProductApiService);

  readonly productId = input.required<string>();
  readonly images = input<readonly ProductImage[]>([]);
  readonly imageDeleted = output<void>();
  readonly imageMainChanged = output<void>();
  readonly imagesReordered = output<readonly string[]>();

  protected readonly deletingImageId = signal<string | null>(null);
  protected readonly deleteConfirmation = signal<ProductImage | null>(null);
  protected readonly imageLoadedIds = signal(new Set<string>());
  protected readonly imageFailedIds = signal(new Set<string>());
  protected readonly dragHintVisible = signal(false);
  protected readonly draggedIndex = signal<number | null>(null);

  protected imageLoaded(imageId: string): boolean {
    return this.imageLoadedIds().has(imageId);
  }

  protected imageFailed(imageId: string): boolean {
    return this.imageFailedIds().has(imageId);
  }

  protected markImageLoaded(imageId: string): void {
    const updated = new Set(this.imageLoadedIds());
    updated.add(imageId);
    this.imageLoadedIds.set(updated);
  }

  protected markImageFailed(imageId: string): void {
    const updated = new Set(this.imageFailedIds());
    updated.add(imageId);
    this.imageFailedIds.set(updated);
  }

  protected resolveImageUrl(imageUrl: string): string {
    return this.productApi.resolveImageUrl(imageUrl);
  }

  protected imageSource(image: ProductImage): string {
    if (!image?.imageUrl) return '';
    return this.productApi.resolveImageUrl(image.imageUrl);
  }

  protected onSetMain(image: ProductImage): void {
    if (this.deletingImageId() !== null) return;
    if (image.main) return;

    this.deletingImageId.set(image.id);
    this.productApi.setImageAsMain(this.productId(), image.id).subscribe({
      next: () => {
        this.deletingImageId.set(null);
        this.imageMainChanged.emit();
      },
      error: (err: unknown) => {
        console.error('Failed to set main image:', err);
        this.deletingImageId.set(null);
      }
    });
  }

  protected onDeleteClick(image: ProductImage): void {
    this.deleteConfirmation.set(image);
  }

  protected onConfirmCancel(): void {
    this.deleteConfirmation.set(null);
  }

  protected onConfirmDelete(): void {
    const image = this.deleteConfirmation();
    if (!image) return;

    this.deleteConfirmation.set(null);
    this.deletingImageId.set(image.id);
    this.productApi.deleteImage(this.productId(), image.id).subscribe({
      next: () => {
        this.deletingImageId.set(null);
        this.imageDeleted.emit();
      },
      error: (err: unknown) => {
        console.error('Failed to delete image:', err);
        this.deletingImageId.set(null);
      }
    });
  }

  protected onDragStart(event: DragEvent, index: number): void {
    this.draggedIndex.set(index);
    this.dragHintVisible.set(true);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', index.toString());
    }
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
  }

  protected onDrop(event: DragEvent, targetIndex: number): void {
    event.preventDefault();
    event.stopPropagation();

    const sourceIndex = this.draggedIndex();
    if (sourceIndex === null || sourceIndex === targetIndex) {
      return;
    }

    const currentImages = [...this.images()];
    const [movedImage] = currentImages.splice(sourceIndex, 1);
    currentImages.splice(targetIndex, 0, movedImage);

    const reorderedIds = currentImages.map(img => img.id);
    this.imagesReordered.emit(reorderedIds);
  }

  protected onDragEnd(_event: DragEvent): void {
    this.draggedIndex.set(null);
  }
}
