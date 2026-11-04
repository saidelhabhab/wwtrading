import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, map, of, switchMap, tap } from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { CreateMaintenanceRequest, MaintenancePriority } from '../../maintenance/maintenance.models';

@Component({
  selector: 'wwt-client-maintenance-new',
  standalone: true,
  imports: [FormsModule, RouterLink, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Nouvelle demande de maintenance">
      <wwt-page-header
        title="Nouvelle demande de maintenance"
        subtitle="Décrivez votre équipement et le problème rencontré"
        breadcrumb="Espace client / Mes interventions / Nouvelle demande">
      </wwt-page-header>

      <main class="new-maint-page" aria-label="Formulaire de demande de maintenance">

        @if (successMsg()) {
          <div class="alert alert--success" role="status">
            <span class="material-icons" aria-hidden="true">check_circle</span>
            {{ successMsg() }}
          </div>
        }

        @if (errorMsg()) {
          <div class="alert alert--error" role="alert">
            <span class="material-icons" aria-hidden="true">error_outline</span>
            {{ errorMsg() }}
          </div>
        }

        <section class="form-card" aria-labelledby="form-title">
          <header class="form-card__header">
            <span class="eyebrow">Espace client</span>
            <h2 id="form-title">Déclaration d'une demande de maintenance</h2>
          </header>

          <form class="maint-form" (ngSubmit)="submit()" #f="ngForm" novalidate>

            <!-- Sujet -->
            <div class="field">
              <label for="subject" class="label">Sujet <span class="required" aria-hidden="true">*</span></label>
              <input
                id="subject"
                name="subject"
                type="text"
                class="input"
                [(ngModel)]="subject"
                required
                maxlength="200"
                placeholder="Ex. : Panne Konica Minolta C258"
                [disabled]="submitting()"
                aria-required="true"/>
            </div>

            <!-- Description -->
            <div class="field">
              <label for="description" class="label">Description du problème <span class="required" aria-hidden="true">*</span></label>
              <textarea
                id="description"
                name="description"
                class="input textarea"
                [(ngModel)]="description"
                required
                rows="5"
                maxlength="2000"
                placeholder="Décrivez le problème en détail : symptômes, fréquence, contexte…"
                [disabled]="submitting()"
                aria-required="true"></textarea>
            </div>

            <!-- Priorité -->
            <div class="field">
              <label for="priority" class="label">Priorité</label>
              <select
                id="priority"
                name="priority"
                class="input select"
                [(ngModel)]="priority"
                [disabled]="submitting()">
                <option value="LOW">Basse</option>
                <option value="MEDIUM">Normale</option>
                <option value="HIGH">Haute</option>
                <option value="CRITICAL">Critique</option>
              </select>
            </div>

            <fieldset class="machine-fields">
              <legend>Machine concernée</legend>

            <!-- Référence équipement -->
            <div class="field">
              <label for="productReference" class="label">Marque, modèle ou référence</label>
              <input
                id="productReference"
                name="productReference"
                type="text"
                class="input"
                [(ngModel)]="productReference"
                maxlength="64"
                placeholder="Ex. : Konica Minolta — C258"
                [disabled]="submitting()"/>
            </div>

            <!-- N° de série -->
            <div class="field">
              <label for="serialNumber" class="label">Numéro de série</label>
              <input
                id="serialNumber"
                name="serialNumber"
                type="text"
                class="input"
                [(ngModel)]="serialNumber"
                maxlength="100"
                placeholder="Ex. : SN-00123456"
                [disabled]="submitting()"/>
            </div>
            </fieldset>

            <!-- Référence client -->
            <div class="field">
              <label for="customerReference" class="label">Référence / N° de bon (facultatif)</label>
              <input
                id="customerReference"
                name="clientReference"
                type="text"
                class="input"
                [(ngModel)]="clientReference"
                maxlength="100"
                placeholder="Ex. : BON-2026-001"
                [disabled]="submitting()"/>
            </div>

            <div class="field">
              <label for="maintenancePhotos" class="label">Photos du problème (facultatif)</label>
              <label class="file-picker" for="maintenancePhotos">
                <span class="material-icons" aria-hidden="true">add_photo_alternate</span>
                Ajouter des photos
              </label>
              <input
                id="maintenancePhotos"
                class="file-input"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                [disabled]="submitting()"
                (change)="selectPhotos($event)">
              <small>Formats acceptés : JPG, PNG, WEBP · Maximum : 5 photos</small>
              @if (photoError()) { <p class="field-error" role="alert">{{ photoError() }}</p> }
              @if (photos().length > 0) {
                <ul class="selected-files" aria-label="Photos sélectionnées">
                  @for (photo of photos(); track photo) { <li>{{ photo.name }}</li> }
                </ul>
              }
            </div>

            <div class="form-actions">
              <a class="btn btn--ghost" routerLink="/client/maintenance" [attr.aria-disabled]="submitting()">
                Annuler
              </a>
              <button
                type="submit"
                class="btn btn--primary"
                [disabled]="submitting() || !subject.trim() || !description.trim()">
                @if (submitting()) {
                  <span class="material-icons spin" aria-hidden="true">sync</span>
                  Envoi en cours…
                } @else {
                  <span class="material-icons" aria-hidden="true">send</span>
                  Soumettre la demande
                }
              </button>
            </div>
          </form>
        </section>

      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .new-maint-page {
      padding: 1.5rem 2rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      max-width: 720px;
    }

    .alert {
      display: flex;
      align-items: center;
      gap: .75rem;
      padding: .875rem 1.25rem;
      border-radius: 8px;
      font-size: .875rem;
    }
    .alert--success {
      background: rgba(76, 175, 80, .1);
      border: 1px solid rgba(76, 175, 80, .35);
      color: #81c784;
    }
    .alert--error {
      background: rgba(244, 67, 54, .1);
      border: 1px solid rgba(244, 67, 54, .35);
      color: #e57373;
    }

    .form-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 1.75rem;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
      max-width: var(--dashboard-max-width, 1680px);
      margin: 0 auto;
    }
    .form-card__header {
      margin-bottom: 1.75rem;
      padding-bottom: 1.15rem;
      border-bottom: 1px solid #e2e8f0;
    }
    .eyebrow {
      font-size: .72rem;
      text-transform: uppercase;
      letter-spacing: .08em;
      color: #af0f50;
      font-weight: 750;
    }
    .form-card__header h2 {
      margin: .25rem 0 0;
      font-size: 1.25rem;
      font-weight: 750;
      color: #0f172a;
    }

    .maint-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .machine-fields { display: grid; gap: 1.25rem; margin: 0; padding: 1.25rem; border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; }
    .machine-fields legend { padding: 0 .4rem; color: #0f172a; font-size: .9rem; font-weight: 700; }

    .field {
      display: flex;
      flex-direction: column;
      gap: .35rem;
    }

    .label {
      font-size: .78rem;
      font-weight: 650;
      color: #334155;
      text-transform: uppercase;
      letter-spacing: .04em;
    }
    .required { color: #af0f50; }

    .input {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      color: #0f172a;
      font-size: .9rem;
      padding: .625rem .875rem;
      transition: border-color .15s ease, box-shadow .15s ease;
      outline: none;
      font-family: inherit;
      width: 100%;
      box-sizing: border-box;
    }
    .input:focus { border-color: #af0f50; box-shadow: 0 0 0 3px rgba(175, 15, 80, 0.15); }
    .input:disabled { opacity: .55; cursor: not-allowed; background: #f1f5f9; }
    .textarea { resize: vertical; min-height: 100px; }
    .select { appearance: none; cursor: pointer; }
    .file-input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    .file-picker { display: inline-flex; width: fit-content; align-items: center; gap: .5rem; padding: .65rem 1rem; border: 1px dashed #cbd5e1; border-radius: 8px; color: #475569; background: #f8fafc; cursor: pointer; font-size: 0.85rem; font-weight: 600; transition: all 0.15s ease; }
    .file-picker:hover { border-color: #af0f50; color: #af0f50; background: #fdf2f6; }
    .field small { color: #64748b; font-size: 0.78rem; }
    .field-error { margin: 0; color: #ef4444; font-size: .8rem; }
    .selected-files { display: grid; gap: .25rem; margin: 0; padding-left: 1.2rem; color: #475569; font-size: .8rem; }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: .75rem;
      margin-top: .5rem;
      padding-top: 1.15rem;
      border-top: 1px solid #e2e8f0;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: .5rem;
      padding: .6rem 1.35rem;
      border-radius: 8px;
      font-size: .875rem;
      font-weight: 650;
      cursor: pointer;
      border: 1px solid transparent;
      text-decoration: none;
      transition: all .18s ease;
    }
    .btn--primary { background: #af0f50; color: #fff; box-shadow: 0 1px 2px rgba(175, 15, 80, 0.25); }
    .btn--primary:hover:not(:disabled) { background: #930b42; transform: translateY(-1px); }
    .btn--primary:disabled { opacity: .5; cursor: not-allowed; }
    .btn--ghost { background: #ffffff; color: #334155; border: 1px solid #e2e8f0; }
    .btn--ghost:hover { border-color: #cbd5e1; background: #f8fafc; }

    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class ClientMaintenanceNewComponent {
  private readonly api = inject(MaintenanceApiService);
  private readonly router = inject(Router);

  protected subject = '';
  protected description = '';
  protected priority: MaintenancePriority = 'MEDIUM';
  protected productReference = '';
  protected serialNumber = '';
  protected clientReference = '';
  protected readonly photos = signal<readonly File[]>([]);
  protected readonly photoError = signal<string | null>(null);

  protected readonly submitting = signal(false);
  protected readonly errorMsg = signal<string | null>(null);
  protected readonly successMsg = signal<string | null>(null);

  protected selectPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.photoError.set(null);
    if (files.length > 5) {
      this.photoError.set('Vous pouvez joindre au maximum 5 photos.');
      this.photos.set([]);
      input.value = '';
      return;
    }
    const allowed = new Set(['image/jpeg', 'image/png', 'image/webp']);
    if (files.some((file) => !allowed.has(file.type))) {
      this.photoError.set('Seuls les formats JPG, PNG et WEBP sont acceptés.');
      this.photos.set([]);
      input.value = '';
      return;
    }
    this.photos.set(files);
  }

  protected submit(): void {
    if (!this.subject.trim() || !this.description.trim() || this.submitting()) return;

    this.submitting.set(true);
    this.errorMsg.set(null);
    this.successMsg.set(null);

    const request: CreateMaintenanceRequest = {
      subject: this.subject.trim(),
      description: this.description.trim(),
      priority: this.priority,
      productReference: this.productReference.trim() || null,
      serialNumber: this.serialNumber.trim() || null,
      clientReference: this.clientReference.trim() || null,
    };

    let createdId: string | null = null;
    this.api.create(request).pipe(
      tap((created) => { createdId = created.id; }),
      switchMap((created) => this.photos().length === 0
        ? of(created)
        : this.api.uploadAttachments(created.id, this.photos()).pipe(map(() => created))),
      finalize(() => this.submitting.set(false))
    ).subscribe({
      next: () => {
        this.successMsg.set('Votre demande a bien été soumise. Redirection…');
        setTimeout(() => this.router.navigate(['/client/maintenance']), 1500);
      },
      error: (err) => {
        this.errorMsg.set(createdId
          ? 'La demande a été créée, mais les photos n’ont pas pu être ajoutées.'
          : err?.error?.detail ?? 'Impossible de soumettre la demande. Veuillez réessayer.');
      }
    });
  }
}
