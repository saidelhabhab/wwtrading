import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { QuotationApiService } from './quotation-api.service';
import { getQuotationStatusLabel, getServiceTypeLabel, Quotation, QuotationStatus } from './quotation.models';

type ProgressState = 'complete' | 'current' | 'pending';
interface ProgressStep { readonly label: string; readonly state: ProgressState; }

@Component({
  selector: 'wwt-client-quotation-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, DashboardLayoutComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Mes devis">
    <main class="detail-page" aria-labelledby="quotation-detail-title">
      <a class="back-link" routerLink="/devis/mes-demandes">
        <span class="material-icons" aria-hidden="true">arrow_back</span> Mes demandes
      </a>

      @if (loading()) {
        <section class="state-card" role="status"><span class="spinner" aria-hidden="true"></span><h2>Chargement de la demande…</h2></section>
      } @else if (error()) {
        <section class="state-card state-card--error" role="alert">
          <span class="material-icons" aria-hidden="true">error_outline</span>
          <h2>Demande indisponible</h2><p>{{ error() }}</p>
          <button type="button" (click)="retry()">Réessayer</button>
        </section>
      } @else {
        @if (quotation(); as current) {
        <header class="detail-heading">
          <div><p class="eyebrow">Demande de devis</p><h1 id="quotation-detail-title">#{{ shortReference(current.id) }}</h1><p>Envoyée le {{ current.createdAt | date:'dd/MM/yyyy à HH:mm' }}</p></div>
          <span class="status" [class]="'status ' + statusClass(current.status)">{{ statusLabel(current.status) }}</span>
        </header>

        <section class="progress-card" aria-labelledby="progress-title">
          <div class="section-heading"><div><p class="eyebrow">Suivi</p><h2 id="progress-title">Progression de votre demande</h2></div></div>
          @if (current.status === 'CANCELLED') {
            <div class="cancelled" role="status"><span class="material-icons" aria-hidden="true">cancel</span><div><strong>Demande annulée</strong><p>Cette demande ne suit plus le parcours de traitement.</p></div></div>
          } @else {
            <ol class="progress" aria-label="Étapes de traitement du devis">
              @for (step of progressSteps(); track step.label; let index = $index) {
                <li [class]="'progress-step progress-step--' + step.state" [attr.aria-current]="step.state === 'current' ? 'step' : null">
                  <span class="marker" aria-hidden="true">{{ step.state === 'complete' ? '✓' : index + 1 }}</span>
                  <span><strong>{{ step.label }}</strong><small>{{ progressStateLabel(step.state) }}</small></span>
                </li>
              }
            </ol>
          }
        </section>

        @if (current.status === 'QUOTED' || current.status === 'ACCEPTED' || current.status === 'REJECTED') {
          <div class="document-actions">
            <button type="button" [disabled]="pdfDownloading()" (click)="downloadPdf(current.id)">
              {{ pdfDownloading() ? 'Téléchargement…' : 'Télécharger le PDF' }}
            </button>
          </div>
          @if (pdfError()) { <p class="action-error" role="alert">{{ pdfError() }}</p> }
        }

        @if (current.status === 'ACCEPTED' && current.lines?.length) {
          <section class="progress-card" role="status">
            <h2>✓ Devis accepté</h2>
            <p>Votre devis a été accepté. Complétez les informations de livraison et de paiement pour confirmer votre commande.</p>
            <a class="back-link" routerLink="/checkout" [queryParams]="{ quotationId: current.id }">Continuer vers la commande</a>
          </section>
        }

        @if ((current.status === 'QUOTED' || current.status === 'ACCEPTED' || current.status === 'REJECTED') && current.lines?.length) {
          <article class="commercial-document" aria-labelledby="commercial-title">
            <header class="document-header">
              <div><strong>WWTRADING</strong><small>Expertise Konica Minolta &amp; Develop</small></div>
              <div><h2 id="commercial-title">DEVIS COMMERCIAL</h2><p>N° {{ current.id }}</p></div>
            </header>
            <div class="document-meta">
              <p><strong>Date</strong><br>{{ current.updatedAt | date:'dd/MM/yyyy' }}</p>
              <p><strong>Validité</strong><br>{{ current.validUntil | date:'dd/MM/yyyy' }}</p>
              <p><strong>Client</strong><br>{{ current.fullName }}<br>{{ current.company || '' }}<br>{{ current.city }}</p>
            </div>
            <h3>Détail du devis</h3>
            <div class="document-table-wrap"><table>
              <thead><tr><th scope="col">Produit</th><th scope="col">Qté</th><th scope="col">PU</th><th scope="col">Total</th></tr></thead>
              <tbody>@for (line of current.lines; track line.productId) {
                <tr><td>{{ line.productName }} <small>{{ line.productReference }}</small></td>
                  <td>{{ line.proposedQuantity }}</td><td>{{ line.proposedUnitPrice | number:'1.2-2' }} DH</td>
                  <td>{{ line.proposedLineTotal | number:'1.2-2' }} DH</td></tr>
              }</tbody>
            </table></div>
            <dl class="document-totals">
              <div><dt>Sous-total</dt><dd>{{ current.subtotal | number:'1.2-2' }} DH</dd></div>
              <div><dt>Remise</dt><dd>{{ current.discount | number:'1.2-2' }} DH</dd></div>
              <div><dt>Livraison</dt><dd>{{ current.deliveryFee | number:'1.2-2' }} DH</dd></div>
              <div class="document-total"><dt>TOTAL</dt><dd>{{ current.finalTotal | number:'1.2-2' }} DH</dd></div>
            </dl>
            @if (current.commercialNotes) { <section class="document-note"><h3>Conditions et note commerciale</h3><p>{{ current.commercialNotes }}</p></section> }
          </article>
          <div class="document-actions">
            <button type="button" (click)="printQuotation()">Imprimer le devis</button>
            @if (current.status === 'QUOTED') {
            <button type="button" [disabled]="deciding()" (click)="decide('ACCEPTED')">Accepter le devis</button>
            <button type="button" [disabled]="deciding()" (click)="decide('REJECTED')">Refuser le devis</button>
            }
          </div>
          @if (decisionError()) { <p class="action-error" role="alert">{{ decisionError() }}</p> }
        }

        @if ((current.status !== 'QUOTED' && current.status !== 'ACCEPTED' && current.status !== 'REJECTED') || !current.lines?.length) {
        <div class="content-grid">
          <section class="panel" aria-labelledby="request-title">
            <div class="section-heading"><div><p class="eyebrow">Votre besoin</p><h2 id="request-title">Détails de la demande</h2></div></div>
            @if (current.lines?.length) {
              <h3>Produits demandés</h3>
              <ul>@for (line of current.lines; track line.productId) {
                <li>{{ line.productName }} — quantité {{ line.requestedQuantity }}</li>
              }</ul>
            } @else {
            <dl>
              <div><dt>Service</dt><dd>{{ serviceLabel(current.serviceType) }}</dd></div>
              <div><dt>Équipement souhaité</dt><dd>{{ current.requestedEquipment || 'Non renseigné' }}</dd></div>
              <div><dt>Marque / modèle</dt><dd>{{ current.brandModel || 'Non renseigné' }}</dd></div>
              <div><dt>Quantité</dt><dd>{{ current.quantity }}</dd></div>
              <div><dt>Délai souhaité</dt><dd>{{ timelineLabel(current.desiredTimeline) }}</dd></div>
              <div><dt>Budget estimatif</dt><dd>{{ current.estimatedBudget === null ? 'Non renseigné' : ((current.estimatedBudget | number:'1.2-2') + ' DH') }}</dd></div>
            </dl>
            }
            @if (current.description) { <div class="text-block"><h3>Description</h3><p>{{ current.description }}</p></div> }
            @if (current.additionalMessage) { <div class="text-block"><h3>Message complémentaire</h3><p>{{ current.additionalMessage }}</p></div> }
          </section>

          <aside class="side-column">
            <section class="panel proposal" aria-labelledby="proposal-title">
              <div class="section-heading"><div><p class="eyebrow">Proposition</p><h2 id="proposal-title">Réponse commerciale</h2></div></div>
              @if (current.proposedAmount !== null) {
                <p class="amount"><span>Montant proposé</span><strong>{{ current.proposedAmount | number:'1.2-2' }} DH</strong></p>
              } @else { <p class="muted">La proposition commerciale n’est pas encore disponible.</p> }
              @if (current.commercialNotes) { <div class="commercial-notes"><h3>Notes commerciales</h3><p>{{ current.commercialNotes }}</p></div> }
            </section>

            <section class="panel" aria-labelledby="contact-title">
              <div class="section-heading"><div><p class="eyebrow">Coordonnées</p><h2 id="contact-title">Contact</h2></div></div>
              <dl><div><dt>Nom</dt><dd>{{ current.fullName }}</dd></div><div><dt>Société</dt><dd>{{ current.company || 'Non renseignée' }}</dd></div><div><dt>Email</dt><dd>{{ current.email }}</dd></div><div><dt>Téléphone</dt><dd>{{ current.phone }}</dd></div><div><dt>Ville</dt><dd>{{ current.city }}</dd></div></dl>
            </section>
          </aside>
        </div>
        @if (current.status === 'QUOTED') {
          <div class="document-actions">
            <button type="button" [disabled]="deciding()" (click)="decide('ACCEPTED')">Accepter le devis</button>
            <button type="button" [disabled]="deciding()" (click)="decide('REJECTED')">Refuser le devis</button>
          </div>
          @if (decisionError()) { <p class="action-error" role="alert">{{ decisionError() }}</p> }
        }
        }

        @if (current.status === 'PENDING') {
          <section class="cancel-card">
            <div><h2>Annuler cette demande</h2><p>L’annulation est disponible uniquement avant le début de son étude.</p></div>
            <button type="button" [disabled]="cancelling()" (click)="cancelQuotation()">{{ cancelling() ? 'Annulation…' : 'Annuler la demande' }}</button>
          </section>
        }
          @if (cancelError()) { <p class="action-error" role="alert">{{ cancelError() }}</p> }
        }
      }
    </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    :host { display: block; background: #f6f4f2; color: #1a1a18; }.detail-page { width: min(1180px, calc(100% - 2rem)); min-height: 70vh; margin: 0 auto; padding: clamp(2rem, 6vw, 5rem) 0; }
    .back-link { display: inline-flex; align-items: center; gap: .45rem; margin-bottom: 1.5rem; color: #8f123f; font-weight: 800; text-decoration: none; }.back-link .material-icons { font-size: 1.2rem; }
    .detail-heading { display: flex; align-items: end; justify-content: space-between; gap: 2rem; margin-bottom: 1.5rem; }.detail-heading h1 { margin: 0; font-size: clamp(2rem, 5vw, 3.6rem); line-height: 1; letter-spacing: -.045em; }.detail-heading p:not(.eyebrow) { margin: .65rem 0 0; color: #68635f; }
    .eyebrow { margin: 0 0 .4rem; color: #af0f50; font-size: .72rem; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }
    .panel, .progress-card, .cancel-card, .state-card { border: 1px solid #e0dcda; border-radius: 1rem; background: #fff; box-shadow: 0 1rem 2.8rem rgb(26 26 24 / 6%); }.panel, .progress-card { padding: clamp(1.2rem, 3vw, 1.75rem); }
    .section-heading h2 { margin: 0; font-size: 1.35rem; }.progress-card { margin-bottom: 1rem; }
    .progress { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 1.6rem 0 0; padding: 0; list-style: none; }.progress-step { position: relative; display: grid; grid-template-columns: 2.4rem 1fr; gap: .7rem; align-items: center; color: #8a837f; }.progress-step:not(:last-child)::after { position: absolute; top: 1.15rem; left: 2.4rem; width: calc(100% - 2.4rem); height: 2px; background: #ddd7d4; content: ''; }.progress-step--complete:not(:last-child)::after { background: #af0f50; }.marker { z-index: 1; display: grid; width: 2.4rem; height: 2.4rem; place-items: center; border: 2px solid #d7d0cc; border-radius: 50%; background: #fff; font-weight: 850; }.progress-step > span:last-child { z-index: 1; display: grid; gap: .15rem; width: fit-content; padding-right: .5rem; background: #fff; }.progress-step small { font-size: .7rem; }.progress-step--complete .marker { border-color: #af0f50; background: #af0f50; color: #fff; }.progress-step--current .marker { border-color: #af0f50; color: #af0f50; box-shadow: 0 0 0 .28rem rgb(175 15 80 / 10%); }.progress-step--current strong, .progress-step--current small { color: #8f123f; }
    .cancelled { display: flex; align-items: center; gap: .8rem; margin-top: 1.25rem; padding: 1rem; border: 1px solid #e0a2a2; border-radius: .7rem; background: #fff1f1; color: #812b2b; }.cancelled .material-icons { font-size: 2rem; }.cancelled p { margin: .2rem 0 0; }
    .content-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(18rem, .85fr); gap: 1rem; }.side-column { display: grid; align-content: start; gap: 1rem; }
    dl { margin: 1.2rem 0 0; }dl div { display: grid; grid-template-columns: minmax(9rem, .7fr) 1.3fr; gap: 1rem; padding: .75rem 0; border-bottom: 1px solid #eeeae8; }dt { color: #706a66; font-size: .82rem; font-weight: 750; }dd { margin: 0; font-weight: 700; overflow-wrap: anywhere; }
    .text-block { margin-top: 1.3rem; }.text-block h3, .commercial-notes h3 { margin: 0 0 .45rem; font-size: .9rem; }.text-block p, .commercial-notes p, .muted { margin: 0; color: #625d59; line-height: 1.65; white-space: pre-wrap; }.amount { display: grid; gap: .3rem; margin: 1.2rem 0; }.amount span { color: #706a66; }.amount strong { color: #8f123f; font-size: 1.8rem; }
    .status { display: inline-flex; padding: .45rem .75rem; border-radius: 999px; font-size: .75rem; font-weight: 850; }.status--pending { background: #fff2cc; color: #72520a; }.status--review { background: #e8f0ff; color: #244d8e; }.status--quoted { background: #f4e8ff; color: #693190; }.status--accepted { background: #e5f5e9; color: #246137; }.status--rejected, .status--cancelled { background: #fde8e8; color: #8b2d2d; }
    .cancel-card { display: flex; align-items: center; justify-content: space-between; gap: 1.5rem; margin-top: 1rem; padding: 1.25rem 1.5rem; }.cancel-card h2, .cancel-card p { margin: 0; }.cancel-card p { margin-top: .3rem; color: #68635f; }.cancel-card button, .state-card button { min-height: 2.75rem; padding: .65rem 1rem; border: 1px solid #a83232; border-radius: 999px; background: #fff; color: #8b2d2d; font: inherit; font-weight: 800; cursor: pointer; }.cancel-card button:disabled { opacity: .5; }.action-error { color: #8b2d2d; font-weight: 750; }
    .state-card { display: grid; justify-items: center; gap: .75rem; padding: clamp(2.5rem, 8vw, 5rem) 1.5rem; text-align: center; }.state-card h2, .state-card p { margin: 0; }.state-card p { color: #68635f; }.state-card > .material-icons { color: #af0f50; font-size: 2.5rem; }.spinner { width: 2rem; height: 2rem; border: 3px solid #eadde2; border-top-color: #af0f50; border-radius: 50%; animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
    a:focus-visible, button:focus-visible { outline: 3px solid #d02870; outline-offset: 3px; }
    @media (max-width: 820px) { .content-grid { grid-template-columns: 1fr; }.progress { grid-template-columns: 1fr; gap: .8rem; }.progress-step:not(:last-child)::after { top: 2.4rem; left: 1.15rem; width: 2px; height: calc(100% + .8rem); }.progress-step > span:last-child { padding-block: .2rem; }.cancel-card { align-items: stretch; flex-direction: column; } }
    @media (max-width: 520px) { .detail-page { width: min(100% - 1rem, 1180px); }.detail-heading { align-items: flex-start; flex-direction: column; gap: 1rem; }dl div { grid-template-columns: 1fr; gap: .25rem; }.cancel-card button { width: 100%; } }
    .commercial-document { margin: 1rem auto; padding: clamp(1.25rem, 4vw, 3rem); border: 1px solid #ded9d7; background: #fff; box-shadow: 0 1rem 2.8rem rgb(26 26 24 / 6%); }
    .document-header, .document-meta { display: flex; justify-content: space-between; gap: 1.5rem; flex-wrap: wrap; }
    .document-header { padding-bottom: 1.5rem; border-bottom: 3px solid #af0f50; }.document-header strong { color: #af0f50; font-size: 1.5rem; }.document-header small { display: block; }.document-header h2 { margin: 0; font-size: 1.45rem; }.document-header p { margin: .3rem 0 0; overflow-wrap: anywhere; }
    .document-meta { margin: 1.5rem 0 2rem; }.document-meta p { margin: 0; line-height: 1.5; }
    .document-table-wrap { max-width: 100%; overflow-x: auto; }.commercial-document table { width: 100%; border-collapse: collapse; }.commercial-document th, .commercial-document td { padding: .8rem .55rem; border-bottom: 1px solid #ded9d7; text-align: left; }.commercial-document td:not(:first-child), .commercial-document th:not(:first-child) { text-align: right; white-space: nowrap; }.commercial-document td small { display: block; color: #655e5a; }
    .document-totals { width: min(100%, 22rem); margin: 1.5rem 0 0 auto; }.document-totals div { display: flex; justify-content: space-between; gap: 1rem; padding: .4rem 0; }.document-totals dd { margin: 0; }.document-total { border-top: 2px solid #af0f50; color: #8f123f; font-weight: 850; }.document-note { margin-top: 1.5rem; white-space: pre-wrap; }
    .document-actions { display: flex; gap: .75rem; flex-wrap: wrap; margin: 1rem 0; }.document-actions button { min-height: 2.75rem; padding: .6rem 1rem; border: 1px solid #af0f50; background: #fff; color: #8f123f; font: inherit; font-weight: 750; cursor: pointer; }.document-actions button:disabled { opacity: .5; cursor: not-allowed; }
    @media (max-width: 520px) { .commercial-document { padding: 1rem; }.commercial-document th, .commercial-document td { padding: .5rem .3rem; font-size: .8rem; } }
    @media print { .detail-page { width: 100%; min-height: 0; padding: 0; }.commercial-document { margin: 0; padding: 0; border: 0; box-shadow: none; }.document-actions, .progress-card, .detail-heading, .back-link, .cancel-card, .content-grid { display: none !important; } .commercial-document table { break-inside: auto; } .commercial-document tr { break-inside: avoid; } }
    @media (prefers-reduced-motion: reduce) { .spinner { animation: none; } }
  `]
})
export class ClientQuotationDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly quotationApi = inject(QuotationApiService);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly quotation = signal<Quotation | null>(null);
  protected readonly cancelling = signal(false);
  protected readonly cancelError = signal<string | null>(null);
  protected readonly deciding = signal(false);
  protected readonly decisionError = signal<string | null>(null);
  protected readonly pdfDownloading = signal(false);
  protected readonly pdfError = signal<string | null>(null);
  private quotationId: string | null = null;

  protected readonly progressSteps = computed<readonly ProgressStep[]>(() => {
    const status = this.quotation()?.status;
    const sequence: readonly QuotationStatus[] = ['PENDING', 'IN_REVIEW', 'QUOTED'];
    const currentIndex = sequence.indexOf(status as QuotationStatus);
    const steps: ProgressStep[] = sequence.map((step, index) => ({
      label: getQuotationStatusLabel(step),
      state: status === 'ACCEPTED' || status === 'REJECTED' || index < currentIndex
        ? 'complete'
        : index === currentIndex ? 'current' : 'pending'
    }));
    steps.push({
      label: status === 'ACCEPTED' ? 'Accepté' : status === 'REJECTED' ? 'Refusé' : 'Décision',
      state: status === 'ACCEPTED' || status === 'REJECTED' ? 'current' : 'pending'
    });
    return steps;
  });

  ngOnInit(): void {
    this.quotationId = this.route.snapshot.paramMap.get('id');
    if (!this.quotationId) { this.error.set('Identifiant de demande invalide.'); this.loading.set(false); return; }
    this.load(this.quotationId);
  }

  protected retry(): void { if (this.quotationId) this.load(this.quotationId); }
  protected printQuotation(): void { globalThis.print(); }
  protected downloadPdf(id: string): void {
    if (this.pdfDownloading()) return;
    this.pdfDownloading.set(true);
    this.pdfError.set(null);
    this.quotationApi.getQuotationPdf(id).pipe(finalize(() => this.pdfDownloading.set(false))).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `devis-${id}.pdf`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        globalThis.setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: (failure: unknown) => {
        this.pdfError.set(failure instanceof HttpErrorResponse && failure.status === 404
          ? 'Ce devis est introuvable.'
          : failure instanceof HttpErrorResponse && failure.status === 403
            ? 'Vous n’avez pas accès à ce document.'
            : 'Le PDF du devis ne peut pas être téléchargé pour le moment.');
      }
    });
  }
  protected shortReference(id: string): string { return id.slice(0, 8).toUpperCase(); }
  protected statusLabel(status: QuotationStatus): string { return getQuotationStatusLabel(status); }
  protected serviceLabel(type: string): string { return getServiceTypeLabel(type); }
  protected progressStateLabel(state: ProgressState): string { return state === 'complete' ? 'Terminée' : state === 'current' ? 'Étape actuelle' : 'À venir'; }
  protected timelineLabel(value: string | null): string {
    switch (value) { case 'ASAP': return 'Dès que possible'; case 'THIS_WEEK': return 'Cette semaine'; case 'THIS_MONTH': return 'Ce mois-ci'; case 'TO_DEFINE': return 'À définir'; default: return value || 'Non renseigné'; }
  }
  protected statusClass(status: QuotationStatus): string {
    switch (status) { case 'PENDING': return 'status--pending'; case 'IN_REVIEW': return 'status--review'; case 'QUOTED': return 'status--quoted'; case 'ACCEPTED': return 'status--accepted'; case 'REJECTED': return 'status--rejected'; case 'CANCELLED': return 'status--cancelled'; default: return 'status--pending'; }
  }

  protected cancelQuotation(): void {
    const current = this.quotation();
    if (!current || current.status !== 'PENDING' || this.cancelling()) return;
    if (!globalThis.confirm('Voulez-vous vraiment annuler cette demande de devis ?')) return;
    this.cancelling.set(true);
    this.cancelError.set(null);
    this.quotationApi.cancelQuotation(current.id).pipe(finalize(() => this.cancelling.set(false))).subscribe({
      next: (updated) => this.quotation.set(updated),
      error: (failure: unknown) => this.handleCancellationError(failure)
    });
  }

  protected decide(status: 'ACCEPTED' | 'REJECTED'): void {
    const current = this.quotation();
    if (!current || current.status !== 'QUOTED' || this.deciding()) return;
    if (!globalThis.confirm(status === 'ACCEPTED' ? 'Accepter ce devis ?' : 'Refuser ce devis ?')) return;
    this.deciding.set(true);
    this.decisionError.set(null);
    this.quotationApi.decide(current.id, status).pipe(finalize(() => this.deciding.set(false))).subscribe({
      next: updated => this.quotation.set(updated),
      error: (failure: unknown) => {
        if (failure instanceof HttpErrorResponse && failure.status === 401) {
          this.decisionError.set('Votre session a expiré. Reconnexion en cours…');
          void this.auth.login(`/devis/mes-demandes/${current.id}`);
        } else if (failure instanceof HttpErrorResponse && failure.status === 403) {
          this.decisionError.set("Vous n'êtes pas autorisé à décider pour ce devis.");
        } else if (failure instanceof HttpErrorResponse && failure.status === 409) {
          this.decisionError.set('Ce devis ne peut plus être accepté ou refusé.');
        } else {
          this.decisionError.set('La décision n’a pas pu être enregistrée. Réessayez.');
        }
      }
    });
  }

  private load(id: string): void {
    this.loading.set(true); this.error.set(null);
    this.quotationApi.getQuotation(id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (result) => this.quotation.set(result),
      error: (failure: unknown) => this.handleError(failure)
    });
  }

  private handleError(failure: unknown): void {
    if (failure instanceof HttpErrorResponse && failure.status === 401) { this.error.set('Votre session a expiré. Reconnexion en cours…'); void this.auth.login(`/devis/mes-demandes/${this.quotationId ?? ''}`); return; }
    if (failure instanceof HttpErrorResponse && failure.status === 403) { this.error.set("Vous n'avez pas accès à cette demande de devis."); return; }
    if (failure instanceof HttpErrorResponse && failure.status === 404) { this.error.set('Cette demande de devis est introuvable.'); return; }
    this.error.set('Cette demande ne peut pas être chargée pour le moment.');
  }

  private handleCancellationError(failure: unknown): void {
    if (failure instanceof HttpErrorResponse && failure.status === 401) {
      this.cancelError.set('Votre session a expiré. Reconnexion en cours…');
      void this.auth.login(`/devis/mes-demandes/${this.quotationId ?? ''}`);
      return;
    }
    if (failure instanceof HttpErrorResponse && failure.status === 403) {
      this.cancelError.set("Vous n'êtes pas autorisé à annuler cette demande.");
      return;
    }
    if (failure instanceof HttpErrorResponse && (failure.status === 400 || failure.status === 409)) {
      this.cancelError.set('Cette demande ne peut plus être annulée.');
      return;
    }
    this.cancelError.set('L’annulation a échoué. Veuillez réessayer.');
  }
}
