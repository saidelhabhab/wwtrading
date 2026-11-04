import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ImagePreview } from './product-image.service';

@Component({
  selector: 'wwt-product-image-upload',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="image-upload" aria-labelledby="upload-title">
      <div class="section-heading">
        <h3 id="upload-title">Images du produit</h3>
        <p>Ajoutez plusieurs photos du produit. La première image sera affichée dans le catalogue.</p>
      </div>

      <div
        class="upload-zone"
        [class.dragging]="isDragging()"
        (dragenter)="onDragEnter($event)"
        (dragover)="onDragOver($event)"
        (dragleave)="onDragLeave($event)"
        (drop)="onDrop($event)"
        role="region"
        aria-label="Zone de dépôt d'images"
      >
        <svg
          class="upload-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
          <polyline points="17 8 12 3 7 8"></polyline>
          <line x1="12" y1="3" x2="12" y2="15"></line>
        </svg>
        <h4>Glissez-déposez vos images ici</h4>
        <p>ou</p>
        <label class="file-input-label">
          <span>Cliquez pour sélectionner des fichiers</span>
          <input
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp"
            (change)="onFileInputChange($event)"
            aria-label="Sélectionner des images"
            class="visually-hidden"
          />
        </label>
        <p class="upload-hint">Formats acceptés : JPEG, PNG, WebP (max. 5 MB par image)</p>
      </div>

      @if (previews().length > 0) {
        <div class="preview-list" aria-label="Aperçu des images sélectionnées">
          <div class="preview-count">{{ previews().length }} image(s) en attente d'upload</div>
          <div class="preview-grid">
            @for (preview of previews(); track preview.file.name + preview.file.lastModified) {
              <div class="preview-item" [class.error]="preview.error !== null">
                @if (!preview.error && preview.preview) {
                  <img [src]="preview.preview" [alt]="preview.filename" />
                  <div class="preview-overlay">
                    <button
                      type="button"
                      class="remove-btn"
                      [attr.aria-label]="'Supprimer ' + preview.filename"
                      (click)="onRemovePreview(preview)"
                    >
                      ✕
                    </button>
                  </div>
                  <p class="filename">{{ preview.filename }}</p>
                } @else {
                  <div class="preview-error">
                    <span aria-hidden="true">⚠</span>
                    <p class="error-message">{{ preview.error }}</p>
                    <button
                      type="button"
                      class="remove-btn"
                      [attr.aria-label]="'Supprimer ' + preview.filename"
                      (click)="onRemovePreview(preview)"
                    >
                      ✕ Retirer
                    </button>
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .image-upload { padding: clamp(1.25rem, 4vw, 2rem); border: 1px solid rgb(26 26 24 / 12%); border-radius: .75rem; background: #fff; box-shadow: 0 1rem 2.5rem rgb(26 26 24 / 6%); }
    .section-heading { margin-bottom: 1.5rem; }
    .section-heading h3 { margin: 0 0 .5rem; color: #1a1a18; font-size: 1rem; font-weight: 750; }
    .section-heading p { margin: 0; color: #6d6965; font-size: .88rem; line-height: 1.5; }

    .upload-zone {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      padding: 2rem;
      border: 2px dashed #aaa6a1;
      border-radius: .6rem;
      background: #fafaf8;
      transition: all 0.2s ease;
      cursor: pointer;
    }
    .upload-zone.dragging {
      border-color: #af0f50;
      background: rgb(175 15 80 / 5%);
    }

    .upload-icon { width: 3rem; height: 3rem; color: #af0f50; }
    .upload-zone h4 { margin: 0; color: #1a1a18; font-size: 1rem; font-weight: 750; }
    .upload-zone p { margin: 0; color: #6d6965; font-size: .88rem; }
    .upload-hint { font-size: .78rem; color: #999593; }

    .file-input-label {
      display: inline-block;
      padding: .6rem 1.2rem;
      border: 1px solid #af0f50;
      border-radius: .4rem;
      background: #fff;
      color: #af0f50;
      font-weight: 750;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .file-input-label:hover {
      background: rgb(175 15 80 / 5%);
    }
    .file-input-label:focus-visible {
      outline: 3px solid rgb(175 15 80 / 20%);
    }

    .preview-list { margin-top: 1.5rem; }
    .preview-count { margin-bottom: 1rem; color: #1a1a18; font-weight: 750; font-size: .88rem; }
    .preview-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
      gap: 1rem;
    }

    .preview-item {
      position: relative;
      aspect-ratio: 1;
      border-radius: .4rem;
      overflow: hidden;
      background: #f5f5f3;
      border: 1px solid #aaa6a1;
    }
    .preview-item.error {
      background: #faf0f3;
      border-color: #d9a0ab;
    }

    .preview-item img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .preview-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(26, 26, 24, 0.7);
      opacity: 0;
      transition: opacity 0.2s ease;
    }
    .preview-item:hover .preview-overlay {
      opacity: 1;
    }

    .remove-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 2.5rem;
      height: 2.5rem;
      border-radius: 50%;
      background: #af0f50;
      color: #fff;
      border: none;
      font-size: 1.2rem;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .remove-btn:hover {
      background: #8f0f42;
      transform: scale(1.1);
    }
    .remove-btn:focus-visible {
      outline: 3px solid rgb(175 15 80 / 20%);
    }

    .filename {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      margin: 0;
      padding: .4rem .5rem;
      background: rgba(26, 26, 24, 0.8);
      color: #fff;
      font-size: .65rem;
      font-weight: 700;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .preview-error {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: .5rem;
      padding: .75rem;
      text-align: center;
      height: 100%;
    }
    .preview-error span {
      font-size: 1.5rem;
    }
    .error-message {
      margin: 0;
      color: #74102f;
      font-size: .65rem;
      font-weight: 700;
      line-height: 1.3;
    }
    .preview-error .remove-btn {
      width: auto;
      height: auto;
      padding: .3rem .6rem;
      font-size: .65rem;
    }

    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border-width: 0;
    }

    @media (max-width: 640px) {
      .preview-grid {
        grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
      }
    }
  `]
})
export class ProductImageUploadComponent {
  readonly previews = input<readonly ImagePreview[]>([]);
  readonly previewsChanged = output<readonly ImagePreview[]>();
  readonly filesSelected = output<File[]>();

  protected readonly isDragging = signal(false);

  protected onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      const files = Array.from(input.files);
      this.filesSelected.emit(files);
      input.value = '';
    }
  }

  protected onDragEnter(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(true);
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if ((event.target as HTMLElement).classList.contains('upload-zone')) {
      this.isDragging.set(false);
    }
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging.set(false);

    if (event.dataTransfer?.files) {
      const files = Array.from(event.dataTransfer.files).filter(
        file => file.type.startsWith('image/')
      );
      if (files.length > 0) {
        this.filesSelected.emit(files);
      }
    }
  }

  protected onRemovePreview(preview: ImagePreview): void {
    const updated = this.previews().filter(p => p.file !== preview.file);
    this.previewsChanged.emit(updated);
  }
}
