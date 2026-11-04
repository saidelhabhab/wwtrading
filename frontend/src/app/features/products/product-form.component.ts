import { HttpErrorResponse, HttpEventType, HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ProductApiService } from './product-api.service';
import { ImagePreview, ProductImageService } from './product-image.service';
import { ProductImageGridComponent } from './product-image-grid.component';
import { ProductImageUploadComponent } from './product-image-upload.component';
import {
  CreateProductRequest,
  MACHINE_CATEGORIES,
  MACHINE_CATEGORY_LABELS,
  MachineCategory,
  PRODUCT_COLOR_MODES,
  PRODUCT_COLOR_MODE_LABELS,
  ProductColorMode,
  PRODUCT_TYPES,
  PRODUCT_TYPE_LABELS,
  ProductType,
  ProductImage,
  UpdateProductRequest
} from './product.models';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
@Component({
  selector: 'wwt-product-form',
  standalone: true,
  imports: [ReactiveFormsModule, ProductImageUploadComponent, ProductImageGridComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="product-form-page" aria-labelledby="product-form-title">
      <a class="back-link" href="/products">← Retour au catalogue</a>

      @if (loading()) {
        <div class="state" role="status">Chargement du produit…</div>
      } @else if (notFound()) {
        <section class="state error" role="alert">
          <h1 id="product-form-title">Produit introuvable</h1>
          <p>Ce produit n’existe pas ou n’est plus disponible.</p>
        </section>
      } @else if (loadError()) {
        <section class="state error" role="alert">
          <h1 id="product-form-title">Chargement impossible</h1>
          <p>{{ loadError() }}</p>
          @if (editMode) { <button type="button" (click)="loadProduct()">Réessayer</button> }
        </section>
      } @else {
        <header>
          <p class="eyebrow">Administration du catalogue</p>
          <h1 id="product-form-title">{{ editMode ? 'Modifier le produit' : 'Créer un produit' }}</h1>
          <p>{{ editMode ? 'Mettez à jour les informations commerciales du produit.' : 'Ajoutez un nouveau produit au catalogue WWTRADING.' }}</p>
        </header>

        @if (successMessage()) {
          <div class="state success" role="status" aria-live="polite">{{ successMessage() }}</div>
        }
        @if (submitError()) {
          <div class="state error" role="alert" aria-live="assertive">{{ submitError() }}</div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <section class="form-section" aria-labelledby="general-information-title">
            <div class="section-heading">
              <span>01</span>
              <div>
                <h2 id="general-information-title">Informations générales</h2>
                <p>Les champs marqués d’un astérisque sont obligatoires.</p>
              </div>
            </div>

            <div class="form-grid">
              <div class="field full-width">
                <label for="product-type">Type de produit <span aria-hidden="true">*</span></label>
                <select id="product-type" formControlName="type" aria-describedby="product-type-help" (change)="onTypeChange()">
                  @for (type of productTypes; track type.value) {
                    <option [value]="type.value">{{ type.label }}</option>
                  }
                </select>
                <p id="product-type-help" class="field-help">La valeur métier exacte sera transmise au catalogue.</p>
              </div>

              <div class="field">
                <label for="product-name">Nom du produit <span aria-hidden="true">*</span></label>
                <input id="product-name" type="text" formControlName="name" maxlength="200" autocomplete="off">
                @if (showError('name')) { <p class="field-error">Le nom est obligatoire (200 caractères maximum).</p> }
              </div>

              <div class="field">
                <label for="product-reference">Référence <span aria-hidden="true">*</span></label>
                <input id="product-reference" type="text" formControlName="reference" maxlength="64" autocomplete="off">
                @if (showError('reference')) { <p class="field-error">La référence est obligatoire (64 caractères maximum).</p> }
              </div>
            </div>
          </section>

          <section class="form-section" aria-labelledby="description-title">
            <div class="section-heading">
              <span>02</span>
              <div><h2 id="description-title">Description</h2><p>Présentez clairement le produit aux clients professionnels.</p></div>
            </div>
            <div class="field">
              <label for="product-description">Description du produit</label>
              <textarea id="product-description" formControlName="description" maxlength="10000" rows="7"></textarea>
              @if (showError('description')) { <p class="field-error">La description ne peut pas dépasser 10 000 caractères.</p> }
            </div>
          </section>

          <section class="form-section" aria-labelledby="identification-title">
            <div class="section-heading">
              <span>03</span>
              <div><h2 id="identification-title">Identification</h2><p>Renseignez les informations constructeur disponibles.</p></div>
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="product-brand">Marque</label>
                <input id="product-brand" type="text" formControlName="brand" maxlength="100" autocomplete="organization">
                @if (showError('brand')) { <p class="field-error">La marque ne peut pas dépasser 100 caractères.</p> }
              </div>
              <div class="field">
                <label for="product-model">Modèle</label>
                <input id="product-model" type="text" formControlName="model" maxlength="150" autocomplete="off">
                @if (showError('model')) { <p class="field-error">Le modèle ne peut pas dépasser 150 caractères.</p> }
              </div>
            </div>
          </section>

          @if (isPrinter()) {
            <section class="form-section" aria-labelledby="machine-title">
              <div class="section-heading">
                <span>04</span>
                <div><h2 id="machine-title">Machine</h2><p>La catégorie machine s’applique uniquement aux imprimantes.</p></div>
              </div>
              <div class="field">
                <label for="machine-category">Catégorie de machine</label>
                <select id="machine-category" formControlName="machineCategory">
                  <option [ngValue]="null">Non renseignée</option>
                  @for (category of machineCategories; track category.value) { <option [ngValue]="category.value">{{ category.label }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="color-mode">Mode couleur</label>
                <select id="color-mode" formControlName="colorMode">
                  <option [ngValue]="null">Non renseigné</option>
                  @for (colorMode of colorModes; track colorMode.value) { <option [ngValue]="colorMode.value">{{ colorMode.label }}</option> }
                </select>
                <p class="field-help">Le mode couleur s’applique uniquement aux imprimantes.</p>
              </div>
            </section>
          }

          <section class="form-section" aria-labelledby="warranty-title">
            <div class="section-heading">
              <span>05</span>
              <div><h2 id="warranty-title">Garantie</h2><p>Ces valeurs sont facultatives et doivent rester positives ou nulles.</p></div>
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="warranty-months">Garantie (mois)</label>
                <input id="warranty-months" type="number" formControlName="warrantyMonths" min="0" step="1" inputmode="numeric">
                @if (showError('warrantyMonths')) { <p class="field-error">Le nombre de mois doit être supérieur ou égal à 0.</p> }
              </div>
              <div class="field">
                <label for="warranty-copies">Garantie (copies)</label>
                <input id="warranty-copies" type="number" formControlName="warrantyCopies" min="0" step="1" inputmode="numeric">
                @if (showError('warrantyCopies')) { <p class="field-error">Le nombre de copies doit être supérieur ou égal à 0.</p> }
              </div>
            </div>
          </section>

          <section class="form-section" aria-labelledby="pricing-title">
            <div class="section-heading">
              <span>06</span>
              <div><h2 id="pricing-title">Tarification</h2><p>Le prix est enregistré en dirhams marocains.</p></div>
            </div>
            <div class="field price-field">
              <label for="product-price">Prix (DH) <span aria-hidden="true">*</span></label>
              <input id="product-price" type="number" formControlName="price" min="0" step="0.01" inputmode="decimal">
              @if (showError('price')) { <p class="field-error">Le prix est obligatoire et doit être supérieur ou égal à 0.</p> }
            </div>
            <div class="field">
              <label for="available-quantity">Stock disponible</label>
              <input id="available-quantity" type="number" [value]="initialStockAvailability()" disabled aria-readonly="true">
              <p class="field-hint">Le stock est géré par le module Stock (enregistrements IN / OUT / ajustements). Il ne se modifie pas ici.</p>
            </div>
          </section>

          <section class="form-section" aria-labelledby="product-images-title">
            <div class="section-heading">
              <span>07</span>
              <div>
                <h2 id="product-images-title">Images du produit</h2>
                <p>Ajoutez plusieurs photos et définissez l’image principale à partir de la galerie.</p>
              </div>
            </div>

            @if (imageLoadError()) {
              <div class="state error" role="alert">{{ imageLoadError() }}</div>
            }

            @if (!editMode) {
              <wwt-product-image-upload
                [previews]="selectedPreviews()"
                (filesSelected)="handleSelectedFiles($event)"
                (previewsChanged)="handlePreviewChanges($event)"
              ></wwt-product-image-upload>
            }

            @if (editMode) {
              <wwt-product-image-upload
                [previews]="selectedPreviews()"
                (filesSelected)="handleSelectedFiles($event)"
                (previewsChanged)="handlePreviewChanges($event)"
              ></wwt-product-image-upload>
              <div class="gallery-wrapper">
                <wwt-product-image-grid
                  [productId]="productId ?? ''"
                  [images]="existingImages()"
                  (imageDeleted)="refreshImages()"
                  (imageMainChanged)="refreshImages()"
                  (imagesReordered)="reorderExistingImages($event)"
                ></wwt-product-image-grid>
              </div>
            }

            @if (!editMode && selectedPreviews().length > 0) {
              <div class="upload-status" aria-live="polite">
                <strong>{{ selectedPreviews().length }}</strong> image(s) prête(s) pour l'upload après création.
              </div>
            }
          </section>

          <div class="actions">
            <a href="/products">Annuler</a>
            <button type="submit" [disabled]="form.invalid || submitting()">
              {{ submitting() ? 'Enregistrement…' : (editMode ? 'Enregistrer' : 'Créer le produit') }}
            </button>
          </div>
        </form>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .product-form-page { max-width: 1040px; margin: 0 auto; padding: clamp(2.5rem, 7vw, 5.5rem) clamp(1rem, 5vw, 3rem); }
    .back-link { display: inline-flex; margin-bottom: 2rem; color: #8f123f; font-weight: 750; text-underline-offset: .2em; }
    header { margin-bottom: 2rem; }
    .eyebrow { margin: 0 0 .55rem; color: #af0f50; font-size: .78rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    h1 { margin: 0; color: #1a1a18; font-size: clamp(2rem, 5vw, 3.5rem); }
    header p:last-child { color: #66635f; line-height: 1.6; }
    form { display: grid; gap: 1.25rem; }
    .form-section { padding: clamp(1.25rem, 4vw, 2rem); border: 1px solid rgb(26 26 24 / 12%); border-radius: .75rem; background: #fff; box-shadow: 0 1rem 2.5rem rgb(26 26 24 / 6%); }
    .form-section:first-child { border-top: 4px solid #af0f50; }
    .section-heading { display: flex; gap: .85rem; align-items: flex-start; margin-bottom: 1.4rem; }
    .section-heading > span { display: grid; width: 2rem; height: 2rem; place-items: center; border-radius: 50%; background: #1a1a18; color: #fff; font-size: .75rem; font-weight: 800; }
    h2 { margin: 0; color: #1a1a18; font-size: 1.12rem; }
    .section-heading p, .field-help { margin: .25rem 0 0; color: #6d6965; font-size: .88rem; line-height: 1.5; }
    .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.15rem; }
    .field { display: grid; gap: .5rem; }
    .full-width { grid-column: 1 / -1; }
    .price-field { max-width: 24rem; }
    label { color: #292825; font-weight: 750; }
    label span { color: #af0f50; }
    input, textarea, select { width: 100%; box-sizing: border-box; padding: .85rem .95rem; border: 1px solid #aaa6a1; border-radius: .4rem; background: #fff; color: #1a1a18; font: inherit; }
    textarea { resize: vertical; min-height: 9rem; }
    input:focus, textarea:focus, select:focus { outline: 3px solid rgb(175 15 80 / 20%); border-color: #af0f50; }
    .field-error { margin: 0; color: #9a123f; font-size: .88rem; }
    .actions { display: flex; justify-content: flex-end; align-items: center; flex-wrap: wrap; gap: .8rem; margin-top: 1.2rem; }
    .actions a, button { padding: .72rem 1.05rem; border: 1px solid #af0f50; border-radius: 999px; font: inherit; font-weight: 750; text-decoration: none; }
    .actions a { color: #8f123f; }
    button { background: #af0f50; color: #fff; cursor: pointer; }
    button:disabled { cursor: not-allowed; opacity: .55; }
    .state { padding: 1.25rem; border: 1px solid rgb(26 26 24 / 12%); background: #fff; }
    .state.error { border-color: #af0f50; color: #74102f; }
    .state.success { border-color: #287a50; color: #185c3a; background: #f1fbf6; }
    .state button { background: #fff; color: #8f123f; }
    .gallery-wrapper { margin-top: 1.5rem; }
    .upload-status { margin-top: 1rem; padding: .75rem 1rem; border-radius: .5rem; background: #f4f3f2; color: #1a1a18; font-size: .88rem; }
    @media (max-width: 640px) {
      .form-grid { grid-template-columns: 1fr; }
      .full-width { grid-column: auto; }
      .actions { align-items: stretch; flex-direction: column-reverse; }
      .actions a, .actions button { text-align: center; }
    }
  `]
})
export class ProductFormComponent implements OnInit {
  private readonly productApi = inject(ProductApiService);
  private readonly imageService = inject(ProductImageService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected productId: string | null = null;

  protected readonly editMode = this.route.snapshot.data['mode'] === 'edit';
  protected readonly loading = signal(this.editMode);
  protected readonly submitting = signal(false);
  protected readonly notFound = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitError = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly imageLoadError = signal<string | null>(null);
  protected readonly selectedPreviews = signal<readonly ImagePreview[]>([]);
  protected readonly existingImages = signal<readonly ProductImage[]>([]);
  protected readonly pendingUploadCount = signal(0);
  protected readonly uploadProgress = signal<Record<string, number>>({});

  protected readonly form = new FormGroup({
    reference: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(64)]
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(10000)]
    }),
    price: new FormControl<number | null>(null, [Validators.required, Validators.min(0)]),
    type: new FormControl<ProductType>('PRINTER', { nonNullable: true, validators: [Validators.required] }),
    brand: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
    model: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(150)] }),
    machineCategory: new FormControl<MachineCategory | null>(null),
    colorMode: new FormControl<ProductColorMode | null>(null),
    warrantyMonths: new FormControl<number | null>(null, [Validators.min(0)]),
    warrantyCopies: new FormControl<number | null>(null, [Validators.min(0)])
  });

  /** Read-only stock availability shown for context; managed in the Stock module. */
  protected readonly initialStockAvailability = signal<number | null>(null);
  protected readonly productTypes = PRODUCT_TYPES.map((value) => ({
    value,
    label: PRODUCT_TYPE_LABELS[value]
  }));
  protected readonly machineCategories = MACHINE_CATEGORIES.map((value) => ({
    value,
    label: MACHINE_CATEGORY_LABELS[value]
  }));
  protected readonly colorModes = PRODUCT_COLOR_MODES.map((value) => ({
    value,
    label: PRODUCT_COLOR_MODE_LABELS[value]
  }));

  constructor() {
    if (this.editMode) this.form.disable();
  }

  ngOnInit(): void {
    if (this.editMode) this.loadProduct();
  }

  protected loadProduct(): void {
    this.productId = this.route.snapshot.paramMap.get('id');
    this.notFound.set(false);
    this.loadError.set(null);
    this.imageLoadError.set(null);
    if (!this.productId || !UUID_PATTERN.test(this.productId)) {
      this.loadError.set('La référence du produit est invalide.');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.form.disable();
    this.productApi.getById(this.productId).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (product) => {
        this.initialStockAvailability.set(product.availableQuantity ?? null);
        this.form.setValue({
          reference: product.reference,
          name: product.name,
          description: product.description ?? '',
          price: product.price,
          type: product.type,
          brand: product.brand ?? '',
          model: product.model ?? '',
          machineCategory: product.machineCategory,
          colorMode: product.colorMode,
          warrantyMonths: product.warrantyMonths,
          warrantyCopies: product.warrantyCopies
        });
        this.form.enable();
        this.refreshImages();
      },
      error: (failure: unknown) => {
        if (failure instanceof HttpErrorResponse && failure.status === 404) {
          this.notFound.set(true);
        } else {
          this.loadError.set('Le produit ne peut pas être chargé pour le moment.');
        }
      }
    });
  }

  protected refreshImages(): void {
    if (!this.productId || !this.editMode) return;
    this.productApi.getImages(this.productId).subscribe({
      next: (images) => this.existingImages.set(images),
      error: () => this.imageLoadError.set('Impossible de charger les images. Réessayez.')
    });
  }

  protected handleSelectedFiles(files: File[]): void {
    const previews = this.imageService.validateFiles(files);
    const merged = [...this.selectedPreviews(), ...previews];
    this.selectedPreviews.set(merged);
    this.pendingUploadCount.set(merged.filter(item => !item.error).length);
  }

  protected handlePreviewChanges(previews: readonly ImagePreview[]): void {
    this.selectedPreviews.set(previews);
    this.pendingUploadCount.set(previews.filter(item => !item.error).length);
  }

  protected reorderExistingImages(imageIds: readonly string[]): void {
    if (!this.productId) return;
    this.imageService.reorderImages(this.productId, imageIds).subscribe({
      next: () => this.refreshImages(),
      error: () => this.imageLoadError.set('Impossible de réorganiser les images.')
    });
  }

  protected isCreatingWithImages(): boolean {
    return !this.editMode && this.selectedPreviews().some(item => !item.error);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting() || (this.editMode && !this.productId)) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: CreateProductRequest | UpdateProductRequest = {
      reference: value.reference.trim(),
      name: value.name.trim(),
      description: value.description.trim() || null,
      price: value.price as number,
      type: value.type,
      brand: value.brand.trim() || null,
      model: value.model.trim() || null,
      machineCategory: value.type === 'PRINTER' ? value.machineCategory : null,
      colorMode: value.type === 'PRINTER' ? value.colorMode : null,
      warrantyMonths: value.warrantyMonths,
      warrantyCopies: value.warrantyCopies
    };

    this.submitting.set(true);
    this.submitError.set(null);
    this.successMessage.set(null);
    this.form.disable();

    const operation = this.editMode ? this.productApi.update(this.productId as string, request) : this.productApi.create(request);

    operation.pipe(
      finalize(() => {
        this.submitting.set(false);
        this.form.enable();
      })
    ).subscribe({
      next: (product) => {
        if (this.editMode) {
          this.productId = product.id;
          this.successMessage.set('Produit mis à jour avec succès. Import des images…');
          this.uploadSelectedImages(product.id);
          return;
        }

        this.productId = product.id;
        this.successMessage.set('Produit créé avec succès. Import des images…');
        this.uploadSelectedImages(product.id);
      },
      error: (failure: unknown) => {
        this.submitError.set(this.submissionErrorMessage(failure));
      }
    });
  }

  private uploadSelectedImages(productId: string): void {
    const validPreviews = this.selectedPreviews().filter(item => !item.error);
    if (validPreviews.length === 0) {
      void this.router.navigate(['/products', productId]);
      return;
    }

    const uploadNext = (index: number): void => {
      const preview = validPreviews[index];
      const progress = { ...this.uploadProgress(), [preview.file.name]: 0 };
      this.uploadProgress.set(progress);

      this.imageService.uploadImageWithProgress(productId, preview.file).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            const total = event.total;
            this.uploadProgress.update((current) => ({
              ...current,
              [preview.file.name]: Math.round((event.loaded / total) * 100)
            }));
          }

          if (event instanceof HttpResponse) {
            this.uploadProgress.update((current) => ({ ...current, [preview.file.name]: 100 }));
            if (index + 1 < validPreviews.length) {
              uploadNext(index + 1);
            } else {
              setTimeout(() => void this.router.navigate(['/products', productId]), 250);
            }
          }
        },
        error: (failure: unknown) => {
          this.uploadProgress.update((current) => ({ ...current, [preview.file.name]: 0 }));
          this.submitError.set(this.imageUploadErrorMessage(failure, preview.file.name));
        }
      });
    };

    uploadNext(0);
  }

  protected showError(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  protected isPrinter(): boolean { return this.form.controls.type.value === 'PRINTER'; }

  protected onTypeChange(): void {
    if (!this.isPrinter()) {
      // Machine category and color mode only exist for printers; the backend rejects
      // them for any other type, so both are cleared when the type changes away.
      this.form.controls.machineCategory.setValue(null);
      this.form.controls.colorMode.setValue(null);
    }
  }

  private submissionErrorMessage(failure: unknown): string {
    if (!(failure instanceof HttpErrorResponse)) return 'Le produit ne peut pas être enregistré pour le moment.';
    if (failure.status === 0) return 'Le service Produit est momentanément inaccessible. Réessayez plus tard.';
    if (failure.status === 400) return 'Certaines informations sont invalides. Vérifiez le formulaire.';
    if (failure.status === 401) return 'Votre session a expiré. Reconnectez-vous avant de réessayer.';
    if (failure.status === 403) return 'Vous ne disposez pas des droits nécessaires pour créer ce produit.';
    if (failure.status === 404) return 'Le service Produit demandé est introuvable.';
    if (failure.status === 409) return 'Un produit utilise déjà cette référence.';
    return 'Le produit ne peut pas être enregistré pour le moment.';
  }

  private imageUploadErrorMessage(failure: unknown, filename: string): string {
    if (!(failure instanceof HttpErrorResponse)) {
      return `L’image « ${filename} » n’a pas pu être importée. Vérifiez le fichier et réessayez.`;
    }
    const backendDetail = typeof failure.error?.detail === 'string' ? failure.error.detail : null;
    if (backendDetail) return `L’image « ${filename} » n’a pas pu être importée : ${backendDetail}`;
    if (failure.status === 0) return 'Le service Produit est momentanément inaccessible pendant l’import de l’image.';
    if (failure.status === 400) return `L’image « ${filename} » est invalide ou vide.`;
    if (failure.status === 401) return 'Votre session a expiré. Reconnectez-vous avant d’importer une image.';
    if (failure.status === 403) return 'Vous ne disposez pas des droits nécessaires pour importer cette image.';
    if (failure.status === 404) return 'Le produit à modifier est introuvable pendant l’import de l’image.';
    if (failure.status === 413) return `L’image « ${filename} » dépasse la taille maximale autorisée.`;
    if (failure.status === 415) return `Le format de l’image « ${filename} » n’est pas accepté.`;
    return `L’image « ${filename} » n’a pas pu être importée. Réessayez.`;
  }
}
