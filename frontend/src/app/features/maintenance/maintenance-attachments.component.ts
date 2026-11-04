import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { DOCUMENT } from '@angular/common';
import { catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';

const IMAGE_MIME_PREFIX = 'image/';
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'];

const FILE_ICONS: ReadonlyArray<readonly [string, string]> = [
  ['pdf', 'picture_as_pdf'],
  ['zip', 'folder_zip'],
  ['doc', 'description'],
  ['xls', 'table_chart'],
];

import { MaintenanceApiService } from './maintenance-api.service';
import { MaintenanceAttachmentResponse } from './maintenance.models';

interface AttachmentView {
  readonly attachment: MaintenanceAttachmentResponse;
  readonly blobUrl: string;
  readonly safeUrl: SafeResourceUrl;
  readonly isImage: boolean;
  readonly failed: boolean;
  readonly icon: string;
}

function extractExtension(filename: string): string {
  const parts = filename.toLowerCase().split('.');
  return parts.length > 1 ? parts[parts.length - 1] : '';
}

function resolveIcon(filename: string): string {
  const extension = extractExtension(filename);
  const entry = FILE_ICONS.find(([ext]) => extension === ext || extension.startsWith(ext));
  return entry ? entry[1] : 'insert_drive_file';
}

@Component({
  selector: 'wwt-maintenance-attachments',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="attachments" aria-labelledby="maintenance-photos-title">
      <div class="attachments__header">
        <h2 id="maintenance-photos-title">Photos du problème</h2>
        @if (downloadableCount() > 1) {
          <button type="button" class="btn-dl-all" (click)="downloadAll()">
            <span class="material-icons" aria-hidden="true">download</span>
            Télécharger toutes les photos
          </button>
        }
      </div>
      @if (loading()) {
        <p class="state" role="status">Chargement des photos…</p>
      } @else if (error()) {
        <p class="state state--error" role="alert">{{ error() }}</p>
      } @else if (views().length === 0) {
        <p class="state">Aucune photo jointe à cette demande.</p>
      } @else {
        <div class="gallery">
          @for (view of views(); track view.attachment.id) {
            <figure [class.figure--broken]="view.failed">
              @if (view.isImage && !view.failed) {
                <button type="button" class="thumb thumb--image" (click)="openLightbox(view.attachment.id)"
                        [attr.aria-label]="'Agrandir ' + view.attachment.filename">
                  <img [src]="view.safeUrl" [alt]="'Photo jointe : ' + view.attachment.filename" loading="lazy"
                       (error)="markFailed(view.attachment.id)">
                  <span class="thumb__zoom" aria-hidden="true"><span class="material-icons">zoom_in</span></span>
                </button>
              } @else {
                <div class="thumb thumb--file">
                  <span class="material-icons thumb__icon" aria-hidden="true">{{ view.icon }}</span>
                  @if (view.failed) {
                    <span class="thumb__note">Aperçu indisponible</span>
                  }
                </div>
              }
              <figcaption>
                <span class="fig-name" [title]="view.attachment.filename">{{ view.attachment.filename }}</span>
                <a class="fig-dl" [href]="view.safeUrl" [download]="view.attachment.filename"
                   [attr.aria-label]="'Télécharger ' + view.attachment.filename">
                  <span class="material-icons" aria-hidden="true">download</span>
                </a>
              </figcaption>
            </figure>
          }
        </div>
      }
    </section>

    @if (current(); as view) {
      <div class="lightbox" role="dialog" aria-modal="true" aria-label="Aperçu de la photo" (click)="closeLightbox()">
        <div class="lightbox__panel" (click)="$event.stopPropagation()">
          <header class="lightbox__header">
            <span class="lightbox__name" [title]="view.attachment.filename">{{ view.attachment.filename }}</span>
            <span class="lightbox__count">{{ lightboxPosition() + 1 }} / {{ imageViews().length }}</span>
            <div class="lightbox__actions">
              <a class="lightbox__btn" [href]="view.safeUrl" [download]="view.attachment.filename">
                <span class="material-icons" aria-hidden="true">download</span>
                Télécharger
              </a>
              <button type="button" class="lightbox__btn lightbox__btn--icon" (click)="closeLightbox()" aria-label="Fermer">
                <span class="material-icons" aria-hidden="true">close</span>
              </button>
            </div>
          </header>
          <div class="lightbox__stage">
            @if (imageViews().length > 1) {
              <button type="button" class="lightbox__nav lightbox__nav--prev" (click)="previousImage()" aria-label="Photo précédente">
                <span class="material-icons" aria-hidden="true">chevron_left</span>
              </button>
            }
            <img class="lightbox__img" [src]="view.safeUrl" [alt]="'Photo jointe : ' + view.attachment.filename">
            @if (imageViews().length > 1) {
              <button type="button" class="lightbox__nav lightbox__nav--next" (click)="nextImage()" aria-label="Photo suivante">
                <span class="material-icons" aria-hidden="true">chevron_right</span>
              </button>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .attachments { padding: 1.25rem; border: 1px solid #2a2a2a; border-radius: 10px; background: #141414; }
    .attachments__header { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: 1rem; }
    h2 { margin: 0; color: #fff; font-size: 1rem; }
    .state { margin: 0; color: #999; font-size: .875rem; }
    .state--error { color: #ef8a8a; }

    .btn-dl-all {
      display: inline-flex; align-items: center; gap: .4rem;
      padding: .4rem .75rem; border: 1px solid #3a3a3a; border-radius: 8px;
      background: #1c1c1c; color: #eee; font-size: .78rem; cursor: pointer;
      transition: border-color .15s ease, background .15s ease;
    }
    .btn-dl-all:hover { border-color: #e91e8c; background: #242424; }
    .btn-dl-all .material-icons { font-size: 1rem; }

    .gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr)); gap: .85rem; }
    figure { min-width: 0; margin: 0; overflow: hidden; border: 1px solid #303030; border-radius: 8px; background: #0f0f0f; transition: border-color .15s ease; }
    figure:hover { border-color: #4a4a4a; }
    .thumb { position: relative; display: flex; align-items: center; justify-content: center; width: 100%; aspect-ratio: 4 / 3; padding: 0; border: 0; background: #0a0a0a; }
    .thumb--image { padding: 0; cursor: zoom-in; overflow: hidden; }
    .thumb--image img { display: block; width: 100%; height: 100%; object-fit: cover; transition: transform .2s ease; }
    .thumb--image:hover img { transform: scale(1.04); }
    .thumb__zoom {
      position: absolute; right: .45rem; bottom: .45rem; display: flex; align-items: center; justify-content: center;
      width: 1.6rem; height: 1.6rem; border-radius: 50%; background: rgba(0, 0, 0, .65); color: #fff; opacity: 0; transition: opacity .15s ease;
    }
    .thumb__zoom .material-icons { font-size: 1rem; }
    .thumb--image:hover .thumb__zoom { opacity: 1; }
    .thumb--file { flex-direction: column; gap: .5rem; }
    .thumb__icon { color: #888; font-size: 2.2rem; }
    .thumb__note { color: #777; font-size: .7rem; text-align: center; padding: 0 .5rem; }
    .figure--broken { opacity: .85; }

    figcaption { display: flex; align-items: center; gap: .4rem; padding: .5rem .6rem; }
    .fig-name { flex: 1; min-width: 0; overflow: hidden; color: #aaa; font-size: .72rem; text-overflow: ellipsis; white-space: nowrap; }
    .fig-dl { display: inline-flex; color: #999; transition: color .15s ease; }
    .fig-dl:hover { color: #e91e8c; }
    .fig-dl .material-icons { font-size: 1.05rem; }

    .lightbox {
      position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center;
      padding: 1rem; background: rgba(0, 0, 0, .88);
    }
    .lightbox__panel {
      display: flex; flex-direction: column; width: min(72rem, 100%); max-height: 100%;
      border: 1px solid #333; border-radius: 12px; background: #121212; overflow: hidden;
    }
    .lightbox__header { display: flex; align-items: center; gap: .75rem; padding: .65rem .9rem; border-bottom: 1px solid #2a2a2a; }
    .lightbox__name { flex: 1; min-width: 0; overflow: hidden; color: #fff; font-size: .82rem; text-overflow: ellipsis; white-space: nowrap; }
    .lightbox__count { color: #888; font-size: .75rem; }
    .lightbox__actions { display: flex; align-items: center; gap: .5rem; }
    .lightbox__btn {
      display: inline-flex; align-items: center; gap: .35rem; padding: .35rem .65rem;
      border: 1px solid #3a3a3a; border-radius: 8px; background: #1c1c1c; color: #eee;
      font-size: .75rem; text-decoration: none; cursor: pointer; transition: border-color .15s ease;
    }
    .lightbox__btn:hover { border-color: #e91e8c; }
    .lightbox__btn .material-icons { font-size: 1rem; }
    .lightbox__btn--icon { padding: .35rem; }
    .lightbox__stage { position: relative; display: flex; align-items: center; justify-content: center; flex: 1; min-height: 0; padding: 1rem; }
    .lightbox__img { max-width: 100%; max-height: min(70vh, 46rem); object-fit: contain; border-radius: 8px; }
    .lightbox__nav {
      position: absolute; top: 50%; transform: translateY(-50%); display: flex; align-items: center; justify-content: center;
      width: 2.4rem; height: 2.4rem; border: 1px solid #3a3a3a; border-radius: 50%; background: rgba(0, 0, 0, .6); color: #fff; cursor: pointer;
      transition: border-color .15s ease;
    }
    .lightbox__nav:hover { border-color: #e91e8c; }
    .lightbox__nav--prev { left: .75rem; }
    .lightbox__nav--next { right: .75rem; }

    @media (max-width: 40rem) {
      .gallery { grid-template-columns: repeat(auto-fill, minmax(7.5rem, 1fr)); }
      .btn-dl-all { font-size: .72rem; }
      .lightbox__nav--prev { left: .35rem; }
      .lightbox__nav--next { right: .35rem; }
    }
  `]
})
export class MaintenanceAttachmentsComponent implements OnInit, OnDestroy {
  private readonly api = inject(MaintenanceApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly document = inject(DOCUMENT);
  readonly maintenanceId = input.required<string>();
  protected readonly views = signal<readonly AttachmentView[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly lightboxIndex = signal<number | null>(null);

  protected readonly imageViews = computed(() =>
    this.views().filter((view) => view.isImage && !view.failed));
  protected readonly downloadableCount = computed(() =>
    this.views().filter((view) => !view.failed).length);
  protected readonly current = computed(() => {
    const index = this.lightboxIndex();
    return index === null ? null : this.imageViews()[index] ?? null;
  });
  protected readonly lightboxPosition = computed(() =>
    Math.max(0, this.lightboxIndex() ?? 0));

  ngOnInit(): void {
    this.api.getAttachments(this.maintenanceId()).pipe(
      switchMap((attachments) => attachments.length === 0
        ? of([] as AttachmentView[])
        : forkJoin(attachments.map((attachment) => this.api
            .getAttachmentContent(this.maintenanceId(), attachment.id)
            .pipe(map((blob) => this.toView(attachment, blob)))))
      ),
      catchError(() => {
        this.error.set('Les photos ne peuvent pas être chargées pour le moment.');
        return of([] as AttachmentView[]);
      }),
      finalize(() => this.loading.set(false))
    ).subscribe((views) => this.views.set(views));
  }

  ngOnDestroy(): void {
    this.closeLightbox();
    this.views().forEach((view) => URL.revokeObjectURL(view.blobUrl));
  }

  protected openLightbox(attachmentId: string): void {
    const index = this.imageViews().findIndex((view) => view.attachment.id === attachmentId);
    if (index >= 0) {
      this.lightboxIndex.set(index);
      this.document.body.style.overflow = 'hidden';
    }
  }

  protected closeLightbox(): void {
    this.lightboxIndex.set(null);
    this.document.body.style.overflow = '';
  }

  protected previousImage(): void {
    const index = this.lightboxIndex();
    if (index !== null) {
      this.lightboxIndex.set((index - 1 + this.imageViews().length) % this.imageViews().length);
    }
  }

  protected nextImage(): void {
    const index = this.lightboxIndex();
    if (index !== null) {
      this.lightboxIndex.set((index + 1) % this.imageViews().length);
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.closeLightbox();
  }

  @HostListener('document:keydown.arrowleft')
  protected onArrowLeft(): void {
    if (this.lightboxIndex() !== null) {
      this.previousImage();
    }
  }

  @HostListener('document:keydown.arrowright')
  protected onArrowRight(): void {
    if (this.lightboxIndex() !== null) {
      this.nextImage();
    }
  }

  protected markFailed(attachmentId: string): void {
    this.views.update((views) => views.map((view) =>
      view.attachment.id === attachmentId && view.isImage
        ? { ...view, failed: true }
        : view));
  }

  protected downloadAll(): void {
    this.views().forEach((view) => {
      if (view.failed) return;
      const link = this.document.createElement('a');
      link.href = view.safeUrl as string;
      link.download = view.attachment.filename;
      this.document.body.appendChild(link);
      link.click();
      link.remove();
    });
  }

  private toView(attachment: MaintenanceAttachmentResponse, blob: Blob): AttachmentView {
    const blobUrl = URL.createObjectURL(blob);
    const isImage = IMAGE_MIME_PREFIX === blob.type.slice(0, IMAGE_MIME_PREFIX.length)
      && IMAGE_EXTENSIONS.includes(extractExtension(attachment.filename));
    const icon = resolveIcon(attachment.filename);
    return {
      attachment,
      blobUrl,
      safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(blobUrl),
      isImage,
      failed: false,
      icon,
    };
  }
}
