import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  concatMap,
  finalize,
  forkJoin,
  from,
  map,
  of,
  reduce,
  switchMap
} from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { OrderApiService } from '../../orders/order-api.service';
import { OrderResponse, orderStatusLabel } from '../../orders/order.models';
import { QuotationApiService } from '../../quotations/quotation-api.service';
import { Quotation, getQuotationStatusLabel } from '../../quotations/quotation.models';

interface PageResult<T> {
  readonly content: readonly T[];
  readonly totalPages: number;
}

@Component({
  selector: 'wwt-client-documents',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Mes documents">
      <wwt-page-header
        title="Mes documents"
        subtitle="Retrouvez vos devis et vos documents de commande"
        breadcrumb="Espace client / Documents">
      </wwt-page-header>

      <main class="documents-page" aria-label="Centre de documents client">
        @if (downloadError()) {
          <div class="download-error" role="alert">
            <span class="material-icons" aria-hidden="true">error_outline</span>
            <span>{{ downloadError() }}</span>
            <button type="button" aria-label="Fermer le message" (click)="downloadError.set(null)">
              <span class="material-icons" aria-hidden="true">close</span>
            </button>
          </div>
        }

        <section class="document-section" aria-labelledby="quotations-title">
          <header class="section-header">
            <div class="section-heading">
              <span class="section-icon material-icons" aria-hidden="true">description</span>
              <div><span class="eyebrow">Commercial</span><h2 id="quotations-title">Devis</h2></div>
            </div>
            @if (!quotationsLoading() && !quotationsError()) {
              <span class="count">{{ quotations().length }} document{{ quotations().length > 1 ? 's' : '' }}</span>
            }
          </header>

          @if (quotationsLoading()) {
            <div class="state" role="status"><span class="material-icons spin" aria-hidden="true">sync</span>Chargement des devis…</div>
          } @else if (quotationsError()) {
            <div class="state state--error" role="alert">
              <span class="material-icons" aria-hidden="true">error_outline</span>
              <div><strong>Devis indisponibles</strong><p>{{ quotationsError() }}</p></div>
              <button type="button" class="retry" (click)="loadDocuments()">Réessayer</button>
            </div>
          } @else if (quotations().length === 0) {
            <div class="state state--empty" role="status">
              <span class="material-icons" aria-hidden="true">folder_open</span>
              <div><strong>Aucun devis</strong><p>Vos devis apparaîtront ici dès qu’ils seront disponibles.</p></div>
            </div>
          } @else {
            <div class="document-list">
              @for (quotation of quotations(); track quotation.id) {
                <article class="document-row">
                  <div class="document-mark"><span class="material-icons" aria-hidden="true">request_quote</span></div>
                  <div class="document-main">
                    <strong>DEV-{{ shortReference(quotation.id) }}</strong>
                    <span>{{ quotation.createdAt | date:'dd/MM/yyyy' }}</span>
                  </div>
                  <div class="document-value">
                    <span>Montant</span>
                    <strong>{{ quotationAmount(quotation) === null ? 'Non chiffré' : ((quotationAmount(quotation) | number:'1.2-2') + ' DH') }}</strong>
                  </div>
                  <span class="status">{{ quotationStatus(quotation) }}</span>
                  <div class="actions">
                    <a [routerLink]="['/devis/mes-demandes', quotation.id]" aria-label="Voir le devis">Voir</a>
                    <button type="button" (click)="downloadQuotation(quotation.id)" [disabled]="downloadingId() === quotation.id">
                      <span class="material-icons" aria-hidden="true">download</span>
                      {{ downloadingId() === quotation.id ? 'Téléchargement…' : 'PDF' }}
                    </button>
                  </div>
                </article>
              }
            </div>
          }
        </section>

        <section class="document-section" aria-labelledby="orders-title">
          <header class="section-header">
            <div class="section-heading">
              <span class="section-icon material-icons" aria-hidden="true">receipt_long</span>
              <div><span class="eyebrow">Commandes</span><h2 id="orders-title">Bons de commande</h2></div>
            </div>
            @if (!ordersLoading() && !ordersError()) {
              <span class="count">{{ orders().length }} document{{ orders().length > 1 ? 's' : '' }}</span>
            }
          </header>

          @if (ordersLoading()) {
            <div class="state" role="status"><span class="material-icons spin" aria-hidden="true">sync</span>Chargement des documents…</div>
          } @else if (ordersError()) {
            <div class="state state--error" role="alert">
              <span class="material-icons" aria-hidden="true">error_outline</span>
              <div><strong>Documents indisponibles</strong><p>{{ ordersError() }}</p></div>
              <button type="button" class="retry" (click)="loadDocuments()">Réessayer</button>
            </div>
          } @else if (orders().length === 0) {
            <div class="state state--empty" role="status">
              <span class="material-icons" aria-hidden="true">folder_open</span>
              <div><strong>Aucun bon de commande</strong><p>Les documents de vos commandes apparaîtront ici.</p></div>
            </div>
          } @else {
            <div class="document-list">
              @for (order of orders(); track order.id) {
                <article class="document-row">
                  <div class="document-mark"><span class="material-icons" aria-hidden="true">shopping_bag</span></div>
                  <div class="document-main">
                    <strong>CMD-{{ shortReference(order.id) }}</strong>
                    <span>{{ order.createdAt | date:'dd/MM/yyyy' }}</span>
                  </div>
                  <div class="document-value"><span>Total</span><strong>{{ order.total | number:'1.2-2' }} DH</strong></div>
                  <span class="status">{{ orderStatus(order) }}</span>
                  <div class="actions">
                    <a [routerLink]="['/client/orders', order.id]" aria-label="Voir la commande">Voir</a>
                    <button type="button" (click)="downloadOrder(order.id)" [disabled]="downloadingId() === order.id">
                      <span class="material-icons" aria-hidden="true">download</span>
                      {{ downloadingId() === order.id ? 'Téléchargement…' : 'PDF' }}
                    </button>
                  </div>
                </article>
              }
            </div>
          }
        </section>
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .documents-page { padding: 1.5rem 2rem 2rem; display: grid; gap: 1.5rem; max-width: var(--dashboard-max-width, 1680px); margin: 0 auto; }
    .document-section { overflow: hidden; border: 1px solid #e2e8f0; border-radius: 14px; background: #ffffff; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06); }
    .section-header { padding: 1.25rem 1.5rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem; border-bottom: 1px solid #e2e8f0; }
    .section-heading { display: flex; align-items: center; gap: .85rem; }
    .section-icon, .document-mark { display: grid; place-items: center; color: #af0f50; background: #fdf2f6; }
    .section-icon { width: 42px; height: 42px; border-radius: 10px; }
    .eyebrow { color: #af0f50; font-size: .68rem; font-weight: 800; letter-spacing: .09em; text-transform: uppercase; }
    h2, p { margin: 0; } h2 { margin-top: .15rem; color: #0f172a; font-size: 1.15rem; font-weight: 700; }
    .count, .status { padding: .3rem .65rem; border-radius: 999px; font-size: .75rem; white-space: nowrap; font-weight: 600; }
    .count { color: #64748b; background: #f1f5f9; border: 1px solid #e2e8f0; }
    .status { justify-self: start; color: #8f123f; background: #fdf2f6; border: 1px solid #fce7f0; }
    .document-list { display: grid; }
    .document-row { min-width: 0; padding: 1rem 1.5rem; display: grid; grid-template-columns: auto minmax(150px, 1.3fr) minmax(120px, .8fr) minmax(110px, .7fr) auto; align-items: center; gap: 1rem; border-bottom: 1px solid #f1f5f9; transition: background 0.15s ease; }
    .document-row:hover { background: #f8fafc; }
    .document-row:last-child { border-bottom: 0; }
    .document-mark { width: 40px; height: 40px; border-radius: 10px; }
    .document-main, .document-value { min-width: 0; display: grid; gap: .2rem; }
    .document-main strong { overflow-wrap: anywhere; color: #0f172a; font-size: 0.9rem; }
    .document-main span, .document-value span { color: #64748b; font-size: .76rem; }
    .document-value strong { color: #0f172a; font-size: .95rem; font-weight: 750; }
    .actions { display: flex; align-items: center; justify-content: flex-end; gap: .5rem; }
    .actions a, .actions button, .retry { min-height: 34px; padding: .4rem .75rem; border: 1px solid #e2e8f0; border-radius: 7px; background: #ffffff; color: #334155; font: inherit; font-size: .78rem; font-weight: 650; text-decoration: none; cursor: pointer; transition: all 0.15s ease; }
    .actions button { display: inline-flex; align-items: center; gap: .35rem; border-color: #fbcfe8; background: #fdf2f6; color: #af0f50; }
    .actions .material-icons { font-size: 1rem; }
    .actions a:hover, .actions a:focus-visible { border-color: #af0f50; color: #af0f50; background: #fdf2f6; }
    .actions button:hover:not(:disabled), .actions button:focus-visible { background: #af0f50; border-color: #af0f50; color: #ffffff; }
    .actions button:disabled { opacity: .55; cursor: wait; }
    .state { min-height: 170px; padding: 2rem; display: flex; align-items: center; justify-content: center; gap: .8rem; color: #64748b; text-align: center; }
    .state > .material-icons { color: #af0f50; font-size: 2rem; }
    .state--error { flex-wrap: wrap; }.state--error > .material-icons { color: #ef4444; }.state strong { display: block; margin-bottom: .25rem; color: #0f172a; }
    .retry { margin-left: .5rem; background: #0f172a; border-color: #0f172a; color: #fff; }
    .retry:hover { background: #1e293b; }
    .download-error { padding: .85rem 1rem; display: flex; align-items: center; gap: .7rem; border: 1px solid #fecaca; border-radius: 10px; background: #fef2f2; color: #991b1b; font-size: 0.85rem; }
    .download-error button { margin-left: auto; border: 0; background: transparent; color: inherit; cursor: pointer; }
    .download-error button .material-icons { font-size: 1.1rem; }
    .spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
    @media (max-width: 900px) {
      .document-row { grid-template-columns: auto 1fr auto; }
      .document-value { grid-column: 2; }.status { grid-column: 2; }.actions { grid-column: 3; grid-row: 1 / span 3; flex-direction: column; align-items: stretch; }
    }
    @media (max-width: 600px) {
      .documents-page { padding: 1rem; }.section-header { padding: 1rem; align-items: flex-start; }.count { display: none; }
      .document-row { padding: 1rem; grid-template-columns: 38px minmax(0, 1fr); align-items: start; }
      .document-value, .status, .actions { grid-column: 2; }.actions { grid-row: auto; flex-direction: row; justify-content: flex-start; }
      .state { min-height: 150px; padding: 1.5rem 1rem; flex-direction: column; }.retry { margin-left: 0; }
    }
  `]
})
export class ClientDocumentsComponent implements OnInit {
  private readonly orderApi = inject(OrderApiService);
  private readonly quotationApi = inject(QuotationApiService);

  protected readonly orders = signal<readonly OrderResponse[]>([]);
  protected readonly quotations = signal<readonly Quotation[]>([]);
  protected readonly ordersLoading = signal(true);
  protected readonly quotationsLoading = signal(true);
  protected readonly ordersError = signal<string | null>(null);
  protected readonly quotationsError = signal<string | null>(null);
  protected readonly downloadingId = signal<string | null>(null);
  protected readonly downloadError = signal<string | null>(null);

  ngOnInit(): void { this.loadDocuments(); }

  protected loadDocuments(): void {
    this.ordersLoading.set(true);
    this.quotationsLoading.set(true);
    this.ordersError.set(null);
    this.quotationsError.set(null);

    forkJoin({
      orders: this.loadAllPages((page, size) => this.orderApi.getMyOrders(page, size)).pipe(
        map((data) => ({ data, error: null as string | null })),
        catchError(() => of({ data: [] as readonly OrderResponse[], error: 'Vos documents de commande ne peuvent pas être chargés.' }))
      ),
      quotations: this.loadAllPages((page, size) => this.quotationApi.getMyQuotations(page, size)).pipe(
        map((data) => ({ data, error: null as string | null })),
        catchError(() => of({ data: [] as readonly Quotation[], error: 'Vos devis ne peuvent pas être chargés.' }))
      )
    }).subscribe(({ orders, quotations }) => {
      this.orders.set(orders.data);
      this.quotations.set(quotations.data);
      this.ordersError.set(orders.error);
      this.quotationsError.set(quotations.error);
      this.ordersLoading.set(false);
      this.quotationsLoading.set(false);
    });
  }

  protected shortReference(id: string): string { return id.slice(0, 8).toUpperCase(); }
  protected quotationAmount(quotation: Quotation): number | null { return quotation.finalTotal ?? quotation.proposedAmount; }
  protected quotationStatus(quotation: Quotation): string { return getQuotationStatusLabel(quotation.status); }
  protected orderStatus(order: OrderResponse): string { return orderStatusLabel(order.status, order.deliveryMethod); }

  protected downloadQuotation(id: string): void {
    this.download(id, this.quotationApi.getQuotationPdf(id), `devis-${id}.pdf`);
  }

  protected downloadOrder(id: string): void {
    this.download(id, this.orderApi.getOrderPdf(id), `bon-de-commande-${id}.pdf`);
  }

  private download(id: string, request: Observable<Blob>, fileName: string): void {
    if (this.downloadingId()) return;
    this.downloadingId.set(id);
    this.downloadError.set(null);
    request.pipe(finalize(() => this.downloadingId.set(null))).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        globalThis.setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: (failure: unknown) => {
        this.downloadError.set(failure instanceof HttpErrorResponse && failure.status === 403
          ? 'Vous n’avez pas accès à ce document.'
          : failure instanceof HttpErrorResponse && failure.status === 404
            ? 'Ce document est introuvable.'
            : 'Le document ne peut pas être téléchargé pour le moment.');
      }
    });
  }

  private loadAllPages<T>(loadPage: (page: number, size: number) => Observable<PageResult<T>>): Observable<readonly T[]> {
    const pageSize = 50;
    return loadPage(0, pageSize).pipe(
      switchMap((firstPage) => {
        if (firstPage.totalPages <= 1) return of(firstPage.content);
        return from(Array.from({ length: firstPage.totalPages - 1 }, (_, index) => index + 1)).pipe(
          concatMap((page) => loadPage(page, pageSize)),
          map((page) => page.content),
          reduce((items, content) => [...items, ...content], [...firstPage.content])
        );
      })
    );
  }
}
