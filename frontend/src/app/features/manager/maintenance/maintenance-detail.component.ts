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
  TechnicianDirectoryUser,
} from '../../maintenance/maintenance.models';

@Component({
  selector: 'wwt-manager-maintenance-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, FormsModule, DashboardLayoutComponent, PageHeaderComponent, MaintenanceAttachmentsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Demande de maintenance">
      <wwt-page-header
        title="Demande de maintenance"
        breadcrumb="Dashboard / Maintenance / Détail">
      </wwt-page-header>

      <main class="detail-page" aria-label="Détail de la demande de maintenance">

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
              <a routerLink="/manager/maintenance" class="btn btn--ghost btn--sm">← Retour</a>
            </div>
          </div>
        } @else {
          @if (request(); as req) {
          <div class="detail-grid">

            <!-- Status banner -->
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

            <!-- Rejection alert for manager -->
            @if (req.status === 'REJECTED') {
              <div class="status-alert status-alert--warning" role="alert">
                <span class="material-icons" aria-hidden="true">warning</span>
                <div>
                  <strong>Assignation refusée par le technicien</strong>
                  <p>
                    Le technicien {{ technicianDisplayName(req.technicianId) }} a refusé l'intervention
                    @if (req.rejectedAt) {
                      le {{ req.rejectedAt | date:'dd/MM/yyyy à HH:mm' }}.
                    }
                    Veuillez réassigner la demande à un autre technicien.
                  </p>
                </div>
              </div>
            }

            <!-- Acceptance alert for manager -->
            @if (req.status === 'ACCEPTED') {
              <div class="status-alert status-alert--success" role="status">
                <span class="material-icons" aria-hidden="true">check_circle</span>
                <div>
                  <strong>Assignation acceptée</strong>
                  <p>
                    Le technicien a accepté cette intervention
                    @if (req.acceptedAt) {
                      le {{ req.acceptedAt | date:'dd/MM/yyyy à HH:mm' }}.
                    }
                  </p>
                </div>
              </div>
            }

            <!-- In progress alert for manager -->
            @if (req.status === 'IN_PROGRESS') {
              <div class="status-alert status-alert--info" role="status">
                <span class="material-icons" aria-hidden="true">engineering</span>
                <div>
                  <strong>Intervention en cours</strong>
                  <p>
                    Le technicien effectue l'intervention sur l'équipement depuis le
                    <strong>{{ req.startedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                  </p>
                </div>
              </div>
            }

            <!-- Waiting for part alert for manager -->
            @if (req.status === 'WAITING_FOR_PART') {
              <div class="status-alert status-alert--warning" role="status">
                <span class="material-icons" aria-hidden="true">hourglass_top</span>
                <div>
                  <strong>Intervention suspendue — En attente de pièce</strong>
                  <p>
                    L'intervention est suspendue par le technicien dans l'attente de la pièce de rechange approuvée.
                  </p>
                </div>
              </div>
            }

            <!-- Completion pending confirmation alert for manager (T5) -->
            @if (req.status === 'COMPLETION_PENDING_CONFIRMATION') {
              <div class="status-alert status-alert--pending" role="status">
                <span class="material-icons" aria-hidden="true">schedule_send</span>
                <div>
                  <strong>Fin d'intervention soumise (En attente de confirmation client)</strong>
                  <p>
                    Le technicien a soumis la fin d'intervention le
                    <strong>{{ req.completedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                    Le client doit désormais valider la clôture.
                  </p>
                </div>
              </div>
            }

            <!-- Completed alert for manager (T5) -->
            @if (req.status === 'COMPLETED') {
              <div class="status-alert status-alert--success" role="status">
                <span class="material-icons" aria-hidden="true">task_alt</span>
                <div>
                  <strong>Intervention clôturée et confirmée</strong>
                  <p>
                    Finalisée le <strong>{{ req.completedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>
                    et confirmée par le client le <strong>{{ req.clientConfirmedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>.
                  </p>
                </div>
              </div>
            }

            <!-- Request summary -->
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

            <!-- Client info -->
            <section class="detail-card" aria-labelledby="sec-client">
              <h2 id="sec-client" class="detail-card__title">
                <span class="material-icons" aria-hidden="true">person</span>
                Client
              </h2>
              <dl class="detail-dl">
                <dt>Référence client</dt><dd>{{ req.customerReference }}</dd>
                <dt>Référence / N° de bon</dt><dd>{{ req.clientReference ?? '—' }}</dd>
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
                Problème signalé
              </h2>
              <p class="prose">{{ req.description }}</p>
            </section>

            <!-- T3 Technician Diagnosis & Notes + T5 Summary -->
            @if (req.diagnosis || req.interventionNotes || req.completionSummary) {
              <section class="detail-card detail-card--wide detail-card--tech-notes" aria-labelledby="sec-tech-notes">
                <h2 id="sec-tech-notes" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">build</span>
                  Constats, travaux et synthèse (T3/T5)
                </h2>
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
                @if (req.completionSummary) {
                  <div class="note-block note-block--summary">
                    <span class="note-title">Synthèse de fin d'intervention :</span>
                    <p class="prose">{{ req.completionSummary }}</p>
                  </div>
                }
              </section>
            }

            <!-- T4 Spare Parts Requests Management for Manager -->
            @if (partRequests().length > 0) {
              <section class="detail-card detail-card--wide detail-card--parts" aria-labelledby="sec-manager-parts">
                <h2 id="sec-manager-parts" class="detail-card__title">
                  <span class="material-icons" aria-hidden="true">inventory_2</span>
                  Demandes de pièces de rechange (T4)
                </h2>

                @if (partActionError()) {
                  <div class="alert alert--error" role="alert">
                    <span class="material-icons" aria-hidden="true">error_outline</span>
                    {{ partActionError() }}
                  </div>
                }

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
                        @if (part.status === 'REQUESTED') {
                          <div class="part-actions">
                            <button
                              type="button"
                              class="btn btn--accept btn--sm"
                              [disabled]="partActionPending()"
                              (click)="approvePart(part.id)">
                              <span class="material-icons" aria-hidden="true">check</span>
                              Approuver
                            </button>
                            <button
                              type="button"
                              class="btn btn--reject btn--sm"
                              [disabled]="partActionPending()"
                              (click)="rejectPart(part.id)">
                              <span class="material-icons" aria-hidden="true">close</span>
                              Refuser
                            </button>
                          </div>
                        }
                      </div>
                    </div>
                  }
                </div>
              </section>
            }

            <!-- Assignment card -->
            <section class="detail-card detail-card--wide detail-card--highlight" aria-labelledby="sec-assignment">
              <h2 id="sec-assignment" class="detail-card__title">
                <span class="material-icons" aria-hidden="true">engineering</span>
                Assignation et chronologie
              </h2>

              @if (req.technicianId) {
                <dl class="detail-dl">
                  <dt>Technicien</dt>
                  <dd class="tech-assigned-name">
                    <span class="material-icons" aria-hidden="true">engineering</span>
                    <span>{{ technicianDisplayName(req.technicianId) }}</span>
                  </dd>
                  <dt>Assigné le</dt>
                  <dd>{{ req.assignedAt ? (req.assignedAt | date:'dd/MM/yyyy HH:mm') : '—' }}</dd>
                  @if (req.acceptedAt) {
                    <dt>Accepté le</dt><dd>{{ req.acceptedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  }
                  @if (req.rejectedAt) {
                    <dt>Refusé le</dt><dd class="rejected-text">{{ req.rejectedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  }
                  @if (req.startedAt) {
                    <dt>Démarré le</dt><dd>{{ req.startedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  }
                  @if (req.completedAt) {
                    <dt>Fin soumise le</dt><dd>{{ req.completedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  }
                  @if (req.clientConfirmedAt) {
                    <dt>Confirmé client le</dt><dd>{{ req.clientConfirmedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  }
                </dl>
                <p class="assign-hint">Pour réassigner la demande, sélectionnez un autre technicien ci-dessous.</p>
              } @else {
                <p class="assign-hint assign-hint--pending">
                  <span class="material-icons" aria-hidden="true">pending_actions</span>
                  Aucun technicien assigné pour le moment.
                </p>
              }

              @if (assignSuccess()) {
                <div class="alert alert--success" role="status" aria-live="polite">
                  <span class="material-icons" aria-hidden="true">check_circle</span>
                  Technicien assigné avec succès. Statut mis à jour : ASSIGNED.
                </div>
              }
              @if (assignError()) {
                <div class="alert alert--error" role="alert">
                  <span class="material-icons" aria-hidden="true">error_outline</span>
                  {{ assignError() }}
                </div>
              }

              @if (loadingTechnicians()) {
                <div class="tech-loading" role="status" aria-live="polite">
                  <span class="material-icons spin" aria-hidden="true">sync</span>
                  <span>Chargement des techniciens…</span>
                </div>
              } @else if (techniciansError()) {
                <div class="alert alert--error" role="alert">
                  <span class="material-icons" aria-hidden="true">error_outline</span>
                  <div class="error-msg-wrap">
                    <span>{{ techniciansError() }}</span>
                    <button type="button" class="btn btn--ghost btn--sm retry-btn" (click)="loadTechnicians()">
                      <span class="material-icons" aria-hidden="true">refresh</span>
                      Réessayer
                    </button>
                  </div>
                </div>
              } @else if (technicians().length === 0) {
                <div class="alert alert--warning" role="status">
                  <span class="material-icons" aria-hidden="true">info</span>
                  <span>Aucun technicien disponible.</span>
                </div>
              } @else {
                <form class="assign-form" (ngSubmit)="assign()" #assignForm="ngForm" novalidate>
                  <label class="form-field">
                    <span class="form-label">Technicien</span>
                    <select
                      class="form-input form-select"
                      name="technicianId"
                      [(ngModel)]="selectedTechnicianId"
                      required
                      [disabled]="assigning()"
                      aria-label="Sélectionner un technicien">
                      <option value="" disabled>Sélectionner un technicien</option>
                      @for (tech of technicians(); track tech.id) {
                        <option [value]="tech.id">
                          {{ formatTechnicianName(tech) }}
                        </option>
                      }
                    </select>
                  </label>
                  <button type="submit" class="btn btn--primary" [disabled]="assigning() || !selectedTechnicianId">
                    @if (assigning()) {
                      <span class="material-icons spin" aria-hidden="true">sync</span>
                      Assignation…
                    } @else {
                      <span class="material-icons" aria-hidden="true">engineering</span>
                      {{ req.technicianId ? 'Réassigner' : 'Assigner le technicien' }}
                    }
                  </button>
                </form>
              }
            </section>

          </div>

          <div class="detail-actions">
            <a routerLink="/manager/maintenance" class="btn btn--ghost">
              <span class="material-icons" aria-hidden="true">arrow_back</span>
              Retour à la liste
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

    .status-alert {
      grid-column: 1 / -1; display: flex; align-items: flex-start; gap: .75rem;
      padding: 1rem 1.25rem; border-radius: 8px; font-size: .875rem;
    }
    .status-alert strong { display: block; font-size: .9rem; margin-bottom: .2rem; }
    .status-alert p { margin: 0; line-height: 1.4; }
    .status-alert--warning { background: #2a1a0a; border: 1px solid #3a2a0a; color: #ffa726; }
    .status-alert--warning .material-icons { color: #ffa726; }
    .status-alert--success { background: #0a2a0a; border: 1px solid #1a3a1a; color: #66bb6a; }
    .status-alert--success .material-icons { color: #66bb6a; }
    .status-alert--info { background: #1a240a; border: 1px solid #2a3a0a; color: #aed581; }
    .status-alert--info .material-icons { color: #aed581; }
    .status-alert--pending { background: #1a0e2a; border: 1px solid #2a1e3a; color: #b388ff; }
    .status-alert--pending .material-icons { color: #b388ff; }

    .detail-card { background: #111; border: 1px solid #2a2a2a; border-radius: 8px; padding: 1.25rem 1.5rem; }
    .detail-card--wide { grid-column: 1 / -1; }
    .detail-card--highlight { border-color: #2a1a2a; background: #0d0b0d; }
    .detail-card--tech-notes { border-color: #2a3015; background: #0d120a; }
    .detail-card--parts { border-color: #333; }
    .detail-card__title { display: flex; align-items: center; gap: .5rem; margin: 0 0 1rem; font-size: .875rem; font-weight: 700; text-transform: uppercase; letter-spacing: .07em; color: #aaa; border-bottom: 1px solid #2a2a2a; padding-bottom: .75rem; }
    .detail-card__title .material-icons { font-size: 1rem; color: #e91e8c; }
    .detail-card--tech-notes .detail-card__title .material-icons { color: #aed581; }
    .detail-card--parts .detail-card__title .material-icons { color: #64b5f6; }

    .note-block { margin-bottom: 1rem; }
    .note-block:last-child { margin-bottom: 0; }
    .note-block--summary { padding-top: .75rem; border-top: 1px dashed #2a3a20; }
    .note-title { display: block; font-size: .78rem; font-weight: 700; color: #aed581; text-transform: uppercase; letter-spacing: .05em; margin-bottom: .25rem; }

    .parts-list { display: flex; flex-direction: column; gap: .75rem; }
    .part-item { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .75rem 1rem; background: #161616; border: 1px solid #242424; border-radius: 6px; }
    .part-item__main { display: flex; flex-direction: column; gap: .25rem; }
    .part-item__title { font-size: .875rem; color: #fff; }
    .part-ref { color: #888; font-size: .8rem; margin-left: .35rem; }
    .part-qty { background: #222; padding: .15rem .45rem; border-radius: 4px; font-size: .75rem; color: #e91e8c; font-weight: 700; margin-left: .5rem; }
    .part-reason { margin: 0; font-size: .8rem; color: #aaa; }
    .part-meta { font-size: .72rem; color: #666; }
    .part-item__right { display: flex; align-items: center; gap: .75rem; }
    .part-actions { display: flex; gap: .4rem; }

    .badge--part-requested { background: #2a1a0a; color: #ffa726; border: 1px solid #3a2a0a; }
    .badge--part-approved { background: #0a2a0a; color: #66bb6a; border: 1px solid #1a3a1a; }
    .badge--part-rejected { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }

    .detail-dl { display: grid; grid-template-columns: max-content 1fr; gap: .4rem 1rem; margin: 0; font-size: .875rem; }
    .detail-dl dt { color: #888; font-weight: 500; white-space: nowrap; }
    .detail-dl dd { margin: 0; color: #e0e0e0; }
    .mono { font-family: monospace; font-size: .8rem; color: #ccc; }
    .rejected-text { color: #ef5350; }
    .prose { margin: 0; color: #ccc; line-height: 1.6; font-size: .9rem; white-space: pre-wrap; }

    .assign-hint { display: flex; align-items: center; gap: .5rem; margin: .75rem 0; font-size: .85rem; color: #888; }
    .assign-hint .material-icons { font-size: 1rem; }
    .assign-hint--pending { color: #9575cd; }

    .alert { display: flex; align-items: center; gap: .5rem; padding: .75rem 1rem; border-radius: 6px; margin: .75rem 0; font-size: .875rem; }
    .alert--success { background: #0a2a0a; border: 1px solid #1a3a1a; color: #66bb6a; }
    .alert--success .material-icons { color: #66bb6a; }
    .alert--error { background: #2a0a0a; border: 1px solid #3a1a1a; color: #ef5350; }
    .alert--error .material-icons { color: #ef5350; }

    .assign-form { display: flex; flex-direction: column; gap: .75rem; margin-top: 1rem; }
    .form-field { display: flex; flex-direction: column; gap: .35rem; }
    .form-label { font-size: .8rem; font-weight: 600; color: #aaa; }
    .form-hint { font-size: .75rem; font-weight: 400; color: #666; margin-left: .25rem; }
    .form-input {
      background: #0d0d0d; border: 1px solid #333; border-radius: 6px; color: #e0e0e0;
      padding: .6rem .85rem; font-size: .875rem; width: 100%; max-width: 480px;
      transition: border-color .15s;
    }
    .form-input:focus { outline: none; border-color: #e91e8c; }
    .form-input:disabled { opacity: .5; cursor: not-allowed; }
    .form-select {
      font-family: inherit;
      cursor: pointer;
      appearance: none;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' height='24px' viewBox='0 0 24 24' width='24px' fill='%23888888'%3E%3Cpath d='M0 0h24v24H0V0z' fill='none'/%3E%3Cpath d='M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z'/%3E%3C/svg%3E");
      background-repeat: no-repeat;
      background-position: right .75rem center;
      padding-right: 2.5rem;
    }
    .form-select option { background: #1a1a1a; color: #fff; }
    .form-select option:disabled { color: #666; }
    .tech-loading { display: flex; align-items: center; gap: .5rem; color: #888; font-size: .85rem; padding: .5rem 0; }
    .alert--warning { background: #2a1f0a; border: 1px solid #3a2a0a; color: #ffa726; }
    .alert--warning .material-icons { color: #ffa726; }
    .error-msg-wrap { display: flex; align-items: center; justify-content: space-between; width: 100%; gap: .75rem; flex-wrap: wrap; }
    .retry-btn { margin-left: auto; }
    .tech-assigned-name { display: flex; align-items: center; gap: .4rem; color: #fff; }
    .tech-assigned-name .material-icons { font-size: 1.1rem; color: #26c6da; }

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
    .btn { display: inline-flex; align-items: center; gap: .35rem; border: none; cursor: pointer; border-radius: 6px; font-weight: 600; text-decoration: none; transition: background .15s, color .15s; padding: .5rem 1rem; font-size: .875rem; }
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
    }
  `]
})
export class ManagerMaintenanceDetailComponent implements OnInit {
  private readonly api = inject(MaintenanceApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly request = signal<MaintenanceResponse | null>(null);
  protected readonly partRequests = signal<readonly MaintenancePartRequestResponse[]>([]);
  protected readonly assigning = signal(false);
  protected readonly assignSuccess = signal(false);
  protected readonly assignError = signal<string | null>(null);
  protected readonly partActionPending = signal(false);
  protected readonly partActionError = signal<string | null>(null);

  protected readonly technicians = signal<readonly TechnicianDirectoryUser[]>([]);
  protected readonly loadingTechnicians = signal(false);
  protected readonly techniciansError = signal<string | null>(null);
  protected selectedTechnicianId = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.error.set('Identifiant manquant.'); this.loading.set(false); return; }
    this.loadData(id);
    this.loadTechnicians();
  }

  private loadData(id: string): void {
    this.api.getById(id).subscribe({
      next: (r) => {
        this.request.set(r);
        this.selectedTechnicianId = r.technicianId ?? '';
        this.loading.set(false);
        this.loadParts(id);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Impossible de charger la demande.');
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

  protected loadTechnicians(): void {
    this.loadingTechnicians.set(true);
    this.techniciansError.set(null);
    this.api.getTechnicians().subscribe({
      next: (techs) => {
        this.technicians.set(techs);
        this.loadingTechnicians.set(false);
        const req = this.request();
        if (req?.technicianId && !this.selectedTechnicianId) {
          this.selectedTechnicianId = req.technicianId;
        }
      },
      error: (err) => {
        this.loadingTechnicians.set(false);
        this.techniciansError.set(
          err?.error?.message ?? 'Impossible de charger la liste des techniciens.'
        );
      }
    });
  }

  protected assign(): void {
    const req = this.request();
    if (!req || !this.selectedTechnicianId?.trim()) return;
    this.assigning.set(true);
    this.assignSuccess.set(false);
    this.assignError.set(null);

    this.api.assignTechnician(req.id, this.selectedTechnicianId.trim()).subscribe({
      next: (updated) => {
        this.request.set(updated);
        this.selectedTechnicianId = updated.technicianId ?? '';
        this.assignSuccess.set(true);
        this.assigning.set(false);
      },
      error: (err) => {
        this.assignError.set(
          err?.error?.message ?? 'Impossible d\'assigner le technicien. Veuillez réessayer.'
        );
        this.assigning.set(false);
      }
    });
  }

  protected formatTechnicianName(tech: TechnicianDirectoryUser): string {
    const fullName = [tech.firstName, tech.lastName].filter(Boolean).join(' ').trim();
    if (fullName && tech.email) {
      return `${fullName} — ${tech.email}`;
    }
    return fullName || tech.email || tech.id;
  }

  protected technicianDisplayName(id: string | null | undefined): string {
    if (!id) return '—';
    const tech = this.technicians().find((t) => t.id === id);
    if (tech) {
      return this.formatTechnicianName(tech);
    }
    return id;
  }

  protected approvePart(partRequestId: string): void {
    const req = this.request();
    if (!req) return;
    this.partActionPending.set(true);
    this.partActionError.set(null);

    this.api.approvePartRequest(req.id, partRequestId).subscribe({
      next: () => {
        this.partActionPending.set(false);
        this.loadParts(req.id);
      },
      error: (err) => {
        this.partActionError.set(err?.error?.message ?? 'Impossible d\'approuver la demande de pièce.');
        this.partActionPending.set(false);
      }
    });
  }

  protected rejectPart(partRequestId: string): void {
    const req = this.request();
    if (!req) return;
    this.partActionPending.set(true);
    this.partActionError.set(null);

    this.api.rejectPartRequest(req.id, partRequestId).subscribe({
      next: () => {
        this.partActionPending.set(false);
        this.loadParts(req.id);
      },
      error: (err) => {
        this.partActionError.set(err?.error?.message ?? 'Impossible de refuser la demande de pièce.');
        this.partActionPending.set(false);
      }
    });
  }

  protected priorityLabel(p: string): string { return PRIORITY_LABELS[p as keyof typeof PRIORITY_LABELS] ?? p; }
  protected statusLabel(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  protected partRequestStatusLabel(s: string): string { return PART_REQUEST_STATUS_LABELS[s as keyof typeof PART_REQUEST_STATUS_LABELS] ?? s; }
}
