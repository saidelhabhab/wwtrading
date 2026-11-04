import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { QuotationApiService } from './quotation-api.service';
import {
  getQuotationStatusLabel,
  getServiceTypeLabel,
  PageResponse,
  Quotation,
  QuotationStatus
} from './quotation.models';

@Component({
  selector: 'wwt-client-quotation-history',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, DashboardLayoutComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Mes demandes de devis">
    <main class="quotation-page" aria-labelledby="quotation-history-title">
      <header class="page-heading">
        <div>
          <p class="eyebrow">Espace client</p>
          <h1 id="quotation-history-title">Mes demandes de devis</h1>
          <p>Retrouvez vos demandes et suivez leur traitement par l’équipe WWTRADING.</p>
        </div>
        <a class="primary-link" routerLink="/devis">Nouvelle demande</a>
      </header>

      @if (loading()) {
        <section class="state-card" role="status" aria-live="polite">
          <span class="spinner" aria-hidden="true"></span>
          <h2>Chargement de vos demandes…</h2>
        </section>
      } @else if (error()) {
        <section class="state-card state-card--error" role="alert">
          <span class="material-icons" aria-hidden="true">error_outline</span>
          <h2>Chargement impossible</h2>
          <p>{{ error() }}</p>
          <button type="button" (click)="load()">Réessayer</button>
        </section>
      } @else {
        @if (page(); as quotationPage) {
          @if (quotationPage.content.length === 0) {
          <section class="state-card">
            <span class="material-icons" aria-hidden="true">description</span>
            <h2>Aucune demande pour le moment</h2>
            <p>Votre prochaine demande apparaîtra ici après son envoi.</p>
            <a class="primary-link" routerLink="/devis">Demander un devis</a>
          </section>
          } @else {
          <section class="quotations-card" aria-label="Liste de mes demandes de devis">
            <div class="table-heading">
              <div><span>Suivi</span><h2>Demandes récentes</h2></div>
              <strong>{{ quotationPage.totalElements }} demande{{ quotationPage.totalElements > 1 ? 's' : '' }}</strong>
            </div>

            <div class="quotation-table">
              <div class="quotation-row quotation-row--header" aria-hidden="true">
                <span>Demande</span><span>Service</span><span>Date</span><span>Budget</span><span>Statut</span><span></span>
              </div>
              @for (quotation of quotationPage.content; track quotation.id) {
                <article class="quotation-row">
                  <div class="reference"><small>Demande</small><strong>#{{ shortReference(quotation.id) }}</strong></div>
                  <div><small>Service</small><span>{{ serviceLabel(quotation.serviceType) }}</span></div>
                  <div><small>Date</small><span>{{ quotation.createdAt | date:'dd/MM/yyyy' }}</span></div>
                  <div><small>Budget</small><span>{{ quotation.estimatedBudget === null ? 'Non renseigné' : ((quotation.estimatedBudget | number:'1.2-2') + ' DH') }}</span></div>
                  <div><small>Statut</small><span class="status" [class]="'status ' + statusClass(quotation.status)">{{ statusLabel(quotation.status) }}</span></div>
                  <a class="detail-link" [routerLink]="['/devis/mes-demandes', quotation.id]" [attr.aria-label]="'Consulter la demande ' + quotation.id">
                    Voir le détail <span class="material-icons" aria-hidden="true">arrow_forward</span>
                  </a>
                </article>
              }
            </div>

            @if (quotationPage.totalPages > 1) {
              <nav class="pagination" aria-label="Pagination des demandes">
                <button type="button" [disabled]="quotationPage.first || loading()" (click)="load(quotationPage.number - 1)">Précédent</button>
                <span>Page {{ quotationPage.number + 1 }} sur {{ quotationPage.totalPages }}</span>
                <button type="button" [disabled]="quotationPage.last || loading()" (click)="load(quotationPage.number + 1)">Suivant</button>
              </nav>
            }
          </section>
          }
        }
      }
    </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    :host { display: block; background: #f6f4f2; color: #1a1a18; }
    .quotation-page { width: min(1180px, calc(100% - 2rem)); min-height: 70vh; margin: 0 auto; padding: clamp(2rem, 6vw, 5rem) 0; }
    .page-heading { display: flex; align-items: end; justify-content: space-between; gap: 2rem; margin-bottom: 2rem; }
    .eyebrow, .table-heading span { margin: 0 0 .45rem; color: #af0f50; font-size: .74rem; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }
    h1 { margin: 0; font-size: clamp(2rem, 5vw, 3.8rem); line-height: 1; letter-spacing: -.045em; }
    .page-heading p:not(.eyebrow) { max-width: 650px; margin: .85rem 0 0; color: #68635f; line-height: 1.6; }
    .primary-link, .state-card button { display: inline-flex; align-items: center; justify-content: center; min-height: 2.8rem; padding: .7rem 1.1rem; border: 1px solid #af0f50; border-radius: 999px; background: #af0f50; color: #fff; font: inherit; font-weight: 800; text-decoration: none; cursor: pointer; }
    .quotations-card, .state-card { border: 1px solid #e0dcda; border-radius: 1rem; background: #fff; box-shadow: 0 1rem 2.8rem rgb(26 26 24 / 6%); }
    .table-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 1.5rem; border-bottom: 1px solid #e7e2df; }
    .table-heading h2 { margin: 0; font-size: 1.35rem; }.table-heading strong { color: #68635f; font-size: .9rem; }
    .quotation-row { display: grid; grid-template-columns: 1.1fr 1.25fr .9fr 1fr 1fr auto; gap: 1rem; align-items: center; padding: 1rem 1.5rem; border-bottom: 1px solid #eeeae8; }
    .quotation-row--header { padding-block: .75rem; background: #faf9f8; color: #716b67; font-size: .72rem; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
    .quotation-row small { display: none; color: #7b7470; font-size: .7rem; font-weight: 750; text-transform: uppercase; }
    .reference strong { overflow-wrap: anywhere; }.status { display: inline-flex; width: fit-content; padding: .38rem .65rem; border-radius: 999px; font-size: .72rem; font-weight: 850; }
    .status--pending { background: #fff2cc; color: #72520a; }.status--review { background: #e8f0ff; color: #244d8e; }.status--quoted { background: #f4e8ff; color: #693190; }.status--accepted { background: #e5f5e9; color: #246137; }.status--rejected, .status--cancelled { background: #fde8e8; color: #8b2d2d; }
    .detail-link { display: inline-flex; align-items: center; gap: .35rem; color: #8f123f; font-weight: 800; text-decoration: none; white-space: nowrap; }.detail-link .material-icons { font-size: 1.1rem; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; padding: 1.25rem; }.pagination button { padding: .6rem .9rem; border: 1px solid #d5cfcc; border-radius: .55rem; background: #fff; font: inherit; font-weight: 750; }.pagination button:disabled { opacity: .45; }
    .state-card { display: grid; justify-items: center; gap: .75rem; padding: clamp(2.5rem, 8vw, 5rem) 1.5rem; text-align: center; }.state-card h2, .state-card p { margin: 0; }.state-card p { color: #68635f; }.state-card > .material-icons { color: #af0f50; font-size: 2.5rem; }
    .spinner { width: 2rem; height: 2rem; border: 3px solid #eadde2; border-top-color: #af0f50; border-radius: 50%; animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
    a:focus-visible, button:focus-visible { outline: 3px solid #d02870; outline-offset: 3px; }
    @media (max-width: 900px) { .quotation-row--header { display: none; }.quotation-table { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; padding: 1rem; }.quotation-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; padding: 1.15rem; border: 1px solid #e7e2df; border-radius: .75rem; }.quotation-row small { display: block; margin-bottom: .3rem; }.detail-link { grid-column: 1 / -1; justify-content: center; padding-top: .8rem; border-top: 1px solid #eeeae8; } }
    @media (max-width: 600px) { .quotation-page { width: min(100% - 1rem, 1180px); }.page-heading { align-items: stretch; flex-direction: column; }.primary-link { width: 100%; box-sizing: border-box; }.quotation-table { grid-template-columns: 1fr; }.quotation-row { grid-template-columns: 1fr; }.detail-link { grid-column: auto; }.table-heading { align-items: flex-start; flex-direction: column; } }
    @media (prefers-reduced-motion: reduce) { .spinner { animation: none; } }
  `]
})
export class ClientQuotationHistoryComponent implements OnInit {
  private readonly quotationApi = inject(QuotationApiService);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal<PageResponse<Quotation> | null>(null);

  ngOnInit(): void { this.load(0); }

  protected load(page = this.page()?.number ?? 0): void {
    if (this.loading() && this.page()) return;
    this.loading.set(true);
    this.error.set(null);
    this.quotationApi.getMyQuotations(page, 10).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (result) => this.page.set(result),
      error: (failure: unknown) => this.handleError(failure)
    });
  }

  protected shortReference(id: string): string { return id.slice(0, 8).toUpperCase(); }
  protected statusLabel(status: QuotationStatus): string { return getQuotationStatusLabel(status); }
  protected serviceLabel(type: string): string { return getServiceTypeLabel(type); }
  protected statusClass(status: QuotationStatus): string {
    switch (status) {
      case 'PENDING': return 'status--pending';
      case 'IN_REVIEW': return 'status--review';
      case 'QUOTED': return 'status--quoted';
      case 'ACCEPTED': return 'status--accepted';
      case 'REJECTED': return 'status--rejected';
      case 'CANCELLED': return 'status--cancelled';
      default: return 'status--pending';
    }
  }

  private handleError(failure: unknown): void {
    if (failure instanceof HttpErrorResponse && failure.status === 401) {
      this.error.set('Votre session a expiré. Reconnexion en cours…');
      void this.auth.login('/devis/mes-demandes');
      return;
    }
    if (failure instanceof HttpErrorResponse && failure.status === 403) {
      this.error.set("Vous n'avez pas accès à ces demandes de devis.");
      return;
    }
    this.error.set('Vos demandes ne peuvent pas être chargées pour le moment.');
  }
}
