import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { OrderApiService } from './order-api.service';
import {
  DeliveryMethod,
  OrderNotificationDeliveryResponse,
  OrderResponse,
  OrderStatus,
  deliveryAreaLabel,
  deliveryFeeWaivedLabel,
  orderStatusLabel,
  paymentPlanLabel,
  paymentStatusLabel
} from './order.models';
import { ProductApiService } from '../products/product-api.service';
import { ProductImage } from '../products/product.models';

interface OrderItemView {
  readonly item: OrderResponse['items'][number];
  readonly name: string;
  readonly image: ProductImage | null;
}

type OrderProgressStatus = Exclude<OrderStatus, 'CANCELLED'>;
type OrderProgressState = 'completed' | 'current' | 'pending';

@Component({
  selector: 'wwt-order-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Détail de la commande">
      <wwt-page-header
        title="Détail de la commande"
        subtitle="Consultez le suivi, les produits, la livraison et le paiement."
        breadcrumb="Commandes / Détail">
      </wwt-page-header>

    <section class="detail" aria-labelledby="order-title">
      <a class="back" [routerLink]="backPath()">← {{ isClient() ? 'Mes commandes' : 'Commandes' }}</a>
      @if (loading()) {
        <section class="state" role="status">Chargement de la commande…</section>
      } @else if (error()) {
        <section class="state" role="alert">
          <h2>Accès impossible</h2>
          <p>{{ error() }}</p>
        </section>
      } @else {
        @let currentOrder = order();
        @if (currentOrder) {
          <p class="eyebrow">Commande</p><h1 id="order-title">{{ currentOrder.orderNumber || currentOrder.id }}</h1>
            <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; margin-top: 0.35rem;">
              <p class="meta" style="margin: 0;">Créée le {{ currentOrder.createdAt | date:'dd/MM/yyyy HH:mm' }} · ID : {{ currentOrder.id }}</p>
              <span style="padding: 0.2rem 0.6rem; border-radius: 999px; font-size: 0.75rem; font-weight: 750; background: #fdf3f7; color: #8f123f; border: 1px solid #f0cad8;">
                {{ currentOrder.guestOrder ? (currentOrder.customerSubject ? 'Commande invitée rattachée au compte' : 'Commande invitée') : 'Client inscrit' }}
              </span>
            </div>
            @if (currentOrder.contactFullName || currentOrder.contactEmail || currentOrder.contactPhone) {
              <p class="meta" style="margin: 0.35rem 0 0;">
                Contact : <strong>{{ currentOrder.contactFullName || currentOrder.customerReference }}</strong>
                @if (currentOrder.contactEmail) { · {{ currentOrder.contactEmail }} }
                @if (currentOrder.contactPhone) { · {{ currentOrder.contactPhone }} }
              </p>
            }
            <button class="cancel" type="button" [disabled]="pdfDownloading()" (click)="downloadPdf(currentOrder.id)">
              {{ pdfDownloading() ? 'Téléchargement…' : 'Télécharger le bon de commande PDF' }}
            </button>
            @if (pdfError()) { <p class="cancel-error" role="alert">{{ pdfError() }}</p> }
            @if (currentOrder.quotationId) {
              <p class="meta">Créée depuis le devis
                <a [routerLink]="isClient() ? ['/devis/mes-demandes', currentOrder.quotationId] : ['/devis', currentOrder.quotationId]">{{ currentOrder.quotationId }}</a>
              </p>
            }
            <section class="order-progress" aria-labelledby="order-progress-title">
              <div class="progress-heading">
                <div>
                  <p class="progress-kicker">Suivi de commande</p>
                  <h2 id="order-progress-title">Progression</h2>
                </div>
                <p class="payment-status"
                   [class.payment-status--paid]="currentOrder.paymentStatus === 'PAID'"
                   [class.payment-status--partial]="currentOrder.paymentStatus === 'PARTIALLY_PAID'">
                  <span>Statut du paiement</span>
                  <strong>{{ paymentStatusLabel(currentOrder.paymentStatus) }}</strong>
                </p>
              </div>

              @if (currentOrder.status === 'CANCELLED') {
                <div class="cancelled-status" role="status">
                  <span class="cancelled-mark" aria-hidden="true">×</span>
                  <div>
                    <strong>Commande annulée</strong>
                    <p>Cette commande ne suit plus le parcours de traitement.</p>
                  </div>
                </div>
              } @else {
                <ol class="progress-steps" aria-label="Étapes de la commande">
                  @for (step of orderProgressSteps; track step; let index = $index) {
                    @let state = progressState(currentOrder.status, step);
                    <li
                      class="progress-step"
                      [class.progress-step--completed]="state === 'completed'"
                      [class.progress-step--current]="state === 'current'"
                      [attr.aria-current]="state === 'current' ? 'step' : null"
                    >
                      <span class="step-marker" aria-hidden="true">{{ state === 'completed' ? '✓' : index + 1 }}</span>
                      <span class="step-copy">
                        <strong>{{ progressLabel(step, currentOrder.deliveryMethod) }}</strong>
                        <small>{{ progressStateLabel(state) }}</small>
                      </span>
                    </li>
                  }
                </ol>
              }
            </section>
            @if (canCancel(currentOrder)) {
              <button class="cancel" type="button" [disabled]="cancelling()" (click)="cancelOrder(currentOrder.id)">
                {{ cancelling() ? 'Annulation en cours…' : 'Annuler la commande' }}
              </button>
            }
            @if (canConfirm(currentOrder)) { <button class="action" type="button" [disabled]="actionBusy()" (click)="confirmOrder(currentOrder.id)">Confirmer la commande</button> }
            @if (canProcess(currentOrder)) { <button class="action" type="button" [disabled]="actionBusy()" (click)="processOrder(currentOrder.id)">Démarrer le traitement</button> }
            @if (canMarkReady(currentOrder)) { <button class="action" type="button" [disabled]="actionBusy()" (click)="markOrderReady(currentOrder.id)">Marquer comme prête</button> }
            @if (canComplete(currentOrder)) { <button class="action" type="button" [disabled]="actionBusy()" (click)="completeOrder(currentOrder.id)">{{ completionActionLabel(currentOrder.deliveryMethod) }}</button> }
            @if (canConfirmPayment(currentOrder)) { <button class="action" type="button" [disabled]="actionBusy()" (click)="confirmPayment(currentOrder.id)">{{ confirmPaymentActionLabel(currentOrder) }}</button> }
            @if (cancelError()) { <p class="cancel-error" role="alert">{{ cancelError() }}</p> }
            @if (actionError()) { <p class="cancel-error" role="alert">{{ actionError() }}</p> }

            <!-- SECTION DÉDIÉE : PAIEMENT FRACTIONNÉ (50 % + 50 %) OU SUIVI FINANCIER -->
            <section class="panel balance-section" aria-labelledby="balance-payment-title">
              <div class="balance-header">
                <div class="balance-title-group">
                  <h2 id="balance-payment-title">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#8f123f" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <rect x="2" y="5" width="20" height="14" rx="2"></rect>
                      <line x1="2" y1="10" x2="22" y2="10"></line>
                    </svg>
                    {{ isSplitPayment(currentOrder) ? 'Paiement fractionné — 50 % + 50 %' : 'Paiement et solde restant' }}
                  </h2>
                  <p class="balance-subtitle">
                    {{ isSplitPayment(currentOrder)
                      ? 'Deux versements indépendants par virement bancaire : acompte initial de 50 % et solde restant de 50 %.'
                      : 'Synthèse financière de la commande, suivi des règlements et paiement sécurisé du solde.' }}
                  </p>
                </div>
                <div class="balance-status-badge" [class.badge-paid]="currentOrder.paymentStatus === 'PAID'" [class.badge-partial]="currentOrder.paymentStatus === 'PARTIALLY_PAID'">
                  {{ paymentFinancialStatusBadge(currentOrder) }}
                </div>
              </div>

              <!-- Synthèse financière -->
              <div class="balance-metrics-grid">
                <div class="balance-metric-card">
                  <span class="metric-label">Montant total</span>
                  <strong class="metric-value">{{ currentOrder.total | number:'1.2-2' }} DH</strong>
                </div>
                <div class="balance-metric-card">
                  <span class="metric-label">Montant déjà payé</span>
                  <strong class="metric-value text-success">{{ paidAmount(currentOrder) | number:'1.2-2' }} DH</strong>
                </div>
                <div class="balance-metric-card" [class.highlight]="remainingBalance(currentOrder) > 0">
                  <span class="metric-label">Solde restant à payer</span>
                  <strong class="metric-value">{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</strong>
                </div>
                <div class="balance-metric-card">
                  <span class="metric-label">Statut du paiement</span>
                  <span class="metric-status-text">{{ paymentFinancialStatusText(currentOrder) }}</span>
                </div>
                <div class="balance-metric-card">
                  <span class="metric-label">Mode de réception</span>
                  <strong class="metric-value metric-reception">{{ receptionMethodLabel(currentOrder.deliveryMethod) }}</strong>
                </div>
              </div>

              <!-- POUR PAIEMENT FRACTIONNÉ : LES 2 BLOCS INDÉPENDANTS -->
              @if (isSplitPayment(currentOrder)) {
                <div class="split-installments-grid">
                  <!-- BLOC 1 : PREMIER VERSEMENT — ACOMPTE 50 % -->
                  <div class="installment-block" [class.installment-block--confirmed]="isFirstInstallmentConfirmed(currentOrder)">
                    <div class="installment-block-header">
                      <div class="installment-num-badge">1</div>
                      <div class="installment-header-text">
                        <h3 class="installment-block-title">Premier versement — Acompte 50 %</h3>
                        <span class="installment-block-amount">{{ (currentOrder.upfrontPaymentAmount ?? (currentOrder.total / 2)) | number:'1.2-2' }} DH</span>
                      </div>
                      <span class="installment-status-badge" [class]="'badge-status--' + firstInstallmentStatus(currentOrder).toLowerCase()">
                        {{ firstInstallmentStatusLabel(currentOrder) }}
                      </span>
                    </div>

                    @if (currentOrder.firstInstallmentConfirmedAt) {
                      <div class="installment-confirmed-date">
                        <span class="confirmed-check-icon">✓</span>
                        <span>Confirmé le {{ currentOrder.firstInstallmentConfirmedAt | date:'dd/MM/yyyy HH:mm' }}</span>
                      </div>
                    }

                    <!-- Justificatif du 1er virement -->
                    <div class="installment-proof-container">
                      <span class="proof-subheading">Justificatif du premier virement</span>
                      @if (currentOrder.transferProofReference) {
                        <div class="proof-card">
                          @if (proofPreviewUrl(); as previewUrl) {
                            <div
                              class="proof-thumbnail-wrapper"
                              (click)="previewProof(currentOrder.id, 1)"
                              title="Cliquer pour agrandir l'image"
                            >
                              <img [src]="previewUrl" alt="Reçu 1er virement acompte" class="proof-thumb-img" />
                              <div class="proof-thumbnail-hover-overlay">
                                <span>🔍 Agrandir l'image</span>
                              </div>
                            </div>
                          } @else if (previewLoading()) {
                            <div class="proof-loading-placeholder">Chargement de l'aperçu…</div>
                          } @else {
                            <div class="proof-ready-placeholder">Reçu d'acompte disponible</div>
                          }

                          <div class="proof-file-bar">
                            <span class="proof-file-name" [title]="proofFileName(currentOrder)">
                              Fichier : <code>{{ proofFileName(currentOrder) }}</code>
                            </span>
                            <div class="proof-file-actions">
                              <button
                                type="button"
                                class="btn-proof-action btn-proof-preview"
                                [disabled]="previewLoading()"
                                (click)="previewProof(currentOrder.id, 1)">
                                {{ previewLoading() ? 'Chargement…' : 'Consulter' }}
                              </button>
                              <button
                                type="button"
                                class="btn-proof-action btn-proof-download"
                                [disabled]="proofDownloading()"
                                (click)="downloadProof(currentOrder.id, 1)">
                                {{ proofDownloading() ? 'Téléchargement…' : 'Télécharger' }}
                              </button>
                            </div>
                          </div>
                        </div>
                      } @else {
                        <div class="proof-empty-notice">
                          <p>Reçu non fourni</p>
                          @if (isClient() && currentOrder.status !== 'CANCELLED' && currentOrder.paymentStatus === 'UNPAID') {
                            <div class="installment-upload-form">
                              <p style="margin: 0.3rem 0; font-size: 0.8rem; color: #625f5c;">Déposez le reçu de votre acompte de 50 % (JPG, PNG, max 5 Mo) :</p>
                              <input
                                type="file"
                                accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                                (change)="onProofFileSelected($event)"
                                class="file-upload-input"
                              />
                              @if (selectedFile()) {
                                <div class="upload-confirm-row">
                                  <span class="file-chosen-name">{{ selectedFileName() }}</span>
                                  <button
                                    type="button"
                                    class="btn-confirm-upload"
                                    [disabled]="proofUploading()"
                                    (click)="uploadProof(currentOrder.id)">
                                    {{ proofUploading() ? 'Envoi…' : 'Confirmer le reçu d’acompte' }}
                                  </button>
                                </div>
                              }
                            </div>
                          }
                        </div>
                      }

                      @if (proofUploadError()) {
                        <p class="upload-feedback error" role="alert">{{ proofUploadError() }}</p>
                      }
                      @if (proofSuccessMessage()) {
                        <p class="upload-feedback success" role="status">{{ proofSuccessMessage() }}</p>
                      }

                      @if (isStaff() && currentOrder.paymentStatus === 'UNPAID') {
                        <div style="margin-top: 0.4rem; padding-top: 0.4rem; border-top: 1px dashed #e5e0dc;">
                          <button
                            type="button"
                            class="action"
                            style="margin: 0; padding: 0.35rem 0.8rem; font-size: 0.8rem;"
                            [disabled]="actionBusy()"
                            (click)="confirmPayment(currentOrder.id)">
                            Valider l'acompte (50 %)
                          </button>
                        </div>
                      }
                    </div>
                  </div>

                  <!-- BLOC 2 : DEUXIÈME VERSEMENT — SOLDE 50 % -->
                  <div class="installment-block" [class.installment-block--confirmed]="isSecondInstallmentConfirmed(currentOrder)">
                    <div class="installment-block-header">
                      <div class="installment-num-badge">2</div>
                      <div class="installment-header-text">
                        <h3 class="installment-block-title">Deuxième versement — Solde 50 %</h3>
                        <span class="installment-block-amount">{{ (currentOrder.remainingDeliveryAmount ?? (currentOrder.total / 2)) | number:'1.2-2' }} DH</span>
                      </div>
                      <span class="installment-status-badge" [class]="'badge-status--' + secondInstallmentStatus(currentOrder).toLowerCase()">
                        {{ secondInstallmentStatusLabel(currentOrder) }}
                      </span>
                    </div>

                    <div class="installment-method-pill-box">
                      <span class="method-label">Mode de règlement du solde :</span>
                      <span class="method-value" [class.method-set]="!!currentOrder.secondInstallmentPaymentMethod">
                        {{ secondInstallmentMethodLabel(currentOrder) }}
                      </span>
                    </div>

                    @if (isSecondInstallmentConfirmed(currentOrder)) {
                      <div class="installment-confirmed-date">
                        <span class="confirmed-check-icon">✓</span>
                        <span>
                          2ème versement — Confirmé
                          @if (currentOrder.secondInstallmentConfirmedAt) { le {{ currentOrder.secondInstallmentConfirmedAt | date:'dd/MM/yyyy HH:mm' }} }
                          @if (isStaff() && currentOrder.secondInstallmentConfirmedBy) { par {{ currentOrder.secondInstallmentConfirmedBy }} }
                        </span>
                      </div>
                    } @else if (currentOrder.secondInstallmentPaymentMethod === 'CASH') {
                      <div class="installment-pending-date installment-pending-date--cash">
                        <span class="pending-icon">💵</span>
                        <span>Paiement en espèces prévu à la réception — En attente d’encaissement</span>
                      </div>
                    } @else if (currentOrder.secondTransferProofReference) {
                      <div class="installment-pending-date">
                        <span class="pending-icon">⏳</span>
                        <span>Reçu du 2ème versement — En attente de validation</span>
                        @if (currentOrder.secondTransferProofSubmittedAt) {
                          <small style="display: block; font-size: 0.74rem; color: #8c5a08; margin-top: 0.15rem;">Déposé le {{ currentOrder.secondTransferProofSubmittedAt | date:'dd/MM/yyyy HH:mm' }}</small>
                        }
                      </div>
                    }

                    <!-- Zone de contenu ou justificatif du 2ème versement -->
                    <div class="installment-proof-container">
                      @if (currentOrder.secondInstallmentPaymentMethod === 'CASH' && !isSecondInstallmentConfirmed(currentOrder)) {
                        <div class="cash-instructions-card">
                          <div class="cash-card-header">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#286235" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                              <rect x="2" y="6" width="20" height="12" rx="2"></rect>
                              <circle cx="12" cy="12" r="3"></circle>
                              <path d="M6 12h.01M18 12h.01"></path>
                            </svg>
                            <strong>Règlement en espèces à la remise</strong>
                          </div>
                          <p class="cash-instructions-text">
                            @if (currentOrder.deliveryMethod === 'PICKUP') {
                              Le solde de <strong>{{ (currentOrder.remainingDeliveryAmount ?? (currentOrder.total / 2)) | number:'1.2-2' }} DH</strong> sera à remettre à l’accueil du showroom de Kénitra lors de l’enlèvement de votre commande.
                            } @else {
                              Le solde de <strong>{{ (currentOrder.remainingDeliveryAmount ?? (currentOrder.total / 2)) | number:'1.2-2' }} DH</strong> sera à remettre en mains propres au chauffeur-livreur lors de la livraison.
                            }
                          </p>
                          <div class="cash-card-badge">
                            <span>Aucun justificatif requis · Encaissement validé par l’équipe WWTRADING</span>
                          </div>
                        </div>
                      } @else {
                        <span class="proof-subheading">Justificatif du deuxième virement</span>
                        @if (currentOrder.secondTransferProofReference) {
                          <div class="proof-card">
                            @if (secondProofPreviewUrl(); as previewUrl2) {
                              <div
                                class="proof-thumbnail-wrapper"
                                (click)="previewProof(currentOrder.id, 2)"
                                title="Cliquer pour agrandir l'image"
                              >
                                <img [src]="previewUrl2" alt="Reçu 2ème virement solde" class="proof-thumb-img" />
                                <div class="proof-thumbnail-hover-overlay">
                                  <span>🔍 Agrandir l'image</span>
                                </div>
                              </div>
                            } @else if (secondPreviewLoading()) {
                              <div class="proof-loading-placeholder">Chargement de l'aperçu du solde…</div>
                            } @else {
                              <div class="proof-ready-placeholder">Reçu du solde disponible</div>
                            }

                            <div class="proof-file-bar">
                              <span class="proof-file-name" [title]="secondProofFileName(currentOrder)">
                                Fichier : <code>{{ secondProofFileName(currentOrder) }}</code>
                              </span>
                              <div class="proof-file-actions">
                                <button
                                  type="button"
                                  class="btn-proof-action btn-proof-preview"
                                  [disabled]="secondPreviewLoading()"
                                  (click)="previewProof(currentOrder.id, 2)">
                                  {{ secondPreviewLoading() ? 'Chargement…' : 'Consulter' }}
                                </button>
                                <button
                                  type="button"
                                  class="btn-proof-action btn-proof-download"
                                  [disabled]="secondProofDownloading()"
                                  (click)="downloadProof(currentOrder.id, 2)">
                                  {{ secondProofDownloading() ? 'Téléchargement…' : 'Télécharger' }}
                                </button>
                              </div>
                            </div>
                          </div>
                        } @else {
                          <div class="proof-empty-notice">
                            <p>Reçu non fourni</p>
                            @if (!isFirstInstallmentConfirmed(currentOrder)) {
                              <small class="text-muted">Le choix du règlement du solde sera disponible dès confirmation de votre premier acompte.</small>
                            }
                          </div>
                        }
                      }

                      @if (isStaff() && currentOrder.paymentStatus === 'PARTIALLY_PAID') {
                        <div style="margin-top: 0.6rem; padding-top: 0.6rem; border-top: 1px dashed #e5e0dc;">
                          <button
                            type="button"
                            class="action"
                            style="margin: 0; padding: 0.45rem 1rem; font-size: 0.82rem; background: #8f123f; border-color: #8f123f;"
                            [disabled]="actionBusy()"
                            (click)="confirmPayment(currentOrder.id)">
                            @if (currentOrder.secondInstallmentPaymentMethod === 'CASH') {
                              Confirmer l’encaissement des espèces (50 %)
                            } @else if (currentOrder.secondInstallmentPaymentMethod === 'BANK_TRANSFER') {
                              Confirmer le virement du solde (50 %)
                            } @else {
                              Valider le solde (50 %)
                            }
                          </button>
                        </div>
                      }
                    </div>
                  </div>
                </div>

                <!-- CALLOUT : RÉGLER LE SOLDE RESTANT — 50 % -->
                @if (canPayRemainingBalance(currentOrder)) {
                  <div class="balance-cta-callout" id="regler-solde-callout">
                    <div class="cta-callout-header">
                      <div class="cta-callout-info">
                        <span class="callout-tag">Règlement du solde</span>
                        <h3 class="callout-title">Régler le solde restant — 50 %</h3>
                        <p class="callout-desc">
                          Votre premier acompte a été validé. Choisissez votre mode de règlement pour le solde de <strong>{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</strong> : par virement bancaire ou en espèces lors de la réception.
                        </p>
                      </div>
                      <button
                        type="button"
                        class="btn-deposit-second-proof"
                        (click)="toggleBalancePaymentJourney()"
                        [attr.aria-expanded]="balancePaymentJourneyOpen()">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                          <polyline points="17 8 12 3 7 8"></polyline>
                          <line x1="12" y1="3" x2="12" y2="15"></line>
                        </svg>
                        {{ balancePaymentJourneyOpen() ? 'Masquer les options de règlement' : 'Choisir le mode de règlement du solde' }}
                      </button>
                    </div>

                    <!-- ESPACE DÉDIÉ : SÉLECTION DU MODE & INSTRUCTIONS -->
                    @if (balancePaymentJourneyOpen() || currentOrder.secondInstallmentPaymentMethod || currentOrder.secondTransferProofReference) {
                      <div class="balance-journey-container" id="balance-payment-journey">
                        
                        <!-- SÉLECTEUR DE MODE DE RÈGLEMENT DU SOLDE -->
                        <div class="balance-method-selector-box">
                          <label class="selector-title">Mode de règlement choisi pour le solde restant :</label>
                          <div class="method-choice-buttons">
                            <button
                              type="button"
                              class="method-btn"
                              [class.active]="(currentOrder.secondInstallmentPaymentMethod || activeSecondMethodChoice()) === 'BANK_TRANSFER'"
                              [disabled]="selectingSecondMethod()"
                              (click)="chooseSecondInstallmentMethod(currentOrder.id, 'BANK_TRANSFER')">
                              <span class="btn-icon">🏦</span>
                              <div class="btn-text">
                                <strong>Virement bancaire</strong>
                                <small>Virement du solde (50 %) &amp; dépôt du reçu</small>
                              </div>
                            </button>

                            <button
                              type="button"
                              class="method-btn"
                              [class.active]="(currentOrder.secondInstallmentPaymentMethod || activeSecondMethodChoice()) === 'CASH'"
                              [disabled]="selectingSecondMethod()"
                              (click)="chooseSecondInstallmentMethod(currentOrder.id, 'CASH')">
                              <span class="btn-icon">💵</span>
                              <div class="btn-text">
                                <strong>Paiement en espèces</strong>
                                <small>{{ currentOrder.deliveryMethod === 'PICKUP' ? 'Au retrait au showroom (Kénitra)' : 'Au livreur lors de la livraison' }}</small>
                              </div>
                            </button>
                          </div>

                          @if (secondMethodError()) {
                            <p class="upload-feedback error" role="alert">{{ secondMethodError() }}</p>
                          }
                        </div>

                        <!-- OPTION A : VIREMENT BANCAIRE -->
                        @if ((currentOrder.secondInstallmentPaymentMethod || activeSecondMethodChoice()) === 'BANK_TRANSFER') {
                          <div class="journey-heading">
                            <h4>Option A — Virement bancaire &amp; dépôt du reçu du 2ème versement</h4>
                            <p>Suivez les étapes ci-dessous pour régler votre solde de <strong>{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</strong> par virement bancaire.</p>
                          </div>

                          <div class="journey-step-grid">
                            <!-- Étape 1 : Montant exact du solde -->
                            <div class="journey-step-card">
                              <div class="step-badge">1</div>
                              <div class="step-content">
                                <strong>Montant exact du solde restant</strong>
                                <p class="step-amount">{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</p>
                                <small class="step-sub">Montant calculé côté serveur (50 % du montant total restant).</small>
                              </div>
                            </div>

                            <!-- Étape 2 : Coordonnées bancaires officielles -->
                            <div class="journey-step-card">
                              <div class="step-badge">2</div>
                              <div class="step-content">
                                <strong>Coordonnées bancaires officielles WWTRADING</strong>
                                <div class="journey-bank-details">
                                  <div class="journey-bank-header">
                                    <span class="journey-bank-name">{{ currentOrder.paymentBankName || currentOrder.selectedBankReference || 'Banque officielle' }}</span>
                                    <img
                                      [src]="resolveBankLogo(currentOrder.paymentBankLogoUrl, currentOrder.paymentBankName || currentOrder.selectedBankReference)"
                                      [alt]="currentOrder.paymentBankName || 'Logo Banque'"
                                      class="journey-bank-logo"
                                      (error)="onLogoError($event, currentOrder.paymentBankName || currentOrder.selectedBankReference)"
                                    />
                                  </div>
                                  @if (currentOrder.paymentBankRib) {
                                    <p class="journey-rib"><strong>RIB :</strong> <code>{{ currentOrder.paymentBankRib }}</code></p>
                                  }
                                  @if (currentOrder.paymentBankBeneficiary) {
                                    <p class="journey-beneficiary"><strong>Bénéficiaire :</strong> {{ currentOrder.paymentBankBeneficiary }}</p>
                                  }
                                </div>
                              </div>
                            </div>

                            <!-- Étape 3 : Instructions de virement -->
                            <div class="journey-step-card">
                              <div class="step-badge">3</div>
                              <div class="step-content">
                                <strong>Instructions de virement</strong>
                                <p class="instruction-body">
                                  Indiquez impérativement le <strong>numéro de commande</strong> en référence ou motif lors de votre virement :
                                </p>
                                <div class="order-ref-box">
                                  <span>Référence obligatoire :</span>
                                  <code>{{ currentOrder.orderNumber || currentOrder.id }}</code>
                                </div>
                                <p class="instruction-disclaimer">
                                  💡 <em>Rappel :</em> Le paiement du solde par virement avant réception permet d’anticiper la préparation et l’expédition.
                                </p>
                              </div>
                            </div>

                            <!-- Étape 4 : Téléversement du 2ème justificatif -->
                            <div class="journey-step-card">
                              <div class="step-badge">4</div>
                              <div class="step-content">
                                <strong>Dépôt du reçu du 2ème virement</strong>

                                @if (currentOrder.secondTransferProofReference) {
                                  <div class="second-proof-status-card">
                                    <div class="second-proof-status-pill">
                                      <span class="pill-icon">⏳</span>
                                      <span>Reçu du 2ème versement — En attente de validation</span>
                                    </div>
                                    <div class="second-proof-actions">
                                      <button type="button" class="action-btn-link" (click)="previewProof(currentOrder.id, 2)">Consulter</button>
                                      <button type="button" class="action-btn-link" (click)="downloadProof(currentOrder.id, 2)">Télécharger</button>
                                    </div>
                                  </div>
                                  <p class="replace-proof-hint">Vous pouvez déposer un nouveau reçu pour remplacer le fichier précédent :</p>
                                } @else {
                                  <p class="step-sub">Déposez votre reçu de solde pour vérification financière (formats acceptés : JPG, PNG, max 5 Mo).</p>
                                }

                                <div class="second-proof-upload-form">
                                  <input
                                    type="file"
                                    accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                                    (change)="onSecondProofFileSelected($event)"
                                    id="second-proof-input-file"
                                    class="file-upload-input"
                                  />
                                  @if (secondSelectedFile()) {
                                    <div class="upload-confirm-row">
                                      <span class="file-chosen-name">{{ secondSelectedFileName() }}</span>
                                      <button
                                        type="button"
                                        class="btn-confirm-upload"
                                        [disabled]="secondProofUploading()"
                                        (click)="uploadSecondProof(currentOrder.id)">
                                        {{ secondProofUploading() ? 'Téléversement en cours…' : 'Confirmer le dépôt du reçu' }}
                                      </button>
                                    </div>
                                  }
                                </div>

                                @if (secondProofUploadError()) {
                                  <p class="upload-feedback error" role="alert">{{ secondProofUploadError() }}</p>
                                }
                                @if (secondProofSuccessMessage()) {
                                  <p class="upload-feedback success" role="status">{{ secondProofSuccessMessage() }}</p>
                                }
                              </div>
                            </div>
                          </div>
                        }

                        <!-- OPTION B : PAIEMENT EN ESPÈCES -->
                        @if ((currentOrder.secondInstallmentPaymentMethod || activeSecondMethodChoice()) === 'CASH') {
                          <div class="journey-heading">
                            <h4>Option B — Paiement du solde en espèces (50 %)</h4>
                            <p>Vous avez choisi de régler le solde restant de <strong>{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</strong> en espèces directement lors de la réception.</p>
                          </div>

                          <div class="cash-journey-cards-grid">
                            <!-- Carte 1 : Montant en espèces -->
                            <div class="journey-step-card">
                              <div class="step-badge">1</div>
                              <div class="step-content">
                                <strong>Montant exact à préparer en espèces</strong>
                                <p class="step-amount">{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</p>
                                <small class="step-sub">Montant en dirhams marocains (DH), merci de préparer l’appoint exact.</small>
                              </div>
                            </div>

                            <!-- Carte 2 : Modalités de remise en main propre -->
                            <div class="journey-step-card">
                              <div class="step-badge">2</div>
                              <div class="step-content">
                                <strong>
                                  {{ currentOrder.deliveryMethod === 'PICKUP' ? 'Retrait au showroom WWTRADING' : 'Livraison à votre adresse' }}
                                </strong>
                                @if (currentOrder.deliveryMethod === 'PICKUP') {
                                  <p class="instruction-body">
                                    Les espèces devront être remises directement à l’accueil de notre showroom lors de l’enlèvement de votre commande :
                                  </p>
                                  <div class="order-ref-box">
                                    <span>Showroom WWTRADING :</span>
                                    <strong>Parc Industriel Ain Sebâa / Kénitra</strong>
                                    <small>Horaires : Lundi au Vendredi 8h30 - 18h30 · Samedi 9h00 - 13h00</small>
                                  </div>
                                } @else {
                                  <p class="instruction-body">
                                    Les espèces devront être remises en mains propres au chauffeur-livreur lors de la livraison de votre équipement :
                                  </p>
                                  <div class="order-ref-box">
                                    <span>Adresse de livraison :</span>
                                    @if (currentOrder.shippingAddress; as addr) {
                                      <strong>{{ addr.addressLine1 }}{{ addr.addressLine2 ? ', ' + addr.addressLine2 : '' }}</strong>
                                      <small>{{ addr.postalCode }} {{ addr.city }}, {{ addr.country }}</small>
                                    } @else {
                                      <strong>Adresse renseignée sur votre commande</strong>
                                    }
                                  </div>
                                }
                              </div>
                            </div>

                            <!-- Carte 3 : Processus de validation et sécurité -->
                            <div class="journey-step-card">
                              <div class="step-badge">3</div>
                              <div class="step-content">
                                <strong>Validation et encaissement sécurisé</strong>
                                <div class="cash-process-box">
                                  <div class="cash-process-pill">
                                    <span class="pill-icon">💵</span>
                                    <span>Statut : En attente d’encaissement</span>
                                  </div>
                                  <p class="instruction-disclaimer" style="margin-top: 0.5rem;">
                                    ℹ️ <strong>Règle de sécurité :</strong> Le client ne peut pas valider lui-même son propre paiement. Dès remise des fonds, notre agent habilité enregistrera l’encaissement et vous remettra un reçu officiel. Le statut passera alors automatiquement à <strong>« Payé »</strong>.
                                  </p>
                                </div>
                              </div>
                            </div>
                          </div>
                        }

                      </div>
                    }
                  </div>
                }
              }
            </section>

          <div class="grid">
            <section class="panel products-panel"><h2>Produits commandés</h2><div class="products-list">@for (entry of itemViews(); track entry.item.id) { <article class="product-row">@if (entry.image) { <img class="product-image" [src]="imageUrl(entry.image)" [alt]="entry.name"> } @else { <div class="product-image product-image--empty" aria-hidden="true">WW</div> }<div class="product-info"><strong>{{ entry.name }}</strong><small>{{ entry.item.productReference }}</small></div><div class="product-values"><span>Qté {{ entry.item.quantity }}</span><span>{{ entry.item.unitPrice | number:'1.2-2' }} DH / unité</span><strong>{{ entry.item.subtotal | number:'1.2-2' }} DH</strong></div></article> }</div><div class="totals"><p><span>Sous-total</span><strong>{{ currentOrder.subtotal | number:'1.2-2' }} DH</strong></p>@if (currentOrder.discount && currentOrder.discount > 0) { <p><span>Remise du devis</span><strong>− {{ currentOrder.discount | number:'1.2-2' }} DH</strong></p> }<p><span>Frais de livraison</span><strong>{{ currentOrder.deliveryFee | number:'1.2-2' }} DH</strong></p><p class="grand-total"><span>Total final</span><strong>{{ currentOrder.total | number:'1.2-2' }} DH</strong></p></div></section>
            <section class="panel">
              <h2>Livraison et paiement</h2>
              <p><strong>Mode de livraison</strong><br>{{ currentOrder.deliveryMethod === 'PICKUP' ? 'Retrait sur place' : 'Livraison' }}</p>
              @if (deliveryAreaLabel(currentOrder.deliveryArea); as areaText) {
                <p><strong>Zone de livraison</strong><br>{{ areaText }}</p>
              }
              @if (deliveryFeeWaivedLabel(currentOrder.deliveryFeeWaivedReason); as waivedText) {
                <p><strong>Exonération des frais de livraison</strong><br><span style="color: #286235; font-weight: 700;">{{ waivedText }}</span></p>
              }
              <p><strong>Paiement</strong><br>{{ paymentPlanLabel(currentOrder.paymentPlan, currentOrder.paymentMethod, currentOrder.deliveryMethod) }}</p>
              <p><strong>Statut paiement</strong><br>{{ paymentStatusLabel(currentOrder.paymentStatus) }} ({{ currentOrder.paymentStatus }})</p>
              <div class="payment-summary-box" style="margin: 0.8rem 0; padding: 0.85rem; background: #f8f6f5; border: 1px solid #e5e0dc; border-radius: 6px;">
                <strong>Suivi financier</strong>
                <p style="margin: 0.35rem 0; display: flex; justify-content: space-between;"><span>Total commande :</span><strong>{{ currentOrder.total | number:'1.2-2' }} DH</strong></p>
                @if (currentOrder.paymentPlan === 'SPLIT_50_50' || currentOrder.paymentMethod === 'SPLIT_50_50') {
                  <p style="margin: 0.35rem 0; display: flex; justify-content: space-between;">
                    <span>Acompte 50 % à la commande :</span>
                    <strong>{{ (currentOrder.upfrontPaymentAmount ?? (currentOrder.total / 2)) | number:'1.2-2' }} DH</strong>
                  </p>
                  <p style="margin: 0.35rem 0; display: flex; justify-content: space-between;">
                    <span>Solde 50 % à la livraison :</span>
                    <strong>{{ (currentOrder.remainingDeliveryAmount ?? (currentOrder.total / 2)) | number:'1.2-2' }} DH</strong>
                  </p>
                }
                <p style="margin: 0.35rem 0; display: flex; justify-content: space-between;">
                  <span>Montant payé :</span>
                  <strong>{{ paidAmount(currentOrder) | number:'1.2-2' }} DH</strong>
                </p>
                <p style="margin: 0.35rem 0; display: flex; justify-content: space-between;">
                  <span>Reste à payer :</span>
                  <strong>{{ remainingBalance(currentOrder) | number:'1.2-2' }} DH</strong>
                </p>
                @if (currentOrder.firstInstallmentConfirmedAt) {
                  <small>1er versement (acompte 50 %) confirmé le {{ currentOrder.firstInstallmentConfirmedAt | date:'dd/MM/yyyy HH:mm' }}</small>
                }
                @if (currentOrder.secondInstallmentConfirmedAt) {
                  <small>2e versement (solde 50 %) confirmé le {{ currentOrder.secondInstallmentConfirmedAt | date:'dd/MM/yyyy HH:mm' }}</small>
                }
              </div>
              @if (currentOrder.paymentBankName || currentOrder.selectedBankReference) {
                <div class="bank-details-box" style="margin: 0.8rem 0; padding: 1rem; background: #fbf9f8; border: 1px solid #e5e0dc; border-radius: 8px;">
                  <div style="display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; margin-bottom: 0.75rem;">
                    <strong style="color: #1a1a18; font-size: 0.95rem;">Informations de virement</strong>
                    <img
                      [src]="resolveBankLogo(currentOrder.paymentBankLogoUrl, currentOrder.paymentBankName || currentOrder.selectedBankReference)"
                      [alt]="currentOrder.paymentBankName || currentOrder.selectedBankReference || 'Logo Banque'"
                      style="height: 32px; max-width: 120px; object-fit: contain; display: block; border-radius: 4px; padding: 2px 4px; background: #fff; border: 1px solid #ece8e7;"
                      (error)="onLogoError($event, currentOrder.paymentBankName || currentOrder.selectedBankReference)"
                    />
                  </div>
                  <p style="margin: 0.3rem 0; font-size: 0.9rem;"><strong>Banque :</strong> {{ currentOrder.paymentBankName || currentOrder.selectedBankReference }}</p>
                  @if (currentOrder.paymentBankRib) {
                    <p style="margin: 0.3rem 0; font-size: 0.9rem;"><strong>RIB :</strong> <code style="font-family: monospace; font-size: 0.92rem; background: #f1eeeb; padding: 2px 6px; border-radius: 4px; letter-spacing: 0.5px;">{{ currentOrder.paymentBankRib }}</code></p>
                  }
                  @if (currentOrder.paymentBankBeneficiary) {
                    <p style="margin: 0.3rem 0; font-size: 0.9rem;"><strong>Bénéficiaire :</strong> {{ currentOrder.paymentBankBeneficiary }}</p>
                  }
                </div>
              }

              <!-- SECTION DES PREUVES DE VIREMENT (POUR VIREMENT STANDARD NON FRACTIONNÉ) -->
              @if (!isSplitPayment(currentOrder) && (currentOrder.paymentMethod === 'BANK_TRANSFER' || currentOrder.transferProofReference)) {
                <div class="proof-details-box" style="margin: 0.8rem 0; padding: 1rem; background: #fff; border: 1px solid #e5e0dc; border-radius: 8px;">
                  <div style="display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; margin-bottom: 0.75rem;">
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#8f123f" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                        <polyline points="14 2 14 8 20 8"></polyline>
                        <line x1="16" y1="13" x2="8" y2="13"></line>
                        <line x1="16" y1="17" x2="8" y2="17"></line>
                        <polyline points="10 9 9 9 8 9"></polyline>
                      </svg>
                      <strong style="color: #1a1a18; font-size: 0.95rem;">Preuve de virement bancaire</strong>
                    </div>
                  </div>

                  <!-- CARTE DU REÇU UNIQUE -->
                  <div style="margin-bottom: 0.5rem; padding: 0.75rem; background: #fbf9f8; border: 1px solid #ece8e7; border-radius: 6px;">
                    <div style="display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; margin-bottom: 0.5rem;">
                      <span style="font-size: 0.88rem; font-weight: 750; color: #1a1a18;">Reçu de virement</span>
                      @if (currentOrder.transferProofReference) {
                        <span style="display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.15rem 0.55rem; border-radius: 999px; font-size: 0.72rem; font-weight: 750; background: #eef6ef; color: #286235; border: 1px solid #8dad91;">
                          ✓ Reçu enregistré
                        </span>
                      } @else {
                        <span style="padding: 0.15rem 0.55rem; border-radius: 999px; font-size: 0.72rem; font-weight: 750; background: #f8f6f5; color: #77716d; border: 1px solid #d7d0d2;">
                          Reçu non fourni
                        </span>
                      }
                    </div>

                    @if (currentOrder.transferProofReference) {
                      <div style="border: 1px solid #f0cad8; border-radius: 6px; overflow: hidden; background: #fff;">
                        @if (proofPreviewUrl(); as previewUrl) {
                          <div
                            class="proof-thumbnail-wrapper"
                            (click)="previewProof(currentOrder.id, 1)"
                            title="Cliquer pour agrandir"
                            style="position: relative; background: #fbf9f8; display: flex; justify-content: center; align-items: center; min-height: 120px; max-height: 180px; overflow: hidden; cursor: pointer; border-bottom: 1px solid #f0cad8;">
                            <img
                              [src]="previewUrl"
                              alt="Reçu de virement"
                              style="width: 100%; height: auto; max-height: 180px; object-fit: contain; display: block; padding: 6px;"
                            />
                            <div class="proof-thumbnail-hover-overlay">
                              <span style="display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.65rem; border-radius: 999px; background: rgba(0,0,0,0.75); color: #fff; font-size: 0.78rem; font-weight: 700;">
                                🔍 Agrandir l'image
                              </span>
                            </div>
                          </div>
                        } @else if (previewLoading()) {
                          <div style="display: flex; align-items: center; justify-content: center; padding: 1.5rem 1rem; color: #625f5c; font-size: 0.82rem; background: #fdfafb; border-bottom: 1px solid #f0cad8;">
                            Chargement de l'aperçu…
                          </div>
                        } @else {
                          <div style="display: flex; align-items: center; justify-content: center; padding: 1rem; color: #625f5c; font-size: 0.82rem; background: #fdfafb; border-bottom: 1px solid #f0cad8;">
                            <span>Reçu de virement disponible</span>
                          </div>
                        }

                        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.5rem; padding: 0.6rem 0.85rem; background: #fdfafb;">
                          <p style="margin: 0; font-size: 0.76rem; color: #625f5c;">
                            Fichier : <code style="background: #f1eeeb; padding: 1px 4px; border-radius: 4px;">{{ proofFileName(currentOrder) }}</code>
                          </p>
                          <div style="display: flex; gap: 0.4rem;">
                            <button
                              type="button"
                              class="action"
                              style="margin: 0; padding: 0.35rem 0.75rem; font-size: 0.8rem; background: #8f123f; border-color: #8f123f;"
                              [disabled]="previewLoading()"
                              (click)="previewProof(currentOrder.id, 1)">
                              {{ previewLoading() ? 'Chargement…' : 'Consulter' }}
                            </button>
                            <button
                              type="button"
                              class="cancel"
                              style="margin: 0; padding: 0.35rem 0.75rem; font-size: 0.8rem;"
                              [disabled]="proofDownloading()"
                              (click)="downloadProof(currentOrder.id, 1)">
                              {{ proofDownloading() ? 'Téléchargement…' : 'Télécharger' }}
                            </button>
                          </div>
                        </div>
                      </div>
                    } @else {
                      @if (isClient() && currentOrder.status !== 'CANCELLED' && currentOrder.paymentStatus !== 'PAID') {
                        <div style="margin-top: 0.4rem;">
                          <p style="margin: 0 0 0.4rem; font-size: 0.82rem; color: #625f5c;">
                            Joignez votre reçu pour accélérer le traitement (JPG, PNG, max 5 Mo).
                          </p>
                          <div style="display: flex; flex-direction: column; gap: 0.4rem;">
                            <input
                              type="file"
                              accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                              (change)="onProofFileSelected($event)"
                              style="font-size: 0.82rem;"
                            />
                            @if (selectedFile()) {
                              <div style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
                                <span style="font-size: 0.8rem; color: #1a1a18;">{{ selectedFileName() }}</span>
                                <button
                                  type="button"
                                  class="action"
                                  style="margin: 0; padding: 0.35rem 0.75rem; font-size: 0.8rem; background: #8f123f; border-color: #8f123f;"
                                  [disabled]="proofUploading()"
                                  (click)="uploadProof(currentOrder.id)">
                                  {{ proofUploading() ? 'Envoi…' : 'Téléverser le reçu' }}
                                </button>
                              </div>
                            }
                          </div>
                        </div>
                      }
                    }

                    @if (proofUploadError()) {
                      <p style="margin: 0.4rem 0 0; color: #8f123f; font-size: 0.8rem; font-weight: 700;" role="alert">{{ proofUploadError() }}</p>
                    }
                    @if (proofSuccessMessage()) {
                      <p style="margin: 0.4rem 0 0; color: #286235; font-size: 0.8rem; font-weight: 700;" role="status">{{ proofSuccessMessage() }}</p>
                    }
                  </div>
                </div>
              }
              @if (currentOrder.shippingAddress; as shippingAddress) {
                <address>
                  <strong>{{ shippingAddress.firstName }} {{ shippingAddress.lastName }}</strong>
                  @if (shippingAddress.company) { <br><span>{{ shippingAddress.company }}</span> }
                  @if (shippingAddress.phone) { <br><span>Tél : {{ shippingAddress.phone }}</span> }
                  <br>{{ shippingAddress.addressLine1 }}@if (shippingAddress.addressLine2) {, {{ shippingAddress.addressLine2 }} }
                  <br>{{ shippingAddress.postalCode }} {{ shippingAddress.city }}
                  <br>{{ shippingAddress.country }}
                </address>
              }
            </section>
          </div>

          @if (isStaffView()) {
            <section class="panel" style="margin-top: 1rem;" aria-label="Notifications et suivi invité">
              <h2>Notifications (E-mail &amp; WhatsApp) &amp; Lien de suivi</h2>
              @if (notificationDeliveries(); as deliveries) {
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; margin-bottom: 1rem;">
                  <div style="padding: 0.85rem; background: #f8f6f5; border: 1px solid #e5e0dc; border-radius: 6px;">
                    <strong>E-mail client</strong>
                    <p style="margin: 0.35rem 0;">Statut : <strong>{{ deliveries.overallEmailStatus }}</strong></p>
                    @if (deliveries.lastEmailError) {
                      <small style="color: #8f123f;">Dernière erreur : {{ deliveries.lastEmailError }}</small>
                    }
                    <button
                      type="button"
                      class="cancel"
                      style="margin-top: 0.5rem; padding: 0.45rem 0.85rem; font-size: 0.82rem;"
                      [disabled]="retryingNotifications()"
                      (click)="retryNotifications(currentOrder.id, 'EMAIL')">
                      Relancer l'e-mail
                    </button>
                  </div>
                  <div style="padding: 0.85rem; background: #f8f6f5; border: 1px solid #e5e0dc; border-radius: 6px;">
                    <strong>WhatsApp client</strong>
                    <p style="margin: 0.35rem 0;">Statut : <strong>{{ deliveries.overallWhatsAppStatus }}</strong></p>
                    @if (deliveries.lastWhatsAppError) {
                      <small style="color: #625f5c;">Info / erreur : {{ deliveries.lastWhatsAppError }}</small>
                    }
                    <button
                      type="button"
                      class="cancel"
                      style="margin-top: 0.5rem; padding: 0.45rem 0.85rem; font-size: 0.82rem;"
                      [disabled]="retryingNotifications()"
                      (click)="retryNotifications(currentOrder.id, 'WHATSAPP')">
                      Relancer WhatsApp
                    </button>
                  </div>
                </div>
                @if (notificationActionMessage(); as notifMsg) {
                  <p style="color: #286235; font-weight: 700;" role="status">{{ notifMsg }}</p>
                }
              } @else {
                <p class="meta">Statuts de notification en cours de chargement ou indisponibles.</p>
              }

              @if (currentOrder.guestOrder) {
                <div style="margin-top: 1rem; padding-top: 1rem; border-top: 1px solid #e5e0dc;">
                  <strong>Sécurité du lien de suivi invité</strong>
                  <div style="display: flex; flex-wrap: wrap; gap: 0.6rem; margin-top: 0.5rem;">
                    <button
                      type="button"
                      class="cancel"
                      style="margin-top: 0; padding: 0.45rem 0.85rem; font-size: 0.82rem;"
                      [disabled]="actionBusy()"
                      (click)="regenerateTrackingToken(currentOrder.id)">
                      Régénérer le jeton de suivi
                    </button>
                    <button
                      type="button"
                      class="cancel"
                      style="margin-top: 0; padding: 0.45rem 0.85rem; font-size: 0.82rem;"
                      [disabled]="actionBusy()"
                      (click)="revokeTrackingToken(currentOrder.id)">
                      Révoquer le jeton de suivi
                    </button>
                  </div>
                  @if (regeneratedTrackingUrl(); as trackingUrl) {
                    <p style="margin: 0.55rem 0 0; font-size: 0.85rem;">
                      Nouveau lien de suivi : <a [href]="trackingUrl" target="_blank" rel="noopener">{{ trackingUrl }}</a>
                    </p>
                  }
                </div>
              }
            </section>
          }
        }

        <!-- MODAL D'APERÇU SÉCURISÉ POUR LES PREUVES DE VIREMENT -->
        @if (previewModalOpen()) {
          <div class="proof-modal-overlay" (click)="closeProofModal()">
            <div class="proof-modal-card" (click)="$event.stopPropagation()">
              <div class="proof-modal-header">
                <h3 style="margin: 0; font-size: 1.1rem; color: #1a1a18;">{{ previewModalTitle() }}</h3>
                <button type="button" class="proof-modal-close" (click)="closeProofModal()" aria-label="Fermer">✕</button>
              </div>
              <div class="proof-modal-body">
                @if (activeModalUrl()) {
                  <img [src]="activeModalUrl()" [alt]="previewModalTitle()" class="proof-modal-image" />
                }
              </div>
              <div class="proof-modal-footer">
                <button type="button" class="cancel" (click)="closeProofModal()" style="margin: 0;">Fermer</button>
                <button type="button" class="action" (click)="downloadActiveModalProof()" style="margin: 0; background: #8f123f; border-color: #8f123f;">Télécharger</button>
              </div>
            </div>
          </div>
        }
      }
    </section>
    </wwt-dashboard-layout>
  `,
  styles: [`
    :host { display: block; }
    .detail { max-width: 1180px; min-height: 60vh; margin: 0 auto; padding: 0 clamp(1rem, 3vw, 2rem) clamp(2rem, 5vw, 4rem); }
    .back { color: #8f123f; font-weight: 750; text-underline-offset: .2em; }
    .eyebrow { margin: 2rem 0 .5rem; color: #af0f50; font-size: .76rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    h1 { margin: 0; color: #1a1a18; font-size: clamp(2rem, 5vw, 4rem); overflow-wrap: anywhere; }
    h2 { margin-top: 0; color: #1a1a18; }
    .meta { color: #625f5c; }
    .order-progress { margin-top: 1.5rem; padding: clamp(1.1rem, 3vw, 1.6rem); border: 1px solid rgb(26 26 24 / 12%); background: #fff; box-shadow: 0 .7rem 2rem rgb(26 26 24 / 5%); }
    .progress-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding-bottom: 1.2rem; border-bottom: 1px solid rgb(26 26 24 / 10%); }
    .progress-heading h2 { margin: 0; font-size: clamp(1.25rem, 3vw, 1.65rem); }
    .progress-kicker { margin: 0 0 .25rem; color: #8f123f; font-size: .72rem; font-weight: 850; letter-spacing: .1em; text-transform: uppercase; }
    .payment-status { display: grid; gap: .2rem; min-width: 10rem; margin: 0; padding: .65rem .8rem; border: 1px solid #d7d0d2; background: #f8f6f5; }
    .payment-status span { color: #625f5c; font-size: .7rem; font-weight: 750; letter-spacing: .06em; text-transform: uppercase; }
    .payment-status strong { color: #8f123f; }
    .payment-status--paid { border-color: #8dad91; background: #eef6ef; }
    .payment-status--paid strong { color: #286235; }
    .payment-status--partial { border-color: #d6a756; background: #fdf7eb; }
    .payment-status--partial strong { color: #8c5a08; }
    .progress-steps { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin: 1.5rem 0 0; padding: 0; list-style: none; }
    .progress-step { position: relative; display: grid; grid-template-columns: 2.5rem minmax(0, 1fr); gap: .75rem; align-items: center; color: #88827e; }
    .progress-step:not(:last-child)::after { position: absolute; z-index: 0; top: 1.2rem; left: 2.5rem; width: calc(100% - 2.5rem); height: 2px; background: #ddd7d4; content: ''; }
    .progress-step--completed:not(:last-child)::after { background: #af0f50; }
    .step-marker { z-index: 1; display: grid; width: 2.5rem; height: 2.5rem; place-items: center; border: 2px solid #d7d0d2; border-radius: 50%; background: #fff; color: #77716d; font-weight: 850; }
    .step-copy { z-index: 1; display: grid; gap: .15rem; width: fit-content; padding-right: .65rem; background: #fff; }
    .step-copy strong { color: #77716d; font-size: .92rem; }
    .step-copy small { font-size: .72rem; }
    .progress-step--completed .step-marker { border-color: #af0f50; background: #af0f50; color: #fff; }
    .progress-step--completed .step-copy strong { color: #1a1a18; }
    .progress-step--current .step-marker { border-color: #af0f50; color: #af0f50; box-shadow: 0 0 0 .3rem rgb(175 15 80 / 10%); }
    .progress-step--current .step-copy strong, .progress-step--current .step-copy small { color: #8f123f; }
    .cancelled-status { display: flex; align-items: center; gap: .9rem; margin-top: 1.2rem; padding: 1rem; border: 1px solid #b94343; background: #fff1f1; color: #7d2424; }
    .cancelled-mark { display: grid; flex: 0 0 auto; width: 2.5rem; height: 2.5rem; place-items: center; border-radius: 50%; background: #a83232; color: #fff; font-size: 1.55rem; line-height: 1; }
    .cancelled-status p { margin: .2rem 0 0; color: #7d4242; }
    .cancel { margin-top: .75rem; padding: .7rem 1rem; border: 1px solid #af0f50; border-radius: 999px; background: #fff; color: #8f123f; font: inherit; font-weight: 750; }
    .action { margin: .75rem .5rem 0 0; padding: .7rem 1rem; border: 1px solid #1a1a18; border-radius: 999px; background: #1a1a18; color: #fff; font: inherit; font-weight: 750; }
    .cancel:disabled { cursor: not-allowed; opacity: .55; }
    .cancel-error { color: #8f123f; font-weight: 700; }
    .grid { display: grid; grid-template-columns: 1.3fr 1fr; gap: 1rem; margin-top: 2rem; }
    .panel, .state { padding: 1.5rem; border: 1px solid rgb(26 26 24 / 12%); background: #fff; }

    /* NOUVELLE SECTION PAIEMENT ET SOLDE RESTANT */
    .balance-section {
      margin-top: 1.5rem;
      border-radius: 10px;
      border: 1px solid #e5e0dc;
      background: #ffffff;
      box-shadow: 0 4px 16px rgba(26, 26, 24, 0.04);
    }
    .balance-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 1rem;
      padding-bottom: 1rem;
      border-bottom: 1px solid #f0ece9;
    }
    .balance-title-group h2 {
      margin: 0;
      font-size: 1.35rem;
      color: #1a1a18;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .balance-subtitle {
      margin: 0.25rem 0 0;
      font-size: 0.85rem;
      color: #625f5c;
    }
    .balance-status-badge {
      display: inline-flex;
      align-items: center;
      padding: 0.35rem 0.85rem;
      border-radius: 999px;
      font-size: 0.82rem;
      font-weight: 750;
      background: #f8f6f5;
      color: #77716d;
      border: 1px solid #d7d0d2;
    }
    .balance-status-badge.badge-paid {
      background: #eef6ef;
      color: #286235;
      border-color: #8dad91;
    }
    .balance-status-badge.badge-partial {
      background: #fdf7eb;
      color: #8c5a08;
      border-color: #d6a756;
    }
    .balance-metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 0.85rem;
      margin-top: 1.2rem;
    }
    .balance-metric-card {
      padding: 0.9rem 1rem;
      border-radius: 8px;
      background: #fbf9f8;
      border: 1px solid #eae5e3;
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
    }
    .balance-metric-card .metric-label {
      font-size: 0.72rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #77716d;
    }
    .balance-metric-card .metric-value {
      font-size: 1.25rem;
      font-weight: 800;
      color: #1a1a18;
    }
    .balance-metric-card.highlight {
      background: #fdf3f7;
      border-color: #f0cad8;
    }
    .balance-metric-card.highlight .metric-value {
      color: #8f123f;
    }
    .balance-metric-card .text-success {
      color: #286235;
    }
    .balance-metric-card .metric-status-text {
      font-size: 0.88rem;
      font-weight: 700;
      color: #1a1a18;
      line-height: 1.35;
    }
    .balance-metric-card .metric-reception {
      font-size: 1rem;
      font-weight: 750;
      color: #1a1a18;
    }
    .balance-cta-bar {
      margin-top: 1.25rem;
      padding-top: 1.1rem;
      border-top: 1px dashed #e5e0dc;
      display: flex;
      align-items: center;
      gap: 1.2rem;
      flex-wrap: wrap;
    }
    .btn-pay-balance {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.75rem 1.4rem;
      background: #8f123f;
      color: #ffffff;
      border: 1px solid #8f123f;
      border-radius: 999px;
      font-size: 0.95rem;
      font-weight: 750;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(143, 18, 63, 0.22);
      transition: all 0.2s ease;
    }
    .btn-pay-balance:hover {
      background: #730e32;
      box-shadow: 0 6px 18px rgba(143, 18, 63, 0.32);
      transform: translateY(-1px);
    }
    .balance-cta-hint {
      font-size: 0.85rem;
      color: #625f5c;
      flex: 1;
      min-width: 220px;
    }

    /* DEUX BLOCS INDÉPENDANTS : 1ER & 2ND VERSEMENT (50% + 50%) */
    .split-installments-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 1.25rem;
      margin-top: 1.35rem;
    }
    .installment-block {
      border: 1.5px solid #e5e0dc;
      border-radius: 10px;
      padding: 1.25rem;
      background: #ffffff;
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
      box-shadow: 0 2px 8px rgba(26, 26, 24, 0.03);
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }
    .installment-block:hover {
      box-shadow: 0 4px 14px rgba(26, 26, 24, 0.06);
    }
    .installment-block--confirmed {
      border-color: #8dad91;
      background: #fafdfa;
    }
    .installment-block-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.75rem;
      flex-wrap: wrap;
    }
    .installment-num-badge {
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      background: #8f123f;
      color: #fff;
      font-weight: 850;
      font-size: 0.9rem;
      display: grid;
      place-items: center;
      flex-shrink: 0;
    }
    .installment-block--confirmed .installment-num-badge {
      background: #286235;
    }
    .installment-header-text {
      flex: 1;
      min-width: 160px;
    }
    .installment-block-title {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 800;
      color: #1a1a18;
    }
    .installment-block-amount {
      display: block;
      font-size: 1.35rem;
      font-weight: 850;
      color: #8f123f;
      margin-top: 0.25rem;
    }
    .installment-block--confirmed .installment-block-amount {
      color: #286235;
    }
    .installment-status-badge {
      padding: 0.25rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 750;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .badge-status--confirmed {
      background: #eef6ef;
      color: #286235;
      border: 1px solid #8dad91;
    }
    .badge-status--pending {
      background: #fdf7eb;
      color: #8c5a08;
      border: 1px solid #d6a756;
    }
    .badge-status--missing {
      background: #f8f6f5;
      color: #77716d;
      border: 1px solid #d7d0d2;
    }
    .installment-confirmed-date {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.82rem;
      font-weight: 750;
      color: #286235;
      background: #f2f8f3;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      border: 1px solid #c3dec6;
    }
    .installment-pending-date {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.82rem;
      font-weight: 750;
      color: #8c5a08;
      background: #fdf7eb;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      border: 1px solid #d6a756;
    }
    .confirmed-check-icon {
      font-weight: 850;
      font-size: 0.95rem;
    }
    .pending-icon {
      font-size: 0.9rem;
    }
    .installment-proof-container {
      margin-top: 0.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .proof-subheading {
      font-size: 0.82rem;
      font-weight: 750;
      color: #625f5c;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .proof-card {
      border: 1px solid #f0cad8;
      border-radius: 8px;
      overflow: hidden;
      background: #fff;
    }
    .proof-thumb-img {
      width: 100%;
      height: auto;
      max-height: 180px;
      object-fit: contain;
      display: block;
      padding: 8px;
      background: #fbf9f8;
    }
    .proof-loading-placeholder, .proof-ready-placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.25rem 1rem;
      color: #625f5c;
      font-size: 0.82rem;
      background: #fdfafb;
    }
    .proof-file-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 0.5rem;
      padding: 0.6rem 0.85rem;
      background: #fdfafb;
      border-top: 1px solid #f0cad8;
    }
    .proof-file-name {
      font-size: 0.76rem;
      color: #625f5c;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 180px;
    }
    .proof-file-actions {
      display: flex;
      gap: 0.4rem;
    }
    .btn-proof-action {
      padding: 0.35rem 0.75rem;
      font-size: 0.8rem;
      font-weight: 750;
      border-radius: 999px;
      cursor: pointer;
      border: 1px solid transparent;
      transition: all 0.15s ease;
    }
    .btn-proof-preview {
      background: #8f123f;
      color: #fff;
      border-color: #8f123f;
    }
    .btn-proof-preview:hover {
      background: #730e32;
    }
    .btn-proof-download {
      background: #fff;
      color: #8f123f;
      border-color: #af0f50;
    }
    .btn-proof-download:hover {
      background: #fdf3f7;
    }
    .proof-empty-notice {
      padding: 1rem;
      background: #fbf9f8;
      border: 1px dashed #d7d0d2;
      border-radius: 8px;
      text-align: center;
      color: #77716d;
      font-size: 0.85rem;
    }
    .proof-empty-notice p {
      margin: 0 0 0.35rem;
    }
    .installment-upload-form {
      margin-top: 0.4rem;
      text-align: left;
    }

    /* ACTION CALLOUT : RÉGLER LE SOLDE RESTANT — 50 % */
    .balance-cta-callout {
      margin-top: 1.5rem;
      padding: 1.35rem;
      background: linear-gradient(135deg, #fff7fa 0%, #fef4f8 100%);
      border: 1.5px solid #f0cad8;
      border-radius: 10px;
      box-shadow: 0 4px 14px rgba(143, 18, 63, 0.06);
    }
    .cta-callout-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.25rem;
      flex-wrap: wrap;
    }
    .cta-callout-info {
      flex: 1;
      min-width: 260px;
    }
    .callout-tag {
      display: inline-block;
      font-size: 0.72rem;
      font-weight: 850;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #af0f50;
      background: #fff;
      padding: 0.2rem 0.55rem;
      border-radius: 4px;
      border: 1px solid #f0cad8;
      margin-bottom: 0.35rem;
    }
    .callout-title {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 800;
      color: #1a1a18;
    }
    .callout-desc {
      margin: 0.35rem 0 0;
      font-size: 0.9rem;
      color: #4b4845;
      line-height: 1.45;
      max-width: 700px;
    }
    .btn-deposit-second-proof {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1.4rem;
      border-radius: 999px;
      background: #8f123f;
      color: #fff;
      border: none;
      font-size: 0.92rem;
      font-weight: 750;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(143, 18, 63, 0.25);
      transition: all 0.2s ease;
      white-space: nowrap;
    }
    .btn-deposit-second-proof:hover {
      background: #730e32;
      box-shadow: 0 6px 18px rgba(143, 18, 63, 0.35);
      transform: translateY(-1px);
    }

    /* PARCOURS DE PAIEMENT DU SOLDE */
    .balance-journey-container {
      margin-top: 1.25rem;
      padding: 1.25rem;
      background: #fbf9f8;
      border: 1.5px solid #ebdcd9;
      border-radius: 10px;
      animation: fadeInJourney 0.25s ease-out;
    }
    @keyframes fadeInJourney {
      from { opacity: 0; transform: translateY(-6px); }
      to { opacity: 1; transform: translateY(0); }
    }
    .journey-heading h3 {
      margin: 0;
      font-size: 1.15rem;
      color: #8f123f;
    }
    .journey-heading p {
      margin: 0.25rem 0 1rem;
      font-size: 0.88rem;
      color: #4b4845;
    }
    .journey-step-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 1rem;
    }
    .journey-step-card {
      position: relative;
      background: #ffffff;
      border: 1px solid #e7e2df;
      border-radius: 8px;
      padding: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .step-badge {
      width: 1.6rem;
      height: 1.6rem;
      border-radius: 50%;
      background: #8f123f;
      color: #fff;
      font-weight: 800;
      font-size: 0.82rem;
      display: grid;
      place-items: center;
      margin-bottom: 0.2rem;
    }
    .step-content strong {
      display: block;
      color: #1a1a18;
      font-size: 0.95rem;
      margin-bottom: 0.35rem;
    }
    .step-amount {
      font-size: 1.5rem;
      font-weight: 850;
      color: #8f123f;
      margin: 0.3rem 0;
    }
    .step-sub {
      font-size: 0.8rem;
      color: #625f5c;
      margin: 0;
    }
    .journey-bank-details {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      margin-top: 0.4rem;
      font-size: 0.86rem;
    }
    .journey-bank-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      margin-bottom: 0.2rem;
    }
    .journey-bank-name {
      font-weight: 750;
      color: #1a1a18;
    }
    .journey-bank-logo {
      height: 28px;
      max-width: 100px;
      object-fit: contain;
      border-radius: 4px;
      padding: 2px 4px;
      background: #fff;
      border: 1px solid #ece8e7;
    }
    .journey-rib code {
      font-family: monospace;
      font-size: 0.88rem;
      background: #f1eeeb;
      padding: 2px 6px;
      border-radius: 4px;
      letter-spacing: 0.5px;
      word-break: break-all;
    }
    .journey-beneficiary {
      color: #4b4845;
      margin: 0;
    }
    .instruction-body {
      font-size: 0.86rem;
      color: #4b4845;
      margin: 0;
    }
    .order-ref-box {
      margin: 0.5rem 0;
      padding: 0.6rem 0.8rem;
      background: #fdf3f7;
      border: 1px solid #f0cad8;
      border-radius: 6px;
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }
    .order-ref-box span {
      font-size: 0.75rem;
      font-weight: 750;
      color: #8f123f;
      text-transform: uppercase;
    }
    .order-ref-box code {
      font-family: monospace;
      font-size: 1.05rem;
      font-weight: 800;
      color: #1a1a18;
      letter-spacing: 0.5px;
    }
    .instruction-disclaimer {
      font-size: 0.78rem;
      color: #77716d;
      margin: 0;
      line-height: 1.35;
    }
    .second-proof-status-card {
      padding: 0.65rem 0.8rem;
      border-radius: 6px;
      background: #eef6ef;
      border: 1px solid #8dad91;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-bottom: 0.6rem;
    }
    .second-proof-status-pill {
      display: flex;
      align-items: flex-start;
      gap: 0.45rem;
      font-size: 0.82rem;
      font-weight: 700;
      color: #286235;
      line-height: 1.35;
    }
    .pill-icon {
      font-weight: 850;
      font-size: 0.95rem;
    }
    .second-proof-actions {
      display: flex;
      gap: 0.6rem;
    }
    .action-btn-link {
      background: none;
      border: none;
      color: #8f123f;
      font-weight: 750;
      font-size: 0.8rem;
      cursor: pointer;
      padding: 0;
      text-decoration: underline;
    }
    .action-btn-link:hover {
      color: #5d0c29;
    }
    .replace-proof-hint {
      font-size: 0.78rem;
      color: #625f5c;
      margin: 0.25rem 0 0.4rem;
    }
    .second-proof-upload-form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-top: 0.25rem;
    }
    .file-upload-input {
      font-size: 0.82rem;
    }
    .upload-confirm-row {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      flex-wrap: wrap;
      margin-top: 0.25rem;
    }
    .file-chosen-name {
      font-size: 0.8rem;
      color: #1a1a18;
      font-weight: 600;
    }
    .btn-confirm-upload {
      padding: 0.45rem 0.9rem;
      font-size: 0.82rem;
      font-weight: 750;
      border-radius: 999px;
      background: #8f123f;
      color: #fff;
      border: 1px solid #8f123f;
      cursor: pointer;
      transition: background 0.2s ease;
    }
    .btn-confirm-upload:hover {
      background: #730e32;
    }
    .btn-confirm-upload:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .upload-feedback {
      margin: 0.4rem 0 0;
      font-size: 0.82rem;
      font-weight: 700;
    }
    .upload-feedback.error {
      color: #8f123f;
    }
    .upload-feedback.success {
      color: #286235;
    }
    .installment-method-pill-box {
      margin: 0.35rem 0 0.5rem;
      font-size: 0.82rem;
      color: #625f5c;
      display: flex;
      align-items: center;
      gap: 0.4rem;
      flex-wrap: wrap;
    }
    .method-label {
      font-weight: 700;
      color: #77716d;
    }
    .method-value {
      padding: 0.15rem 0.55rem;
      background: #f1eeeb;
      border-radius: 4px;
      font-weight: 750;
      color: #1a1a18;
    }
    .method-value.method-set {
      background: #fdf3f7;
      color: #8f123f;
      border: 1px solid #ebdcd9;
    }
    .installment-pending-date--cash {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0.5rem 0;
      padding: 0.45rem 0.75rem;
      border-radius: 6px;
      background: #fdf7eb;
      border: 1px solid #d6a756;
      color: #8c5a08;
      font-weight: 700;
      font-size: 0.82rem;
    }
    .cash-instructions-card {
      padding: 0.85rem;
      background: #fbfdfa;
      border: 1px solid #c8e2cb;
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
    }
    .cash-card-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: #286235;
      font-size: 0.9rem;
    }
    .cash-instructions-text {
      margin: 0;
      font-size: 0.84rem;
      color: #3b5c3f;
      line-height: 1.45;
    }
    .cash-card-badge {
      font-size: 0.74rem;
      font-weight: 750;
      color: #286235;
      background: #eef6ef;
      padding: 0.25rem 0.6rem;
      border-radius: 4px;
      width: fit-content;
    }
    .balance-method-selector-box {
      margin-bottom: 1.25rem;
      padding: 1rem;
      background: #ffffff;
      border: 1px solid #e7e2df;
      border-radius: 8px;
    }
    .selector-title {
      display: block;
      font-size: 0.84rem;
      font-weight: 800;
      color: #1a1a18;
      margin-bottom: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .method-choice-buttons {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 0.85rem;
    }
    .method-btn {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.9rem 1.1rem;
      background: #faf8f7;
      border: 2px solid #e5e0dc;
      border-radius: 8px;
      cursor: pointer;
      text-align: left;
      transition: all 0.2s ease;
      font: inherit;
    }
    .method-btn:hover:not(:disabled) {
      border-color: #8f123f;
      background: #fff;
      transform: translateY(-1px);
    }
    .method-btn.active {
      border-color: #8f123f;
      background: #fdf5f8;
      box-shadow: 0 3px 12px rgba(143, 18, 63, 0.12);
    }
    .method-btn .btn-icon {
      font-size: 1.5rem;
      line-height: 1;
    }
    .method-btn .btn-text strong {
      display: block;
      font-size: 0.95rem;
      color: #1a1a18;
    }
    .method-btn .btn-text small {
      display: block;
      font-size: 0.78rem;
      color: #625f5c;
      margin-top: 0.15rem;
    }
    .method-btn.active .btn-text strong {
      color: #8f123f;
    }
    .cash-journey-cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 1rem;
    }
    .cash-process-box {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .cash-process-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.35rem 0.75rem;
      background: #fdf7eb;
      border: 1px solid #d6a756;
      border-radius: 999px;
      color: #8c5a08;
      font-weight: 750;
      font-size: 0.82rem;
      width: fit-content;
    }

    .products-list { display: grid; gap: .8rem; }
    .product-row { display: grid; grid-template-columns: 4.5rem minmax(0, 1fr) auto; gap: 1rem; align-items: center; padding: .8rem 0; border-bottom: 1px solid rgb(26 26 24 / 12%); }
    .product-image { width: 4.5rem; height: 4.5rem; object-fit: cover; border: 1px solid #e3dddf; background: #f6f4f2; }
    .product-image--empty { display: grid; place-items: center; color: #af0f50; font-weight: 850; }
    .product-info { display: grid; gap: .25rem; min-width: 0; }
    .product-info strong { overflow-wrap: anywhere; }
    .product-values { display: grid; gap: .2rem; justify-items: end; color: #625f5c; font-size: .86rem; text-align: right; }
    .product-values strong { color: #1a1a18; font-size: 1rem; }
    small { display: block; color: #625f5c; }
    .totals { margin-top: 1rem; }
    .totals p { display: flex; justify-content: space-between; gap: 1rem; margin: .55rem 0; color: #625f5c; }
    .totals .grand-total { margin-top: 1rem; padding-top: .8rem; border-top: 2px solid #1a1a18; color: #1a1a18; font-size: 1.15rem; }
    address { line-height: 1.6; font-style: normal; }
    .state { display: grid; justify-items: start; gap: .8rem; margin-top: 2rem; }
    .state a { color: #8f123f; font-weight: 750; }
    a:focus-visible { outline: 3px solid #d02870; outline-offset: 3px; }
    .proof-modal-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.7); backdrop-filter: blur(4px); padding: 1.5rem; }
    .proof-modal-card { background: #fff; border-radius: 12px; max-width: 900px; width: 100%; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 20px 40px rgba(0,0,0,0.3); overflow: hidden; }
    .proof-modal-header { display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.5rem; border-bottom: 1px solid #e5e0dc; }
    .proof-modal-close { background: transparent; border: none; font-size: 1.25rem; font-weight: bold; cursor: pointer; color: #625f5c; padding: 0.25rem 0.5rem; border-radius: 4px; }
    .proof-modal-close:hover { color: #1a1a18; background: #f1eeeb; }
    .proof-modal-body { padding: 1rem; overflow: auto; display: flex; justify-content: center; align-items: center; background: #fbf9f8; min-height: 200px; }
    .proof-modal-image { max-width: 100%; max-height: 65vh; object-fit: contain; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
    .proof-modal-footer { display: flex; justify-content: flex-end; gap: 0.75rem; padding: 1rem 1.5rem; border-top: 1px solid #e5e0dc; background: #fff; }
    .proof-thumbnail-wrapper { position: relative; }
    .proof-thumbnail-hover-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.25); opacity: 0; transition: opacity 0.2s ease; }
    .proof-thumbnail-wrapper:hover .proof-thumbnail-hover-overlay { opacity: 1; }
    @media (max-width: 760px) {
      .progress-heading { align-items: stretch; flex-direction: column; }
      .payment-status { min-width: 0; }
      .progress-steps { grid-template-columns: 1fr; gap: .85rem; }
      .progress-step:not(:last-child)::after { top: 2.5rem; left: 1.2rem; width: 2px; height: calc(100% + .85rem); }
      .step-copy { padding: .25rem 0; }
      .grid { grid-template-columns: 1fr; }
      .product-row { grid-template-columns: 3.75rem minmax(0, 1fr); }
      .product-image { width: 3.75rem; height: 3.75rem; }
      .product-values { grid-column: 2; justify-items: start; text-align: left; }
      .balance-header { flex-direction: column; align-items: stretch; }
      .balance-metrics-grid { grid-template-columns: 1fr 1fr; }
      .journey-step-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class OrderDetailComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly orderApi = inject(OrderApiService);
  private readonly productApi = inject(ProductApiService);
  private readonly sanitizer = inject(DomSanitizer);

  // 1st proof state
  private rawBlobUrl: string | null = null;
  private readonly cachedProofBlob = signal<Blob | null>(null);
  protected readonly proofDownloading = signal(false);
  protected readonly proofUploading = signal(false);
  protected readonly proofUploadError = signal<string | null>(null);
  protected readonly proofSuccessMessage = signal<string | null>(null);
  protected readonly proofPreviewUrl = signal<SafeUrl | null>(null);
  protected readonly previewLoading = signal(false);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly selectedFileName = signal<string | null>(null);

  // 2nd proof state
  private rawSecondBlobUrl: string | null = null;
  private readonly cachedSecondProofBlob = signal<Blob | null>(null);
  protected readonly secondProofDownloading = signal(false);
  protected readonly secondProofUploading = signal(false);
  protected readonly secondProofUploadError = signal<string | null>(null);
  protected readonly secondProofSuccessMessage = signal<string | null>(null);
  protected readonly secondProofPreviewUrl = signal<SafeUrl | null>(null);
  protected readonly secondPreviewLoading = signal(false);
  protected readonly secondSelectedFile = signal<File | null>(null);
  protected readonly secondSelectedFileName = signal<string | null>(null);
  protected readonly balancePaymentJourneyOpen = signal(false);

  // 2nd installment method choice state
  protected readonly activeSecondMethodChoice = signal<'BANK_TRANSFER' | 'CASH' | null>(null);
  protected readonly selectingSecondMethod = signal(false);
  protected readonly secondMethodError = signal<string | null>(null);

  // Modal preview state
  protected readonly previewModalOpen = signal(false);
  protected readonly previewModalTitle = signal<string>('Reçu de virement bancaire');
  protected readonly activeModalUrl = signal<SafeUrl | null>(null);
  protected readonly activeModalInstallment = signal<1 | 2>(1);

  // Order state
  protected readonly order = signal<OrderResponse | null>(null);
  protected readonly itemViews = signal<readonly OrderItemView[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly cancelling = signal(false);
  protected readonly cancelError = signal<string | null>(null);
  protected readonly actionBusy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly pdfDownloading = signal(false);
  protected readonly pdfError = signal<string | null>(null);
  protected readonly notificationDeliveries = signal<OrderNotificationDeliveryResponse | null>(null);
  protected readonly retryingNotifications = signal(false);
  protected readonly notificationActionMessage = signal<string | null>(null);
  protected readonly regeneratedTrackingUrl = signal<string | null>(null);
  protected readonly orderProgressSteps: readonly OrderProgressStatus[] = ['DRAFT', 'CONFIRMED', 'PROCESSING', 'READY', 'COMPLETED'];
  protected readonly role = computed<WwtradingRole | null>(() => this.auth.roles()[0] ?? null);
  protected readonly isClient = computed(() => this.role() === 'CLIENT');
  protected readonly isStaffView = computed(() => this.isStaff());
  protected readonly backPath = computed(() => {
    switch (this.role()) {
      case 'ADMIN': return '/admin/orders';
      case 'MANAGER': return '/manager/orders';
      case 'COMMERCIAL': return '/commercial/orders';
      case 'TECHNICIAN': return '/technician/orders';
      default: return '/client/orders';
    }
  });

  ngOnInit(): void {
    if (this.role() === 'TECHNICIAN') {
      this.error.set("Vous n'avez pas accès à cette commande.");
      this.loading.set(false);
      return;
    }

    const orderId = this.route.snapshot.paramMap.get('orderId') ?? this.route.snapshot.paramMap.get('id');
    if (!orderId) { this.error.set('Cette commande est introuvable.'); this.loading.set(false); return; }
    this.orderApi.getOrder(orderId).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (result) => {
        this.order.set(result);
        if (result.secondInstallmentPaymentMethod) {
          this.activeSecondMethodChoice.set(result.secondInstallmentPaymentMethod);
        }
        this.loadItemViews(result);
        if (result.transferProofReference) {
          this.loadProofThumbnail(result.id);
        }
        if (result.secondTransferProofReference) {
          this.loadSecondProofThumbnail(result.id);
        }
        if (this.isStaff()) {
          this.loadNotificationDeliveries(result.id);
        }
      },
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  // --- BALANCE & INSTALLMENT METHODS ---

  protected isSplitPayment(order: OrderResponse): boolean {
    return order.paymentPlan === 'SPLIT_50_50' || order.paymentMethod === 'SPLIT_50_50';
  }

  protected isFirstInstallmentConfirmed(order: OrderResponse): boolean {
    return !!order.firstInstallmentConfirmedAt ||
      order.paymentStatus === 'PARTIALLY_PAID' ||
      order.paymentStatus === 'PAID';
  }

  protected isSecondInstallmentConfirmed(order: OrderResponse): boolean {
    return !!order.secondInstallmentConfirmedAt ||
      order.paymentStatus === 'PAID';
  }

  protected firstInstallmentStatus(order: OrderResponse): 'CONFIRMED' | 'PENDING' | 'MISSING' {
    if (this.isFirstInstallmentConfirmed(order)) return 'CONFIRMED';
    if (order.transferProofReference) return 'PENDING';
    return 'MISSING';
  }

  protected firstInstallmentStatusLabel(order: OrderResponse): string {
    const status = this.firstInstallmentStatus(order);
    switch (status) {
      case 'CONFIRMED': return 'Confirmé';
      case 'PENDING': return 'En attente de validation';
      case 'MISSING': return 'Non fourni';
    }
  }

  protected secondInstallmentStatus(order: OrderResponse): 'CONFIRMED' | 'PENDING' | 'PENDING_CASH' | 'MISSING' | 'UNSELECTED' {
    if (this.isSecondInstallmentConfirmed(order)) return 'CONFIRMED';
    const method = order.secondInstallmentPaymentMethod ?? this.activeSecondMethodChoice();
    if (method === 'CASH') return 'PENDING_CASH';
    if (method === 'BANK_TRANSFER') {
      return order.secondTransferProofReference ? 'PENDING' : 'MISSING';
    }
    if (order.secondTransferProofReference) return 'PENDING';
    return 'UNSELECTED';
  }

  protected secondInstallmentStatusLabel(order: OrderResponse): string {
    const status = this.secondInstallmentStatus(order);
    switch (status) {
      case 'CONFIRMED': return 'Confirmé';
      case 'PENDING': return 'En attente de validation';
      case 'PENDING_CASH': return 'En attente d’encaissement';
      case 'MISSING': return 'Reçu non fourni';
      case 'UNSELECTED': return 'En attente de sélection';
    }
  }

  protected secondInstallmentMethodLabel(order: OrderResponse): string {
    const method = order.secondInstallmentPaymentMethod ?? this.activeSecondMethodChoice();
    if (method === 'BANK_TRANSFER') return 'Virement bancaire';
    if (method === 'CASH') {
      return order.deliveryMethod === 'PICKUP' ? 'Espèces (au showroom)' : 'Espèces (à la livraison)';
    }
    return 'En attente du choix du client';
  }

  protected chooseSecondInstallmentMethod(orderId: string, method: 'BANK_TRANSFER' | 'CASH'): void {
    if (this.selectingSecondMethod()) return;
    this.activeSecondMethodChoice.set(method);
    this.selectingSecondMethod.set(true);
    this.secondMethodError.set(null);
    this.orderApi.selectSecondInstallmentMethod(orderId, method)
      .pipe(finalize(() => this.selectingSecondMethod.set(false)))
      .subscribe({
        next: (updatedOrder) => {
          this.order.set(updatedOrder);
          this.activeSecondMethodChoice.set(updatedOrder.secondInstallmentPaymentMethod ?? method);
        },
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 400) {
            this.secondMethodError.set(err.error?.message || 'Sélection impossible pour cette commande.');
          } else {
            this.secondMethodError.set('Impossible d’enregistrer votre choix. Veuillez réessayer.');
          }
        }
      });
  }

  protected canPayRemainingBalance(order: OrderResponse): boolean {
    if (!this.isClient() && !this.isStaff()) return false;
    if (!this.isSplitPayment(order)) return false;
    if (order.status === 'CANCELLED') return false;
    if (this.isSecondInstallmentConfirmed(order)) return false;

    // First installment must be validated
    if (!this.isFirstInstallmentConfirmed(order)) return false;

    const balance = this.remainingBalance(order);
    return balance > 0;
  }

  protected toggleBalancePaymentJourney(): void {
    this.balancePaymentJourneyOpen.update((v) => !v);
  }

  protected remainingBalance(order: OrderResponse): number {
    if (order.paymentStatus === 'PAID') return 0;
    if (order.outstandingBalance !== undefined && order.outstandingBalance !== null) {
      return order.outstandingBalance;
    }
    const paid = this.paidAmount(order);
    return Math.max(0, order.total - paid);
  }

  protected paidAmount(order: OrderResponse): number {
    if (order.paymentStatus === 'PAID') return order.total;
    if (order.amountPaid !== undefined && order.amountPaid !== null) {
      return order.amountPaid;
    }
    if (order.paymentStatus === 'PARTIALLY_PAID') {
      return order.upfrontPaymentAmount ?? (order.total / 2);
    }
    return 0;
  }

  protected paymentFinancialStatusBadge(order: OrderResponse): string {
    if (order.paymentStatus === 'PAID') {
      return 'Payé intégralement (100 %)';
    }
    const isSplit = order.paymentPlan === 'SPLIT_50_50' || order.paymentMethod === 'SPLIT_50_50';
    if (isSplit) {
      if (order.paymentStatus === 'PARTIALLY_PAID' || order.firstInstallmentConfirmedAt) {
        const method = order.secondInstallmentPaymentMethod ?? this.activeSecondMethodChoice();
        if (method === 'CASH') {
          return 'Acompte validé · Solde espèces en attente';
        }
        return order.secondTransferProofReference ? 'Acompte validé · Solde en vérification' : 'Acompte 50 % validé';
      }
      return 'En attente de l’acompte (50 %)';
    }
    return paymentStatusLabel(order.paymentStatus);
  }

  protected paymentFinancialStatusText(order: OrderResponse): string {
    if (order.paymentStatus === 'PAID') {
      return 'Paiement intégral validé';
    }
    const isSplit = order.paymentPlan === 'SPLIT_50_50' || order.paymentMethod === 'SPLIT_50_50';
    if (isSplit) {
      if (order.paymentStatus === 'PARTIALLY_PAID' || order.firstInstallmentConfirmedAt) {
        const method = order.secondInstallmentPaymentMethod ?? this.activeSecondMethodChoice();
        if (method === 'CASH') {
          return order.deliveryMethod === 'PICKUP'
            ? 'Acompte 50 % validé · Solde en espèces à régler au showroom (Kénitra)'
            : 'Acompte 50 % validé · Solde en espèces à régler au livreur';
        }
        if (order.secondTransferProofReference) {
          return 'Acompte 50 % validé · Justificatif du solde transmis (vérification en cours)';
        }
        if (method === 'BANK_TRANSFER') {
          return 'Acompte 50 % validé · En attente du reçu de virement du solde';
        }
        return 'Acompte 50 % validé · En attente du choix de règlement du solde';
      }
      if (order.transferProofReference) {
        return 'Justificatif d’acompte transmis · En attente de validation financière';
      }
      return 'En attente du virement de l’acompte de 50 %';
    }
    if (order.transferProofReference) {
      return 'Justificatif transmis · En attente de validation financière';
    }
    return paymentStatusLabel(order.paymentStatus);
  }

  protected receptionMethodLabel(method: DeliveryMethod): string {
    return method === 'PICKUP' ? 'Retrait showroom (Kénitra)' : 'Livraison à domicile';
  }

  // --- SECOND PROOF HANDLERS ---

  protected onSecondProofFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0] ?? null;
    this.secondProofUploadError.set(null);
    this.secondProofSuccessMessage.set(null);
    if (!file) {
      this.secondSelectedFile.set(null);
      this.secondSelectedFileName.set(null);
      return;
    }
    const lowerName = file.name.toLowerCase();
    const isImage = lowerName.endsWith('.jpg') || lowerName.endsWith('.jpeg') || lowerName.endsWith('.png') ||
      file.type === 'image/jpeg' || file.type === 'image/png';
    if (!isImage) {
      this.secondProofUploadError.set('Format non supporté. Seuls les fichiers JPG, JPEG et PNG sont acceptés.');
      this.secondSelectedFile.set(null);
      this.secondSelectedFileName.set(null);
      input.value = '';
      return;
    }
    const maxBytes = 5 * 1024 * 1024;
    if (file.size > maxBytes) {
      this.secondProofUploadError.set('Fichier trop volumineux. La taille maximale autorisée est de 5 Mo.');
      this.secondSelectedFile.set(null);
      this.secondSelectedFileName.set(null);
      input.value = '';
      return;
    }
    this.secondSelectedFile.set(file);
    this.secondSelectedFileName.set(file.name);
  }

  protected uploadSecondProof(orderId: string): void {
    const file = this.secondSelectedFile();
    if (!file || this.secondProofUploading()) return;
    this.secondProofUploading.set(true);
    this.secondProofUploadError.set(null);
    this.secondProofSuccessMessage.set(null);

    this.orderApi.uploadTransferProof(orderId, file, 2)
      .pipe(finalize(() => this.secondProofUploading.set(false)))
      .subscribe({
        next: (updatedOrder) => {
          this.order.set(updatedOrder);
          this.secondSelectedFile.set(null);
          this.secondSelectedFileName.set(null);
          this.secondProofSuccessMessage.set('Justificatif du solde transmis avec succès. Notre équipe financière va procéder à sa vérification.');
          if (updatedOrder.secondTransferProofReference) {
            this.loadSecondProofThumbnail(updatedOrder.id);
          }
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse) {
            if (error.status === 400) {
              this.secondProofUploadError.set(error.error?.message || 'Fichier non valide (seuls JPG et PNG sont autorisés, max 5 Mo).');
            } else if (error.status === 403) {
              this.secondProofUploadError.set('Vous n’avez pas l’autorisation de modifier cette commande.');
            } else {
              this.secondProofUploadError.set('Impossible d’enregistrer le reçu du solde. Veuillez réessayer.');
            }
          } else {
            this.secondProofUploadError.set('Une erreur est survenue lors du téléversement.');
          }
        }
      });
  }

  private loadSecondProofThumbnail(orderId: string): void {
    if (this.secondProofPreviewUrl()) return;
    this.secondPreviewLoading.set(true);
    this.orderApi.getTransferProof(orderId, false, 2)
      .pipe(finalize(() => this.secondPreviewLoading.set(false)))
      .subscribe({
        next: (blob) => {
          this.cachedSecondProofBlob.set(blob);
          if (this.rawSecondBlobUrl) {
            URL.revokeObjectURL(this.rawSecondBlobUrl);
          }
          const url = URL.createObjectURL(blob);
          this.rawSecondBlobUrl = url;
          this.secondProofPreviewUrl.set(this.sanitizer.bypassSecurityTrustUrl(url));
        },
        error: () => {}
      });
  }

  protected secondProofFileName(order: OrderResponse): string {
    const ref = order.secondTransferProofReference;
    if (ref) {
      const slashIdx = ref.lastIndexOf('/');
      return slashIdx >= 0 ? ref.substring(slashIdx + 1) : ref;
    }
    return `recu-solde-${order.orderNumber || order.id}.png`;
  }

  // --- GENERAL PROOF METHODS (INSTALLMENT 1 & 2) ---

  protected previewProof(orderId: string, installment: 1 | 2 = 1): void {
    if (installment === 2) {
      if (this.secondProofPreviewUrl()) {
        this.previewModalTitle.set('Reçu du solde restant (50 %)');
        this.activeModalUrl.set(this.secondProofPreviewUrl());
        this.activeModalInstallment.set(2);
        this.previewModalOpen.set(true);
        return;
      }
      this.secondPreviewLoading.set(true);
      this.secondProofUploadError.set(null);
      this.orderApi.getTransferProof(orderId, false, 2)
        .pipe(finalize(() => this.secondPreviewLoading.set(false)))
        .subscribe({
          next: (blob) => {
            this.cachedSecondProofBlob.set(blob);
            if (this.rawSecondBlobUrl) {
              URL.revokeObjectURL(this.rawSecondBlobUrl);
            }
            const url = URL.createObjectURL(blob);
            this.rawSecondBlobUrl = url;
            const safe = this.sanitizer.bypassSecurityTrustUrl(url);
            this.secondProofPreviewUrl.set(safe);
            this.previewModalTitle.set('Reçu du solde restant (50 %)');
            this.activeModalUrl.set(safe);
            this.activeModalInstallment.set(2);
            this.previewModalOpen.set(true);
          },
          error: (err: unknown) => {
            this.handleProofError(err, 'Impossible de charger l’aperçu du reçu de solde.');
          }
        });
      return;
    }

    // Installment 1
    if (this.proofPreviewUrl()) {
      this.previewModalTitle.set('Reçu de l’acompte (50 %)');
      this.activeModalUrl.set(this.proofPreviewUrl());
      this.activeModalInstallment.set(1);
      this.previewModalOpen.set(true);
      return;
    }
    this.previewLoading.set(true);
    this.proofUploadError.set(null);
    this.orderApi.getTransferProof(orderId, false, 1)
      .pipe(finalize(() => this.previewLoading.set(false)))
      .subscribe({
        next: (blob) => {
          this.cachedProofBlob.set(blob);
          if (this.rawBlobUrl) {
            URL.revokeObjectURL(this.rawBlobUrl);
          }
          const url = URL.createObjectURL(blob);
          this.rawBlobUrl = url;
          const safe = this.sanitizer.bypassSecurityTrustUrl(url);
          this.proofPreviewUrl.set(safe);
          this.previewModalTitle.set('Reçu de l’acompte (50 %)');
          this.activeModalUrl.set(safe);
          this.activeModalInstallment.set(1);
          this.previewModalOpen.set(true);
        },
        error: (err: unknown) => {
          this.handleProofError(err, 'Impossible de charger l’aperçu du reçu d’acompte.');
        }
      });
  }

  protected downloadProof(orderId: string, installment: 1 | 2 = 1): void {
    if (installment === 2) {
      if (this.secondProofDownloading()) return;
      this.secondProofUploadError.set(null);
      const cached = this.cachedSecondProofBlob();
      if (cached) {
        this.triggerBlobDownload(cached, orderId, 2);
        return;
      }
      this.secondProofDownloading.set(true);
      this.orderApi.getTransferProof(orderId, true, 2)
        .pipe(finalize(() => this.secondProofDownloading.set(false)))
        .subscribe({
          next: (blob) => {
            this.cachedSecondProofBlob.set(blob);
            this.triggerBlobDownload(blob, orderId, 2);
          },
          error: (err: unknown) => {
            this.handleProofError(err, 'Impossible de télécharger le reçu de solde.');
          }
        });
      return;
    }

    // Installment 1
    if (this.proofDownloading()) return;
    this.proofUploadError.set(null);
    const cached = this.cachedProofBlob();
    if (cached) {
      this.triggerBlobDownload(cached, orderId, 1);
      return;
    }
    this.proofDownloading.set(true);
    this.orderApi.getTransferProof(orderId, true, 1)
      .pipe(finalize(() => this.proofDownloading.set(false)))
      .subscribe({
        next: (blob) => {
          this.cachedProofBlob.set(blob);
          this.triggerBlobDownload(blob, orderId, 1);
        },
        error: (err: unknown) => {
          this.handleProofError(err, 'Impossible de télécharger le reçu d’acompte.');
        }
      });
  }

  protected downloadActiveModalProof(): void {
    const currentOrder = this.order();
    if (!currentOrder) return;
    this.downloadProof(currentOrder.id, this.activeModalInstallment());
  }

  private triggerBlobDownload(blob: Blob, orderId: string, installment: 1 | 2 = 1): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const orderNum = this.order()?.orderNumber || orderId;
    const ref = installment === 2 ? this.order()?.secondTransferProofReference : this.order()?.transferProofReference;
    const refExt = ref?.split('.').pop()?.toLowerCase();
    const ext = refExt && ['jpg', 'jpeg', 'png'].includes(refExt) ? refExt : (blob.type === 'image/jpeg' ? 'jpg' : 'png');
    const prefix = installment === 2 ? 'recu-solde-virement-' : 'recu-acompte-virement-';
    link.download = `${prefix}${orderNum}.${ext}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    globalThis.setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }

  protected onProofFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0] ?? null;
    this.proofUploadError.set(null);
    this.proofSuccessMessage.set(null);
    if (!file) {
      this.selectedFile.set(null);
      this.selectedFileName.set(null);
      return;
    }
    const lowerName = file.name.toLowerCase();
    const isImage = lowerName.endsWith('.jpg') || lowerName.endsWith('.jpeg') || lowerName.endsWith('.png') ||
      file.type === 'image/jpeg' || file.type === 'image/png';
    if (!isImage) {
      this.proofUploadError.set('Format non supporté. Seuls les fichiers JPG, JPEG et PNG sont acceptés.');
      this.selectedFile.set(null);
      this.selectedFileName.set(null);
      input.value = '';
      return;
    }
    const maxBytes = 5 * 1024 * 1024;
    if (file.size > maxBytes) {
      this.proofUploadError.set('Fichier trop volumineux. La taille maximale autorisée est de 5 Mo.');
      this.selectedFile.set(null);
      this.selectedFileName.set(null);
      input.value = '';
      return;
    }
    this.selectedFile.set(file);
    this.selectedFileName.set(file.name);
  }

  protected uploadProof(orderId: string): void {
    const file = this.selectedFile();
    if (!file || this.proofUploading()) return;
    this.proofUploading.set(true);
    this.proofUploadError.set(null);
    this.proofSuccessMessage.set(null);

    this.orderApi.uploadTransferProof(orderId, file, 1)
      .pipe(finalize(() => this.proofUploading.set(false)))
      .subscribe({
        next: (updatedOrder) => {
          this.order.set(updatedOrder);
          this.selectedFile.set(null);
          this.selectedFileName.set(null);
          this.proofSuccessMessage.set('Preuve de virement bancaire enregistrée avec succès.');
          if (updatedOrder.transferProofReference) {
            this.loadProofThumbnail(updatedOrder.id);
          }
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse) {
            if (error.status === 400) {
              this.proofUploadError.set(error.error?.message || 'Fichier non valide (seuls JPG et PNG sont autorisés, max 5 Mo).');
            } else if (error.status === 403) {
              this.proofUploadError.set('Vous n’avez pas l’autorisation de modifier cette commande.');
            } else {
              this.proofUploadError.set('Impossible d’enregistrer le reçu. Veuillez réessayer.');
            }
          } else {
            this.proofUploadError.set('Une erreur est survenue lors du téléversement.');
          }
        }
      });
  }

  private loadProofThumbnail(orderId: string): void {
    if (this.proofPreviewUrl()) return;
    this.previewLoading.set(true);
    this.orderApi.getTransferProof(orderId, false, 1)
      .pipe(finalize(() => this.previewLoading.set(false)))
      .subscribe({
        next: (blob) => {
          this.cachedProofBlob.set(blob);
          if (this.rawBlobUrl) {
            URL.revokeObjectURL(this.rawBlobUrl);
          }
          const url = URL.createObjectURL(blob);
          this.rawBlobUrl = url;
          this.proofPreviewUrl.set(this.sanitizer.bypassSecurityTrustUrl(url));
        },
        error: () => {}
      });
  }

  private handleProofError(err: unknown, defaultMessage: string): void {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 404) {
        this.proofUploadError.set('Le fichier du reçu est introuvable sur le serveur de stockage.');
      } else if (err.status === 403) {
        this.proofUploadError.set('Vous n’avez pas l’autorisation d’accéder à ce reçu.');
      } else if (err.status === 401) {
        this.proofUploadError.set('Session expirée. Veuillez vous reconnecter pour accéder au reçu.');
      } else if (err.status >= 500) {
        this.proofUploadError.set('Erreur serveur lors de la récupération du reçu. Veuillez réessayer.');
      } else if (err.status === 0) {
        this.proofUploadError.set('Impossible de contacter le serveur (erreur de connexion ou CORS).');
      } else {
        this.proofUploadError.set(defaultMessage);
      }
    } else {
      this.proofUploadError.set(defaultMessage);
    }
  }

  protected proofFileName(order: OrderResponse): string {
    const ref = order.transferProofReference;
    if (ref) {
      const slashIdx = ref.lastIndexOf('/');
      return slashIdx >= 0 ? ref.substring(slashIdx + 1) : ref;
    }
    return `recu-virement-${order.orderNumber || order.id}.png`;
  }

  protected closeProofModal(): void {
    this.previewModalOpen.set(false);
  }

  // --- ORDER LIFECYCLE & WORKFLOW METHODS ---

  private loadNotificationDeliveries(orderId: string): void {
    this.orderApi.getOrderNotificationDeliveries(orderId).subscribe({
      next: (res) => this.notificationDeliveries.set(res),
      error: () => this.notificationDeliveries.set(null)
    });
  }

  protected retryNotifications(orderId: string, channel: 'EMAIL' | 'WHATSAPP' | 'ALL'): void {
    if (this.retryingNotifications()) return;
    this.retryingNotifications.set(true);
    this.notificationActionMessage.set(null);
    this.orderApi.retryOrderNotificationDeliveries(orderId, channel)
      .pipe(finalize(() => this.retryingNotifications.set(false)))
      .subscribe({
        next: (res) => {
          this.notificationDeliveries.set(res);
          this.notificationActionMessage.set(`Relance ${channel} exécutée.`);
        },
        error: () => {
          this.notificationActionMessage.set("Impossible de relancer la notification pour le moment.");
        }
      });
  }

  protected revokeTrackingToken(orderId: string): void {
    if (this.actionBusy()) return;
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.regeneratedTrackingUrl.set(null);
    this.orderApi.revokeTrackingToken(orderId).pipe(finalize(() => this.actionBusy.set(false))).subscribe({
      next: () => {
        this.notificationActionMessage.set('Le jeton de suivi invité a été révoqué.');
      },
      error: (error: unknown) => this.actionError.set(this.actionMessage(error))
    });
  }

  protected regenerateTrackingToken(orderId: string): void {
    if (this.actionBusy()) return;
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.regeneratedTrackingUrl.set(null);
    this.orderApi.regenerateTrackingToken(orderId).pipe(finalize(() => this.actionBusy.set(false))).subscribe({
      next: (result) => {
        if (result.trackingUrl) {
          this.regeneratedTrackingUrl.set(result.trackingUrl);
        }
      },
      error: (error: unknown) => this.actionError.set(this.actionMessage(error))
    });
  }

  protected resolveBankLogo(logoUrl?: string | null, bankNameOrRef?: string | null): string {
    return this.orderApi.resolveBankLogo(logoUrl, bankNameOrRef);
  }

  protected onLogoError(event: Event, bankNameOrRef?: string | null): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.dataset['fallbackApplied']) {
      img.dataset['fallbackApplied'] = 'true';
      img.src = this.orderApi.resolveBankLogo(null, bankNameOrRef);
    }
  }

  protected progressState(status: OrderResponse['status'], step: OrderProgressStatus): OrderProgressState {
    const currentIndex = this.orderProgressSteps.indexOf(status as OrderProgressStatus);
    const stepIndex = this.orderProgressSteps.indexOf(step);
    if (stepIndex < currentIndex) return 'completed';
    return stepIndex === currentIndex ? 'current' : 'pending';
  }

  protected progressLabel(status: OrderProgressStatus, deliveryMethod: DeliveryMethod): string {
    return orderStatusLabel(status, deliveryMethod);
  }

  protected progressStateLabel(state: OrderProgressState): string {
    switch (state) {
      case 'completed': return 'Terminée';
      case 'current': return 'Étape actuelle';
      case 'pending': return 'À venir';
    }
  }

  protected canConfirm(order: OrderResponse): boolean {
    return this.isStaff() && order.status === 'DRAFT';
  }

  protected canCancel(order: OrderResponse): boolean {
    return this.isClient() && order.status === 'DRAFT';
  }

  protected canProcess(order: OrderResponse): boolean {
    return this.isStaff() && order.status === 'CONFIRMED';
  }

  protected canMarkReady(order: OrderResponse): boolean {
    return this.isStaff() && order.status === 'PROCESSING';
  }

  protected canComplete(order: OrderResponse): boolean {
    return this.isStaff() && order.status === 'READY';
  }

  protected completionActionLabel(deliveryMethod: DeliveryMethod): string {
    return deliveryMethod === 'DELIVERY' ? 'Marquer comme livrée' : 'Marquer comme retirée';
  }

  protected canConfirmPayment(order: OrderResponse): boolean {
    return this.isStaff() && order.status !== 'CANCELLED' && order.paymentStatus !== 'PAID';
  }

  protected confirmPaymentActionLabel(order: OrderResponse): string {
    const isSplit = order.paymentPlan === 'SPLIT_50_50' || order.paymentMethod === 'SPLIT_50_50';
    if (isSplit && order.paymentStatus === 'UNPAID') {
      return 'Confirmer l’acompte de 50 % (virement)';
    }
    if (isSplit && order.paymentStatus === 'PARTIALLY_PAID') {
      if (order.secondInstallmentPaymentMethod === 'CASH') {
        return 'Confirmer l’encaissement des espèces (50 %)';
      }
      if (order.secondInstallmentPaymentMethod === 'BANK_TRANSFER') {
        return 'Confirmer le virement du solde (50 %)';
      }
      return 'Confirmer le solde de 50 %';
    }
    return 'Confirmer le paiement';
  }

  protected paymentPlanLabel = paymentPlanLabel;
  protected paymentStatusLabel = paymentStatusLabel;
  protected deliveryAreaLabel = deliveryAreaLabel;
  protected deliveryFeeWaivedLabel = deliveryFeeWaivedLabel;

  protected confirmOrder(orderId: string): void {
    this.runAction(() => this.orderApi.confirmOrder(orderId));
  }

  protected processOrder(orderId: string): void {
    this.runAction(() => this.orderApi.processOrder(orderId));
  }

  protected markOrderReady(orderId: string): void {
    this.runAction(() => this.orderApi.markOrderReady(orderId));
  }

  protected completeOrder(orderId: string): void {
    this.runAction(() => this.orderApi.completeOrder(orderId));
  }

  protected confirmPayment(orderId: string): void {
    this.runAction(() => this.orderApi.confirmPayment(orderId));
  }

  protected imageUrl(image: ProductImage): string {
    return this.productApi.resolveImageUrl(image.imageUrl);
  }

  protected downloadPdf(id: string): void {
    if (this.pdfDownloading()) return;
    this.pdfDownloading.set(true);
    this.pdfError.set(null);
    this.orderApi.getOrderPdf(id).pipe(finalize(() => this.pdfDownloading.set(false))).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `bon-de-commande-${id}.pdf`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        globalThis.setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: (failure: unknown) => {
        this.pdfError.set(failure instanceof HttpErrorResponse && failure.status === 404
          ? 'Cette commande est introuvable.'
          : failure instanceof HttpErrorResponse && failure.status === 403
            ? 'Vous n’avez pas accès à ce document.'
            : 'Le bon de commande PDF ne peut pas être téléchargé pour le moment.');
      }
    });
  }

  private loadItemViews(order: OrderResponse): void {
    forkJoin(order.items.map((item) => this.productApi.getByReference(item.productReference).pipe(
      switchMap((product) => this.productApi.getImages(product.id).pipe(
        map((images) => ({
          item,
          name: product.name,
          image: images.find((image) => image.main) ?? images[0] ?? null
        })),
        catchError(() => of({ item, name: product.name, image: null }))
      )),
      catchError(() => of({ item, name: item.productReference, image: null }))
    ))).subscribe((items) => this.itemViews.set(items));
  }

  protected cancelOrder(orderId: string): void {
    if (this.order()?.status !== 'DRAFT' || this.cancelling()) return;
    if (!globalThis.confirm('Voulez-vous vraiment annuler cette commande ?')) return;
    this.cancelling.set(true);
    this.cancelError.set(null);
    this.orderApi.cancelOrder(orderId).pipe(finalize(() => this.cancelling.set(false))).subscribe({
      next: (result) => this.order.set(result),
      error: (error: unknown) => this.cancelError.set(error instanceof HttpErrorResponse && error.status === 409
        ? 'Cette commande ne peut plus être annulée.'
        : 'La commande n’a pas pu être annulée. Réessayez.')
    });
  }

  ngOnDestroy(): void {
    if (this.rawBlobUrl) {
      URL.revokeObjectURL(this.rawBlobUrl);
      this.rawBlobUrl = null;
    }
    if (this.rawSecondBlobUrl) {
      URL.revokeObjectURL(this.rawSecondBlobUrl);
      this.rawSecondBlobUrl = null;
    }
  }

  protected isStaff(): boolean {
    return this.role() === 'ADMIN' || this.role() === 'MANAGER' || this.role() === 'COMMERCIAL';
  }

  private runAction(action: () => ReturnType<OrderApiService['confirmOrder']>): void {
    if (this.actionBusy()) return;
    this.actionBusy.set(true);
    this.actionError.set(null);
    action().pipe(finalize(() => this.actionBusy.set(false))).subscribe({
      next: (result) => this.order.set(result),
      error: (error: unknown) => this.actionError.set(this.actionMessage(error))
    });
  }

  private message(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.';
    if (error instanceof HttpErrorResponse && error.status === 403) return "Vous n'avez pas accès à cette commande.";
    if (error instanceof HttpErrorResponse && error.status === 404) return 'Cette commande est introuvable ou ne vous appartient pas.';
    return 'Cette commande ne peut pas être chargée pour le moment.';
  }

  private actionMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'L’action n’a pas pu être exécutée. Réessayez.';
    switch (error.status) {
      case 401: return 'Votre session a expiré. Reconnectez-vous pour continuer.';
      case 403: return 'Cette action n’est pas autorisée pour votre rôle.';
      case 404: return 'Cette commande est introuvable.';
      case 409: return 'Cette commande ne peut plus être modifiée dans son état actuel.';
      default: return 'L’action n’a pas pu être exécutée. Réessayez.';
    }
  }
}
