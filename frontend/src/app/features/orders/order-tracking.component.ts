import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { OrderApiService } from './order-api.service';
import {
  PublicOrderTrackingResponse,
  deliveryAreaLabel,
  deliveryFeeWaivedLabel,
  orderStatusLabel,
  paymentPlanLabel,
  paymentStatusLabel
} from './order.models';

@Component({
  selector: 'wwt-order-tracking',
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="tracking-page" aria-labelledby="tracking-title">
      <p class="eyebrow">Suivi de commande</p>
      <h1 id="tracking-title">Consulter l’avancement de votre commande</h1>
      <p class="intro">
        Accédez au statut de votre commande et aux instructions de règlement grâce au lien sécurisé reçu par e-mail.
      </p>

      <section class="token-lookup" aria-label="Rechercher par jeton de suivi">
        <div class="lookup-row">
          <input
            type="text"
            placeholder="Collez votre jeton ou lien de suivi sécurisé…"
            [value]="tokenInput()"
            (input)="tokenInput.set($any($event.target).value)"
          >
          <button type="button" [disabled]="loading() || !tokenInput().trim()" (click)="lookupFromInput()">
            {{ loading() ? 'Vérification…' : 'Consulter' }}
          </button>
        </div>
      </section>

      @if (loading()) {
        <output class="card">Chargement des informations de suivi…</output>
      } @else if (error()) {
        <section class="card alert" role="alert">
          <h2>Suivi indisponible</h2>
          <p>{{ error() }}</p>
        </section>
      } @else {
        @let order = tracking();
        @if (order) {
          <section class="card" aria-label="Détails de suivi">
          <div class="header-row">
            <div>
              <p class="eyebrow">Référence</p>
              <h2>{{ order.orderNumber }}</h2>
              @if (order.recipientFirstName) {
                <p class="muted">Client : <strong>{{ order.recipientFirstName }}</strong> · Créée le {{ order.createdAt | date:'dd/MM/yyyy HH:mm' }}</p>
              } @else {
                <p class="muted">Créée le {{ order.createdAt | date:'dd/MM/yyyy HH:mm' }}</p>
              }
            </div>
            <div class="badges">
              <span class="status-pill">{{ orderStatusLabel(order.status, order.deliveryMethod) }}</span>
              <span class="payment-pill">{{ paymentStatusLabel(order.paymentStatus) }}</span>
            </div>
          </div>

          <div class="grid-two">
            <div class="panel">
              <h3>Réception &amp; Livraison</h3>
              <p>
                Mode :
                <strong>{{ order.deliveryMethod === 'PICKUP' ? 'Retrait au showroom (Kénitra)' : 'Livraison à domicile' }}</strong>
              </p>
              @if (order.shippingCity) {
                <p>Ville : <strong>{{ order.shippingCity }}</strong></p>
              }
              @if (deliveryAreaLabel(order.deliveryArea); as areaText) {
                <p>Zone tarifaire : <strong>{{ areaText }}</strong></p>
              }
              @if (deliveryFeeWaivedLabel(order.deliveryFeeWaivedReason); as waivedText) {
                <p class="waived">{{ waivedText }}</p>
              }
            </div>

            <div class="panel">
              <h3>Paiement &amp; Virement</h3>
              <p>Option : <strong>{{ paymentPlanLabel(order.paymentPlan, order.paymentMethod, order.deliveryMethod) }}</strong></p>
              <p>Montant réglé : <strong>{{ order.amountPaid | number:'1.2-2' }} DH</strong></p>
              <p>Reste à régler : <strong>{{ order.outstandingBalance | number:'1.2-2' }} DH</strong></p>
              @if (order.paymentPlan === 'SPLIT_50_50' || order.paymentMethod === 'SPLIT_50_50') {
                <p>Acompte 50 % (commande) : <strong>{{ order.upfrontPaymentAmount | number:'1.2-2' }} DH</strong></p>
                <p>Solde 50 % (livraison) : <strong>{{ order.remainingDeliveryAmount | number:'1.2-2' }} DH</strong></p>
                @if (order.secondInstallmentPaymentMethod) {
                  <p>Mode de règlement du solde : <strong>{{ order.secondInstallmentPaymentMethod === 'CASH' ? 'Espèces à la réception' : 'Virement bancaire' }}</strong></p>
                }
              }
            </div>
          </div>

          @if (order.paymentBankName || order.paymentBankRib) {
            <div class="bank-panel">
              <h3>Coordonnées bancaires pour le virement</h3>
              @if (order.paymentBankName) {
                <p>Banque : <strong>{{ order.paymentBankName }}</strong></p>
              }
              @if (order.paymentBankBeneficiary) {
                <p>Bénéficiaire : <strong>{{ order.paymentBankBeneficiary }}</strong></p>
              }
              @if (order.paymentBankRib) {
                <p>RIB : <strong>{{ order.paymentBankRib }}</strong></p>
              }
              <p>Motif à indiquer : <strong>{{ order.orderNumber }}</strong></p>

              @if (order.transferProofSubmitted) {
                <div style="margin-top: 0.75rem; padding: 0.85rem; background: #eef6ef; border: 1px solid #8dad91; border-radius: 8px;">
                  <p class="proof-ok" style="margin: 0 0 0.5rem; color: #286235; font-weight: 700;">✓ Preuve de virement transmise. Notre équipe vérifiera votre règlement sous peu.</p>
                  <button
                    type="button"
                    style="padding: 0.45rem 0.85rem; font-size: 0.82rem; background: #8f123f; border: 1px solid #8f123f; color: #fff; border-radius: 999px; cursor: pointer; font-weight: 700;"
                    [disabled]="downloadingGuestProof()"
                    (click)="downloadGuestProof()">
                    {{ downloadingGuestProof() ? 'Téléchargement…' : 'Télécharger le reçu' }}
                  </button>
                </div>
              } @else if (order.paymentStatus !== 'PAID') {
                <div class="proof-upload">
                  <label>
                    <strong>Transmettre votre preuve de virement (JPG ou PNG)</strong>
                    <input type="file" accept="image/jpeg,image/png" (change)="onProofFileSelected($event)">
                  </label>
                  @if (selectedProofFile()) {
                    <button type="button" [disabled]="uploadingProof()" (click)="submitProof()">
                      {{ uploadingProof() ? 'Envoi en cours…' : 'Envoyer la preuve de virement' }}
                    </button>
                  }
                  @if (proofMessage()) {
                    <p class="muted">{{ proofMessage() }}</p>
                  }
                </div>
              }
            </div>
          }

          <h3>Articles commandés</h3>
          <ul class="items-list">
            @for (item of order.items; track item.productReference) {
              <li>
                <div>
                  <strong>{{ item.productName || item.productReference }}</strong>
                  @if (item.productName) {
                    <small>{{ item.productReference }}</small>
                  }
                </div>
                <div>
                  {{ item.quantity }} × {{ item.unitPrice | number:'1.2-2' }} DH =
                  <strong>{{ item.subtotal | number:'1.2-2' }} DH</strong>
                </div>
              </li>
            }
          </ul>

          <div class="totals">
            <p><span>Sous-total</span><strong>{{ order.subtotal | number:'1.2-2' }} DH</strong></p>
            @if (order.discount > 0) {
              <p><span>Remise</span><strong>− {{ order.discount | number:'1.2-2' }} DH</strong></p>
            }
            <p><span>Frais de livraison</span><strong>{{ order.deliveryFee | number:'1.2-2' }} DH</strong></p>
            <p class="grand-total"><span>Total TTC</span><strong>{{ order.total | number:'1.2-2' }} DH</strong></p>
          </div>

          <div class="account-cta">
            <p>
              <strong>Rattacher cette commande à un compte client :</strong>
              en vous connectant ou en créant un compte WWTRADING avec la même adresse e-mail vérifiée, vos commandes invitées sont automatiquement rattachées à votre historique.
            </p>
            <a routerLink="/register">Créer un compte client</a>
          </div>
        </section>
        }
      }
    </main>
  `,
  styles: [`
    :host { display: block; background: #f6f4f2; min-height: 75vh; }
    .tracking-page { max-width: 960px; margin: 0 auto; padding: clamp(2.5rem, 6vw, 5rem) clamp(1rem, 4vw, 3rem); }
    .eyebrow { margin: 0 0 .45rem; color: #af0f50; font-size: .75rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    h1 { margin: 0; color: #1a1a18; font-size: clamp(2rem, 5vw, 3.2rem); line-height: 1.05; }
    .intro { max-width: 44rem; color: #625f5c; line-height: 1.6; }
    .token-lookup { margin: 1.5rem 0; padding: 1.25rem; border: 1px solid rgb(26 26 24 / 12%); background: #fff; }
    .lookup-row { display: flex; gap: .75rem; flex-wrap: wrap; }
    .lookup-row input { flex: 1 1 18rem; padding: .7rem .9rem; border: 1px solid rgb(26 26 24 / 24%); font: inherit; }
    .lookup-row button { padding: .7rem 1.3rem; border: 1px solid #af0f50; border-radius: 999px; background: #af0f50; color: #fff; font: inherit; font-weight: 750; cursor: pointer; }
    .lookup-row button:disabled { opacity: .55; cursor: not-allowed; }
    .card { display: grid; gap: 1.25rem; margin-top: 1.25rem; padding: clamp(1.25rem, 4vw, 2rem); border: 1px solid rgb(26 26 24 / 12%); background: #fff; }
    .card.alert { border-left: 4px solid #af0f50; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; padding-bottom: 1rem; border-bottom: 1px solid rgb(26 26 24 / 12%); }
    .header-row h2 { margin: 0; font-size: 1.6rem; }
    .muted { margin: .35rem 0 0; color: #625f5c; font-size: .9rem; }
    .badges { display: flex; gap: .5rem; flex-wrap: wrap; }
    .status-pill, .payment-pill { padding: .35rem .75rem; border-radius: 999px; font-size: .78rem; font-weight: 800; }
    .status-pill { background: #1a1a18; color: #fff; }
    .payment-pill { background: #f7dce8; color: #8f123f; }
    .grid-two { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: 1rem; }
    .panel { padding: 1rem; border: 1px solid rgb(26 26 24 / 12%); background: #faf9f8; }
    .panel h3 { margin: 0 0 .6rem; font-size: 1rem; }
    .panel p { margin: .35rem 0; color: #3d3b38; }
    .waived { color: #196f3d; font-weight: 700; }
    .bank-panel { padding: 1.15rem; border-left: 4px solid #1a1a18; background: #f7f5f3; }
    .bank-panel h3 { margin: 0 0 .5rem; font-size: 1rem; }
    .bank-panel p { margin: .3rem 0; }
    .proof-ok { margin-top: .75rem; padding: .6rem .85rem; background: #e8f6ee; color: #196f3d; font-weight: 700; }
    .proof-upload { margin-top: .85rem; display: grid; gap: .6rem; }
    .proof-upload button { justify-self: start; padding: .55rem 1rem; border: 1px solid #af0f50; border-radius: 999px; background: #af0f50; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
    .items-list { list-style: none; margin: 0; padding: 0; display: grid; gap: .65rem; }
    .items-list li { display: flex; justify-content: space-between; gap: 1rem; padding-bottom: .65rem; border-bottom: 1px solid rgb(26 26 24 / 10%); }
    .items-list small { display: block; color: #625f5c; }
    .totals { border-top: 2px solid #1a1a18; padding-top: .85rem; display: grid; gap: .45rem; }
    .totals p { display: flex; justify-content: space-between; margin: 0; }
    .totals .grand-total { font-size: 1.15rem; padding-top: .45rem; border-top: 1px solid rgb(26 26 24 / 15%); }
    .account-cta { padding: 1rem; border: 1px solid rgb(175 15 80 / 25%); background: #fdf6f9; display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .account-cta p { margin: 0; flex: 1 1 20rem; font-size: .9rem; color: #3d3b38; }
    .account-cta a { padding: .6rem 1rem; border: 1px solid #af0f50; border-radius: 999px; background: #fff; color: #8f123f; font-weight: 750; text-decoration: none; }
  `]
})
export class OrderTrackingComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly orderApi = inject(OrderApiService);

  protected readonly tokenInput = signal('');
  protected readonly activeToken = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tracking = signal<PublicOrderTrackingResponse | null>(null);
  protected readonly selectedProofFile = signal<File | null>(null);
  protected readonly uploadingProof = signal(false);
  protected readonly downloadingGuestProof = signal(false);
  protected readonly proofMessage = signal<string | null>(null);

  protected readonly orderStatusLabel = orderStatusLabel;
  protected readonly paymentStatusLabel = paymentStatusLabel;
  protected readonly paymentPlanLabel = paymentPlanLabel;
  protected readonly deliveryAreaLabel = deliveryAreaLabel;
  protected readonly deliveryFeeWaivedLabel = deliveryFeeWaivedLabel;

  ngOnInit(): void {
    const queryToken = this.route.snapshot.queryParamMap.get('token');
    if (queryToken && queryToken.trim()) {
      const extracted = this.extractToken(queryToken);
      this.tokenInput.set(extracted);
      this.fetchTracking(extracted);
    }
  }

  protected lookupFromInput(): void {
    const raw = this.tokenInput().trim();
    if (!raw) return;
    const extracted = this.extractToken(raw);
    this.fetchTracking(extracted);
  }

  private fetchTracking(token: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.activeToken.set(token);
    this.orderApi.trackGuestOrder(token).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (res) => this.tracking.set(res),
      error: (err: unknown) => {
        this.tracking.set(null);
        if (err instanceof HttpErrorResponse && err.status === 429) {
          this.error.set('Trop de tentatives. Veuillez patienter une minute avant de réessayer.');
        } else {
          this.error.set('Lien de suivi invalide, expiré ou révoqué. Vérifiez le lien reçu dans votre e-mail de confirmation.');
        }
      }
    });
  }

  protected onProofFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      this.proofMessage.set('La preuve de virement doit être au format JPG ou PNG.');
      input.value = '';
      return;
    }
    this.proofMessage.set(null);
    this.selectedProofFile.set(file);
  }

  protected submitProof(): void {
    const token = this.activeToken();
    const file = this.selectedProofFile();
    if (!token || !file || this.uploadingProof()) return;
    this.uploadingProof.set(true);
    this.proofMessage.set(null);
    this.orderApi.uploadGuestTransferProof(token, file).pipe(
      finalize(() => this.uploadingProof.set(false))
    ).subscribe({
      next: (updated) => {
        this.tracking.set(updated);
        this.selectedProofFile.set(null);
        this.proofMessage.set('Preuve de virement envoyée avec succès.');
      },
      error: () => this.proofMessage.set('Impossible d’envoyer la preuve de virement. Réessayez.')
    });
  }

  protected downloadGuestProof(): void {
    const token = this.activeToken();
    if (!token || this.downloadingGuestProof()) return;
    this.downloadingGuestProof.set(true);
    this.proofMessage.set(null);
    this.orderApi.getGuestTransferProof(token, true).pipe(
      finalize(() => this.downloadingGuestProof.set(false))
    ).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        const ext = blob.type === 'image/jpeg' ? 'jpg' : 'png';
        link.download = `recu-virement-${this.tracking()?.orderNumber || 'commande'}.${ext}`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        globalThis.setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          if (err.status === 404) {
            this.proofMessage.set('Le fichier du reçu est introuvable sur le serveur.');
          } else if (err.status === 401 || err.status === 403) {
            this.proofMessage.set('Lien de suivi expiré ou non autorisé.');
          } else if (err.status >= 500) {
            this.proofMessage.set('Erreur serveur lors de la récupération du reçu.');
          } else {
            this.proofMessage.set('Impossible de télécharger le reçu de virement.');
          }
        } else {
          this.proofMessage.set('Impossible de télécharger le reçu de virement.');
        }
      }
    });
  }

  private extractToken(rawInput: string): string {
    const trimmed = rawInput.trim();
    const match = /[?&]token=([^&#\s]+)/.exec(trimmed);
    return match ? decodeURIComponent(match[1]) : trimmed;
  }
}

