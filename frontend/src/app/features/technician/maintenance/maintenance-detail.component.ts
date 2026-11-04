import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { MaintenanceAttachmentsComponent } from '../../maintenance/maintenance-attachments.component';
import {
  MaintenancePartRequestResponse,
  MaintenanceResponse,
  PART_REQUEST_STATUS_LABELS,
  PRIORITY_LABELS,
  STATUS_LABELS,
} from '../../maintenance/maintenance.models';

@Component({
  selector: 'wwt-technician-maintenance-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, FormsModule, DashboardLayoutComponent, PageHeaderComponent, MaintenanceAttachmentsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Détail de l'intervention">
      <wwt-page-header
        title="Détail de l'intervention"
        breadcrumb="Dashboard / Mes interventions / Détail">
      </wwt-page-header>

      <main class="detail-page" aria-label="Détail de l'intervention">

        @if (loading()) {
          <div class="state-card state-card--loading" role="status" aria-live="polite">
            <span class="material-icons spin" aria-hidden="true">sync</span>
            <span>Chargement…</span>
          </div>
        } @else if (error()) {
          <div class="state-card state-card--error" role="alert">
            <span class="material-icons" aria-hidden="true">error_outline</span>
            <div>
              <h2>Erreur</h2>
              <p>{{ error() }}</p>
              <a routerLink="/technician/maintenance" class="btn btn--ghost btn--sm">← Retour</a>
            </div>
          </div>
        } @else {
          @if (request(); as req) {
          <div class="detail-grid">

            <!-- Status header banner -->
            <div class="status-banner">
              <div class="status-banner__left">
                <span class="status-ref">{{ req.id.slice(0, 8).toUpperCase() }}</span>
                <h1 class="status-title">{{ req.subject }}</h1>
              </div>
              <div class="status-banner__right">
                <span class="badge badge--priority badge--{{ req.priority.toLowerCase() }}">
                  {{ priorityLabel(req.priority) }}
                </span>
                <span class="badge badge--status badge--{{ req.status.toLowerCase() }}">
                  {{ statusLabel(req.status) }}
                </span>
              </div>
            </div>

            <!-- T2 Action Card: Accept / Reject (when ASSIGNED) -->
            @if (req.status === 'ASSIGNED') {
              <section class="detail-card detail-card--wide detail-card--action" aria-labelledby="sec-t2-decision">
                <h2 id="sec-t2-decision" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">how_to_reg</span>
                  Décision d'assignation
                </h2>
                <p class="decision-prompt">
                  Le manager vous a assigné cette intervention. Veuillez accepter ou refuser l'assignation.
                </p>

                @if (actionError()) {
                  <div class="alert alert--error" role="alert">
                    <span class="material-icons" aria-hidden="true">error_outline</span>
                    {{ actionError() }}
                  </div>
                }

                <div class="decision-actions">
                  <button
                    type="button"
                    class="btn btn--accept"
                    [disabled]="actionPending()"
                    (click)="acceptRequest()">
                    @if (actionPending()) {
                      <span class="material-icons spin" aria-hidden="true">sync</span>
                    } @else {
                      <span class="material-icons" aria-hidden="true">check_circle</span>
                    }
                    Accepter l'assignation
                  </button>

                  <button
                    type="button"
                    class="btn btn--reject"
                    [disabled]="actionPending()"
                    (click)="rejectRequest()">
                    @if (actionPending()) {
                      <span class="material-icons spin" aria-hidden="true">sync</span>
                    } @else {
                      <span class="material-icons" aria-hidden="true">cancel</span>
                    }
                    Refuser l'assignation
                  </button>
                </div>
              </section>
            }

            <!-- T3 Action Card: Start Intervention (when ACCEPTED) -->
            @if (req.status === 'ACCEPTED') {
              <section class="detail-card detail-card--wide detail-card--accepted" aria-labelledby="sec-start-intervention">
                <h2 id="sec-start-intervention" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">play_arrow</span>
                  Démarrer l'intervention
                </h2>
                <p class="decision-prompt">
                  Vous avez accepté cette demande
                  @if (req.acceptedAt) {
                    le <strong>{{ req.acceptedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                  }
                  Cliquez ci-dessous lorsque vous commencez les travaux sur l'équipement.
                </p>

                @if (actionError()) {
                  <div class="alert alert--error" role="alert">
                    <span class="material-icons" aria-hidden="true">error_outline</span>
                    {{ actionError() }}
                  </div>
                }

                <div class="decision-actions">
                  <button
                    type="button"
                    class="btn btn--primary"
                    [disabled]="actionPending()"
                    (click)="startIntervention()">
                    @if (actionPending()) {
                      <span class="material-icons spin" aria-hidden="true">sync</span>
                    } @else {
                      <span class="material-icons" aria-hidden="true">play_arrow</span>
                    }
                    Démarrer l'intervention
                  </button>
                </div>
              </section>
            }

            <!-- T4 WAITING_FOR_PART Banner -->
            @if (req.status === 'WAITING_FOR_PART') {
              <section class="detail-card detail-card--wide detail-card--waiting" aria-labelledby="sec-waiting-part">
                <h2 id="sec-waiting-part" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">hourglass_top</span>
                  Intervention en attente de pièce de rechange
                </h2>
                <p class="decision-prompt">
                  Cette intervention est actuellement suspendue en attente de la livraison de la pièce approuvée.
                  Lorsque la pièce est disponible, cliquez sur "Reprendre l'intervention".
                </p>

                @if (actionError()) {
                  <div class="alert alert--error" role="alert">
                    <span class="material-icons" aria-hidden="true">error_outline</span>
                    {{ actionError() }}
                  </div>
                }

                <div class="decision-actions">
                  <button
                    type="button"
                    class="btn btn--primary"
                    [disabled]="actionPending()"
                    (click)="resumeIntervention()">
                    @if (actionPending()) {
                      <span class="material-icons spin" aria-hidden="true">sync</span>
                    } @else {
                      <span class="material-icons" aria-hidden="true">play_arrow</span>
                    }
                    Reprendre l'intervention
                  </button>
                </div>
              </section>
            }

            <!-- T5 COMPLETION_PENDING_CONFIRMATION Banner -->
            @if (req.status === 'COMPLETION_PENDING_CONFIRMATION') {
              <section class="detail-card detail-card--wide detail-card--pending-confirmation" aria-labelledby="sec-pending-confirmation">
                <h2 id="sec-pending-confirmation" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">schedule_send</span>
                  Fin d'intervention soumise — En attente de confirmation client
                </h2>
                <p class="decision-prompt">
                  Vous avez soumis le rapport de fin d'intervention
                  @if (req.completedAt) {
                    le <strong>{{ req.completedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                  }
                  Le client doit désormais confirmer la fin des travaux pour clôturer la demande.
                </p>
                @if (req.completionSummary) {
                  <div class="completion-summary-block">
                    <span class="summary-label">Synthèse transmise au client :</span>
                    <p class="prose">{{ req.completionSummary }}</p>
                  </div>
                }
              </section>
            }

            <!-- T5 COMPLETED Banner -->
            @if (req.status === 'COMPLETED') {
              <section class="detail-card detail-card--wide detail-card--completed-banner" aria-labelledby="sec-completed-banner">
                <h2 id="sec-completed-banner" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">task_alt</span>
                  Intervention clôturée et confirmée
                </h2>
                <p class="decision-prompt">
                  L'intervention a été finalisée
                  @if (req.completedAt) {
                    le <strong>{{ req.completedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>
                  }
                  et confirmée par le client
                  @if (req.clientConfirmedAt) {
                    le <strong>{{ req.clientConfirmedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                  }
                </p>
                @if (req.completionSummary) {
                  <div class="completion-summary-block">
                    <span class="summary-label">Synthèse finale de l'intervention :</span>
                    <p class="prose">{{ req.completionSummary }}</p>
                  </div>
                }
              </section>
            }

            <!-- T3 Intervention Workspace (when IN_PROGRESS or WAITING_FOR_PART) -->
            @if (req.status === 'IN_PROGRESS' || req.status === 'WAITING_FOR_PART') {
              <section class="detail-card detail-card--wide detail-card--intervention" aria-labelledby="sec-t3-workspace">
                <h2 id="sec-t3-workspace" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">build</span>
                  Suivi de l'intervention en cours
                </h2>

                <div class="intervention-status-info">
                  <span class="material-icons" aria-hidden="true">timelapse</span>
                  <span>
                    Intervention démarrée le
                    <strong>{{ req.startedAt ? (req.startedAt | date:'dd/MM/yyyy à HH:mm') : '—' }}</strong>.
                  </span>
                </div>

                @if (saveSuccess()) {
                  <div class="alert alert--success" role="status" aria-live="polite">
                    <span class="material-icons" aria-hidden="true">check_circle</span>
                    Informations d'intervention enregistrées avec succès.
                  </div>
                }

                @if (req.status === 'IN_PROGRESS') {
                  <form class="intervention-form" (ngSubmit)="saveIntervention()" #intervForm="ngForm">
                    <div class="form-field">
                      <label for="diag-input" class="form-label">
                        Diagnostic technique / Constat
                        <span class="form-hint">(Observations sur la machine et l'origine de la panne)</span>
                      </label>
                      <textarea
                        id="diag-input"
                        class="form-textarea"
                        name="diagnosis"
                        [(ngModel)]="diagnosisInput"
                        rows="4"
                        placeholder="Ex: Mécanisme d'entraînement bloqué par résidus d'encre. Courroie d'entraînement usée."
                        [disabled]="actionPending()"></textarea>
                    </div>

                    <div class="form-field">
                      <label for="notes-input" class="form-label">
                        Travaux réalisés / Notes d'intervention
                        <span class="form-hint">(Détail des actions effectuées et nettoyages réalisés)</span>
                      </label>
                      <textarea
                        id="notes-input"
                        class="form-textarea"
                        name="interventionNotes"
                        [(ngModel)]="notesInput"
                        rows="4"
                        placeholder="Ex: Démontage du bloc galet, nettoyage des résidus, lubrification des engrenages."
                        [disabled]="actionPending()"></textarea>
                    </div>

                    <!-- T5 Completion Section within IN_PROGRESS -->
                    <div class="completion-section">
                      <div class="form-field">
                        <label for="summary-input" class="form-label">
                          Synthèse de fin d'intervention (T5)
                          <span class="form-hint">(Rapport récapitulatif pour le client)</span>
                        </label>
                        <textarea
                          id="summary-input"
                          class="form-textarea"
                          name="completionSummary"
                          [(ngModel)]="summaryInput"
                          rows="3"
                          placeholder="Ex: Remplacement effectué avec succès. Tests d'impression validés."
                          [disabled]="actionPending()"></textarea>
                      </div>

                      <div class="form-actions form-actions--split">
                        <button type="submit" class="btn btn--ghost" [disabled]="actionPending()">
                          <span class="material-icons" aria-hidden="true">save</span>
                          Enregistrer les notes
                        </button>

                        <button
                          type="button"
                          class="btn btn--accept"
                          [disabled]="actionPending()"
                          (click)="submitCompletion()">
                          @if (actionPending()) {
                            <span class="material-icons spin" aria-hidden="true">sync</span>
                          } @else {
                            <span class="material-icons" aria-hidden="true">task_alt</span>
                          }
                          Soumettre la fin d'intervention (T5)
                        </button>
                      </div>
                    </div>
                  </form>
                } @else {
                  @if (req.diagnosis) {
                    <div class="note-block">
                      <span class="note-title">Diagnostic / Constat :</span>
                      <p class="prose">{{ req.diagnosis }}</p>
                    </div>
                  }
                  @if (req.interventionNotes) {
                    <div class="note-block">
                      <span class="note-title">Travaux réalisés :</span>
                      <p class="prose">{{ req.interventionNotes }}</p>
                    </div>
                  }
                }
              </section>

              <!-- T4 Spare Parts Requests Section -->
              <section class="detail-card detail-card--wide detail-card--parts" aria-labelledby="sec-t4-parts">
                <div class="parts-header">
                  <h2 id="sec-t4-parts" class="detail-card__title">
                    <span class="material-icons" aria-hidden="true">inventory_2</span>
                    Demandes de pièces de rechange (T4)
                  </h2>
                  @if (req.status === 'IN_PROGRESS') {
                    <button
                      type="button"
                      class="btn btn--ghost btn--sm"
                      (click)="showPartForm.set(!showPartForm())">
                      <span class="material-icons" aria-hidden="true">add</span>
                      {{ showPartForm() ? 'Masquer le formulaire' : 'Demander une pièce' }}
                    </button>
                  }
                </div>

                @if (partFormSuccess()) {
                  <div class="alert alert--success" role="status">
                    <span class="material-icons" aria-hidden="true">check_circle</span>
                    Demande de pièce de rechange transmise au manager.
                  </div>
                }

                @if (showPartForm() && req.status === 'IN_PROGRESS') {
                  <form class="part-form" (ngSubmit)="submitPartRequest()" #pForm="ngForm">
                    <div class="form-grid">
                      <div class="form-field">
                        <label class="form-label" for="part-name">Nom de la pièce *</label>
                        <input
                          id="part-name"
                          class="form-input"
                          type="text"
                          name="partName"
                          [(ngModel)]="partNameInput"
                          required
                          placeholder="Ex: Courroie d'entraînement principal"
                          [disabled]="partFormPending()">
                      </div>
                      <div class="form-field">
                        <label class="form-label" for="part-ref">Référence pièce</label>
                        <input
                          id="part-ref"
                          class="form-input"
                          type="text"
                          name="partReference"
                          [(ngModel)]="partRefInput"
                          placeholder="Ex: PRT-9942-B"
                          [disabled]="partFormPending()">
                      </div>
                      <div class="form-field">
                        <label class="form-label" for="part-qty">Quantité *</label>
                        <input
                          id="part-qty"
                          class="form-input"
                          type="number"
                          name="partQuantity"
                          [(ngModel)]="partQtyInput"
                          min="1"
                          required
                          [disabled]="partFormPending()">
                      </div>
                    </div>
                    <div class="form-field">
                      <label class="form-label" for="part-reason">Motif de la demande</label>
                      <textarea
                        id="part-reason"
                        class="form-textarea"
                        name="partReason"
                        [(ngModel)]="partReasonInput"
                        rows="2"
                        placeholder="Ex: Courroie craquelée provoquant le bourrage papier."
                        [disabled]="partFormPending()"></textarea>
                    </div>
                    <div class="form-actions">
                      <button type="submit" class="btn btn--primary btn--sm" [disabled]="partFormPending() || !partNameInput">
                        @if (partFormPending()) {
                          <span class="material-icons spin" aria-hidden="true">sync</span>
                          Envoi…
                        } @else {
                          Envoyer la demande
                        }
                      </button>
                    </div>
                  </form>
                }

                @if (partRequests().length === 0) {
                  <p class="no-parts-text">Aucune demande de pièce de rechange enregistrée pour cette intervention.</p>
                } @else {
                  <div class="parts-list">
                    @for (part of partRequests(); track part.id) {
                      <div class="part-item">
                        <div class="part-item__main">
                          <div class="part-item__title">
                            <strong>{{ part.partName }}</strong>
                            @if (part.partReference) {
                              <span class="part-ref mono">({{ part.partReference }})</span>
                            }
                            <span class="part-qty">x{{ part.quantity }}</span>
                          </div>
                          @if (part.reason) {
                            <p class="part-reason">{{ part.reason }}</p>
                          }
                          <span class="part-meta">
                            Demandé le {{ part.requestedAt | date:'dd/MM/yyyy HH:mm' }}
                            @if (part.reviewedBy) {
                              — Traité par {{ part.reviewedBy }} le {{ part.reviewedAt | date:'dd/MM/yyyy HH:mm' }}
                            }
                          </span>
                        </div>
                        <div class="part-item__right">
                          <span class="badge badge--part-{{ part.status.toLowerCase() }}">
                            {{ partRequestStatusLabel(part.status) }}
                          </span>
                          @if (part.status === 'APPROVED' && req.status === 'IN_PROGRESS') {
                            <button
                              type="button"
                              class="btn btn--ghost btn--sm"
                              [disabled]="actionPending()"
                              (click)="waitOnPart(part.id)">
                              <span class="material-icons" aria-hidden="true">hourglass_top</span>
                              Placer en attente de pièce
                            </button>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              </section>
            }

            @if (req.status === 'REJECTED') {
              <section class="detail-card detail-card--wide detail-card--rejected" aria-labelledby="sec-rejected">
                <h2 id="sec-rejected" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">cancel</span>
                  Assignation refusée
                </h2>
                <p class="decision-info">
                  Vous avez refusé cette assignation
                  @if (req.rejectedAt) {
                    le <strong>{{ req.rejectedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                  }
                  Le manager a été notifié pour réassigner la demande.
                </p>
              </section>
            }

            <!-- Request info -->
            <section class="detail-card" aria-labelledby="sec-request">
              <h2 id="sec-request" class="detail-card__title">
                <span class="material-icons" aria-hidden="true">info</span>
                Demande
              </h2>
              <dl class="detail-dl">
                <dt>Référence</dt><dd class="mono">{{ req.id }}</dd>
                <dt>Statut</dt><dd><span class="badge badge--status badge--{{ req.status.toLowerCase() }}">{{ statusLabel(req.status) }}</span></dd>
                <dt>Priorité</dt><dd><span class="badge badge--priority badge--{{ req.priority.toLowerCase() }}">{{ priorityLabel(req.priority) }}</span></dd>
                <dt>Créé le</dt><dd>{{ req.createdAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                <dt>Mis à jour le</dt><dd>{{ req.updatedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
              </dl>
            </section>

            <!-- Machine info -->
            <section class="detail-card" aria-labelledby="sec-machine">
              <h2 id="sec-machine" class="detail-card__title">
                <span class="material-icons" aria-hidden="true">print</span>
                Machine
              </h2>
              <dl class="detail-dl">
                <dt>Marque, modèle ou référence</dt><dd>{{ req.productReference ?? '—' }}</dd>
                <dt>Numéro de série</dt><dd class="mono">{{ req.serialNumber ?? '—' }}</dd>
                <dt>Référence / N° de bon</dt><dd>{{ req.clientReference ?? '—' }}</dd>
              </dl>
            </section>

            <wwt-maintenance-attachments
              class="detail-card--wide"
              [maintenanceId]="req.id">
            </wwt-maintenance-attachments>

            <!-- Problem description -->
            <section class="detail-card detail-card--wide" aria-labelledby="sec-problem">
              <h2 id="sec-problem" class="detail-card__title">
                <span class="material-icons" aria-hidden="true">description</span>
                Problème signalé par le client
              </h2>
              <p class="prose">{{ req.description }}</p>
            </section>

            <!-- Assignment & timeline info -->
            <section class="detail-card detail-card--wide" aria-labelledby="sec-assignment">
              <h2 id="sec-assignment" class="detail-card__title">
                <span class="material-icons" aria-hidden="true">engineering</span>
                Assignation et Suivi Chronologique
              </h2>
              <dl class="detail-dl">
                <dt>Technicien assigné</dt>
                <dd class="mono">{{ req.technicianId ?? '—' }}</dd>
                <dt>Assigné le</dt>
                <dd>{{ req.assignedAt ? (req.assignedAt | date:'dd/MM/yyyy HH:mm') : '—' }}</dd>
                @if (req.acceptedAt) {
                  <dt>Accepté le</dt>
                  <dd>{{ req.acceptedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                }
                @if (req.rejectedAt) {
                  <dt>Refusé le</dt>
                  <dd>{{ req.rejectedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                }
                @if (req.startedAt) {
                  <dt>Démarré le</dt>
                  <dd>{{ req.startedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                }
                @if (req.completedAt) {
                  <dt>Fin soumise le</dt>
                  <dd>{{ req.completedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                }
                @if (req.clientConfirmedAt) {
                  <dt>Confirmé client le</dt>
                  <dd>{{ req.clientConfirmedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                }
              </dl>
            </section>

          </div>

          <div class="detail-actions">
            <a routerLink="/technician/maintenance" class="btn btn--ghost">
              <span class="material-icons" aria-hidden="true">arrow_back</span>
              Retour à mes interventions
            </a>
          </div>
        }
      }
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .detail-page { padding: 1.5rem 2rem; display: flex; flex-direction: column; gap: 1.5rem; }

    .state-card { display: flex; align-items: center; gap: 1.25rem; padding: 2rem; background: #1a1a1a; border: 1px solid #2a2a2a; border-radius: 8px; color: #ccc; }
    .state-card .material-icons { font-size: 2.5rem; color: #888; }
    .state-card h2 { margin: 0 0 .25rem; font-size: 1rem; color: #fff; }
    .state-card p { margin: 0; font-size: .875rem; }
    .state-card--error .material-icons { color: #e53935; }
    .state-card--loading .material-icons { color: #e91e8c; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .spin { animation: spin 1s linear infinite; }

    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .status-banner {
      grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem;
      background: #111; border: 1px solid #2a2a2a; border-radius: 8px; padding: 1.25rem 1.5rem;
    }
    .status-ref { display: block; font-size: .7rem; font-family: monospace; color: #e91e8c; letter-spacing: .1em; font-weight: 700; margin-bottom: .25rem; }
    .status-title { margin: 0; font-size: 1.1rem; color: #fff; font-weight: 600; }
    .status-banner__right { display: flex; gap: .5rem; flex-wrap: wrap; }

    .detail-card { background: #111; border: 1px solid #2a2a2a; border-radius: 8px; padding: 1.25rem 1.5rem; }
    .detail-card--wide { grid-column: 1 / -1; }
    .detail-card--action { border-color: #e91e8c; background: #160c14; }
    .detail-card--accepted { border-color: #2e7d32; background: #0c1a0e; }
    .detail-card--waiting { border-color: #ff9800; background: #1a120a; }
    .detail-card--pending-confirmation { border-color: #9575cd; background: #120e1a; }
    .detail-card--completed-banner { border-color: #2e7d32; background: #0a1f0d; }
    .detail-card--intervention { border-color: #aed581; background: #0e160a; }
    .detail-card--parts { border-color: #333; }
    .detail-card--rejected { border-color: #c62828; background: #1a0c0c; }
    .detail-card__title { display: flex; align-items: center; gap: .5rem; margin: 0 0 1rem; font-size: .875rem; font-weight: 700; text-transform: uppercase; letter-spacing: .07em; color: #aaa; border-bottom: 1px solid #2a2a2a; padding-bottom: .75rem; }
    .detail-card__title .material-icons { font-size: 1rem; color: #e91e8c; }
    .detail-card--accepted .detail-card__title .material-icons { color: #66bb6a; }
    .detail-card--waiting .detail-card__title .material-icons { color: #ff9800; }
    .detail-card--pending-confirmation .detail-card__title .material-icons { color: #b388ff; }
    .detail-card--completed-banner .detail-card__title .material-icons { color: #66bb6a; }
    .detail-card--intervention .detail-card__title .material-icons { color: #aed581; }
    .detail-card--parts .detail-card__title .material-icons { color: #64b5f6; }

    .decision-prompt { margin: 0 0 1.25rem; font-size: .9rem; color: #e0e0e0; }
    .decision-actions { display: flex; gap: 1rem; flex-wrap: wrap; }

    .completion-summary-block { background: #080808; padding: .75rem 1rem; border-radius: 6px; border: 1px solid #222; margin-top: .75rem; }
    .summary-label { display: block; font-size: .75rem; font-weight: 700; color: #b388ff; text-transform: uppercase; margin-bottom: .25rem; }

    .completion-section { margin-top: 1.25rem; padding-top: 1.25rem; border-top: 1px dashed #2a3a20; }
    .form-actions--split { justify-content: space-between !important; }

    .intervention-status-info { display: flex; align-items: center; gap: .5rem; margin-bottom: 1.25rem; font-size: .875rem; color: #aed581; background: #152210; padding: .6rem 1rem; border-radius: 6px; border: 1px solid #203515; }
    .intervention-status-info .material-icons { font-size: 1.1rem; }

    .alert { display: flex; align-items: center; gap: .5rem; padding: .75rem 1rem; border-radius: 6px; margin: 0 0 1rem; font-size: .875rem; }
    .alert--success { background: #0a2a0a; border: 1px solid #1a3a1a; color: #66bb6a; }
    .alert--success .material-icons { color: #66bb6a; }
    .alert--error { background: #2a0a0a; border: 1px solid #3a1a1a; color: #ef5350; }
    .alert--error .material-icons { color: #ef5350; }

    .parts-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; margin-bottom: 1rem; }
    .parts-header .detail-card__title { margin: 0; border-bottom: none; padding-bottom: 0; }
    .no-parts-text { font-size: .85rem; color: #666; margin: 0; }

    .part-form { background: #0a0a0a; border: 1px solid #222; padding: 1rem; border-radius: 6px; margin-bottom: 1rem; display: flex; flex-direction: column; gap: .75rem; }
    .form-grid { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: .75rem; }
    .part-form .form-actions { display: flex; justify-content: flex-end; }

    .parts-list { display: flex; flex-direction: column; gap: .75rem; }
    .part-item { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .75rem 1rem; background: #161616; border: 1px solid #242424; border-radius: 6px; }
    .part-item__main { display: flex; flex-direction: column; gap: .25rem; }
    .part-item__title { font-size: .875rem; color: #fff; }
    .part-ref { color: #888; font-size: .8rem; margin-left: .35rem; }
    .part-qty { background: #222; padding: .15rem .45rem; border-radius: 4px; font-size: .75rem; color: #e91e8c; font-weight: 700; margin-left: .5rem; }
    .part-reason { margin: 0; font-size: .8rem; color: #aaa; }
    .part-meta { font-size: .72rem; color: #666; }
    .part-item__right { display: flex; align-items: center; gap: .75rem; }

    .badge--part-requested { background: #2a1a0a; color: #ffa726; border: 1px solid #3a2a0a; }
    .badge--part-approved { background: #0a2a0a; color: #66bb6a; border: 1px solid #1a3a1a; }
    .badge--part-rejected { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }

    .intervention-form { display: flex; flex-direction: column; gap: 1.25rem; }
    .form-field { display: flex; flex-direction: column; gap: .35rem; }
    .form-label { font-size: .85rem; font-weight: 600; color: #ddd; }
    .form-hint { font-size: .75rem; font-weight: 400; color: #888; margin-left: .25rem; }
    .form-input, .form-textarea {
      background: #0d0d0d; border: 1px solid #333; border-radius: 6px; color: #e0e0e0;
      padding: .6rem .75rem; font-size: .875rem; line-height: 1.5; width: 100%; box-sizing: border-box;
      transition: border-color .15s; font-family: inherit;
    }
    .form-input:focus, .form-textarea:focus { outline: none; border-color: #e91e8c; }
    .form-input:disabled, .form-textarea:disabled { opacity: .5; cursor: not-allowed; }

    .detail-dl { display: grid; grid-template-columns: max-content 1fr; gap: .4rem 1rem; margin: 0; font-size: .875rem; }
    .detail-dl dt { color: #888; font-weight: 500; white-space: nowrap; }
    .detail-dl dd { margin: 0; color: #e0e0e0; }
    .mono { font-family: monospace; font-size: .8rem; color: #ccc; }
    .prose { margin: 0; color: #ccc; line-height: 1.6; font-size: .9rem; white-space: pre-wrap; }

    .badge { display: inline-flex; align-items: center; padding: .2rem .55rem; border-radius: 4px; font-size: .72rem; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; }
    .badge--priority.badge--low { background: #1a2a1a; color: #66bb6a; border: 1px solid #2a3a2a; }
    .badge--priority.badge--medium { background: #1a1f2a; color: #64b5f6; border: 1px solid #2a2f3a; }
    .badge--priority.badge--high { background: #2a1a0a; color: #ffa726; border: 1px solid #3a2a0a; }
    .badge--priority.badge--critical { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }
    .badge--status.badge--submitted { background: #1a1a2a; color: #9575cd; border: 1px solid #2a2a3a; }
    .badge--status.badge--assigned { background: #0a1f2a; color: #26c6da; border: 1px solid #0a2f3a; }
    .badge--status.badge--accepted { background: #0a2a0a; color: #66bb6a; border: 1px solid #1a3a1a; }
    .badge--status.badge--rejected { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }
    .badge--status.badge--open { background: #1e1e1e; color: #888; border: 1px solid #2a2a2a; }
    .badge--status.badge--in_progress { background: #1a200a; color: #aed581; border: 1px solid #2a300a; }
    .badge--status.badge--waiting_for_part { background: #2a1a0a; color: #ff9800; border: 1px solid #3a2a0a; }
    .badge--status.badge--completion_pending_confirmation { background: #1a0e2a; color: #b388ff; border: 1px solid #2a1e3a; }
    .badge--status.badge--completed { background: #0a2a0a; color: #66bb6a; border: 1px solid #1a3a1a; }
    .badge--status.badge--cancelled { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }

    .detail-actions { display: flex; gap: 1rem; flex-wrap: wrap; }
    .btn { display: inline-flex; align-items: center; gap: .35rem; border: none; cursor: pointer; border-radius: 6px; font-weight: 600; text-decoration: none; transition: background .15s, color .15s, opacity .15s; padding: .5rem 1rem; font-size: .875rem; }
    .btn--primary { background: #e91e8c; color: #fff; }
    .btn--primary:hover:not(:disabled) { background: #c2185b; }
    .btn--accept { background: #2e7d32; color: #fff; }
    .btn--accept:hover:not(:disabled) { background: #1b5e20; }
    .btn--reject { background: #c62828; color: #fff; }
    .btn--reject:hover:not(:disabled) { background: #b71c1c; }
    .btn:disabled { opacity: .5; cursor: not-allowed; }
    .btn--ghost { background: transparent; color: #ccc; border: 1px solid #333; }
    .btn--ghost:hover { border-color: #e91e8c; color: #e91e8c; }
    .btn--sm { padding: .35rem .75rem; font-size: .8rem; }

    @media (max-width: 700px) {
      .detail-page { padding: 1rem; }
      .detail-grid { grid-template-columns: 1fr; }
      .detail-card--wide { grid-column: 1; }
      .status-banner { flex-direction: column; align-items: flex-start; }
      .form-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class TechnicianMaintenanceDetailComponent implements OnInit {
  private readonly api = inject(MaintenanceApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly request = signal<MaintenanceResponse | null>(null);
  protected readonly partRequests = signal<readonly MaintenancePartRequestResponse[]>([]);
  protected readonly actionPending = signal(false);
  protected readonly saveSuccess = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly showPartForm = signal(false);
  protected readonly partFormPending = signal(false);
  protected readonly partFormSuccess = signal(false);

  protected diagnosisInput = '';
  protected notesInput = '';
  protected summaryInput = '';
  protected partNameInput = '';
  protected partRefInput = '';
  protected partQtyInput = 1;
  protected partReasonInput = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.error.set('Identifiant manquant.'); this.loading.set(false); return; }
    this.loadData(id);
  }

  private loadData(id: string): void {
    this.api.getById(id).subscribe({
      next: (r) => {
        this.request.set(r);
        this.diagnosisInput = r.diagnosis ?? '';
        this.notesInput = r.interventionNotes ?? '';
        this.summaryInput = r.completionSummary ?? '';
        this.loading.set(false);
        this.loadParts(id);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Impossible de charger l\'intervention.');
        this.loading.set(false);
      }
    });
  }

  private loadParts(id: string): void {
    this.api.getPartRequests(id).subscribe({
      next: (parts) => this.partRequests.set(parts),
      error: () => undefined
    });
  }

  protected acceptRequest(): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.actionError.set(null);

    this.api.accept(req.id).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.actionPending.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible d\'accepter l\'assignation.');
        this.actionPending.set(false);
      }
    });
  }

  protected rejectRequest(): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.actionError.set(null);

    this.api.reject(req.id).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.actionPending.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible de refuser l\'assignation.');
        this.actionPending.set(false);
      }
    });
  }

  protected startIntervention(): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.actionError.set(null);

    this.api.start(req.id).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.diagnosisInput = updated.diagnosis ?? '';
        this.notesInput = updated.interventionNotes ?? '';
        this.actionPending.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible de démarrer l\'intervention.');
        this.actionPending.set(false);
      }
    });
  }

  protected saveIntervention(): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.saveSuccess.set(false);
    this.actionError.set(null);

    this.api.updateIntervention(req.id, {
      diagnosis: this.diagnosisInput.trim(),
      interventionNotes: this.notesInput.trim(),
    }).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.saveSuccess.set(true);
        this.actionPending.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible d\'enregistrer l\'intervention.');
        this.actionPending.set(false);
      }
    });
  }

  protected submitCompletion(): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.actionError.set(null);

    this.api.submitCompletion(req.id, {
      completionSummary: this.summaryInput.trim() || 'Intervention terminée.',
      interventionNotes: this.notesInput.trim() || undefined,
    }).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.actionPending.set(false);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible de soumettre la fin d\'intervention.');
        this.actionPending.set(false);
      }
    });
  }

  protected submitPartRequest(): void {
    const req = this.request();
    if (!req || !this.partNameInput.trim()) return;
    this.partFormPending.set(true);
    this.partFormSuccess.set(false);

    this.api.createPartRequest(req.id, {
      partName: this.partNameInput.trim(),
      partReference: this.partRefInput.trim() || null,
      quantity: this.partQtyInput || 1,
      reason: this.partReasonInput.trim() || null,
    }).subscribe({
      next: () => {
        this.partFormSuccess.set(true);
        this.partFormPending.set(false);
        this.showPartForm.set(false);
        this.partNameInput = '';
        this.partRefInput = '';
        this.partQtyInput = 1;
        this.partReasonInput = '';
        this.loadParts(req.id);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible de transmettre la demande de pièce.');
        this.partFormPending.set(false);
      }
    });
  }

  protected waitOnPart(partRequestId: string): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.actionError.set(null);

    this.api.waitOnPart(req.id, partRequestId).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.actionPending.set(false);
        this.loadParts(req.id);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible de passer en attente de pièce.');
        this.actionPending.set(false);
      }
    });
  }

  protected resumeIntervention(): void {
    const req = this.request();
    if (!req) return;
    this.actionPending.set(true);
    this.actionError.set(null);

    this.api.resumeFromPartWait(req.id).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.actionPending.set(false);
        this.loadParts(req.id);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message ?? 'Impossible de reprendre l\'intervention.');
        this.actionPending.set(false);
      }
    });
  }

  protected priorityLabel(p: string): string {
    return PRIORITY_LABELS[p as keyof typeof PRIORITY_LABELS] ?? p;
  }

  protected statusLabel(s: string): string {
    return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s;
  }

  protected partRequestStatusLabel(s: string): string {
    return PART_REQUEST_STATUS_LABELS[s as keyof typeof PART_REQUEST_STATUS_LABELS] ?? s;
  }
}
