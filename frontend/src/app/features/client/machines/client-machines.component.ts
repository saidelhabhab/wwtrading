import { DatePipe } from '@angular/common';
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
import { OrderResponse } from '../../orders/order.models';
import { ProductApiService } from '../../products/product-api.service';

interface PurchasedEquipment {
  readonly reference: string;
  readonly name: string;
  readonly brand: string | null;
  readonly model: string | null;
  readonly quantity: number;
  readonly orderCount: number;
  readonly lastOrderId: string;
  readonly lastOrderedAt: string;
}

interface AggregatedOrderProduct {
  readonly reference: string;
  quantity: number;
  readonly orderIds: Set<string>;
  lastOrderId: string;
  lastOrderedAt: string;
}

@Component({
  selector: 'wwt-client-machines',
  standalone: true,
  imports: [DatePipe, RouterLink, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Mes machines">
      <wwt-page-header
        title="Mes machines"
        subtitle="Équipements associés à vos achats"
        breadcrumb="Espace client / Mes machines">
      </wwt-page-header>

      <main class="machines-page" aria-label="Mes machines et équipements">
        <section class="machines-panel" aria-labelledby="machines-title">
          <header class="machines-panel__header">
            <div>
              <span class="eyebrow">Mes achats</span>
              <h2 id="machines-title">Équipements associés à vos commandes</h2>
            </div>
            @if (!loading() && !error() && equipment().length > 0) {
              <span class="result-count">{{ equipment().length }} référence{{ equipment().length > 1 ? 's' : '' }}</span>
            }
          </header>

          @if (loading()) {
            <div class="state-card" role="status" aria-live="polite">
              <span class="material-icons spin" aria-hidden="true">sync</span>
              <p>Chargement de vos équipements…</p>
            </div>
          } @else if (error()) {
            <div class="state-card state-card--error" role="alert">
              <span class="material-icons" aria-hidden="true">error_outline</span>
              <div>
                <h3>Chargement impossible</h3>
                <p>{{ error() }}</p>
                <button type="button" class="retry-button" (click)="loadEquipment()">Réessayer</button>
              </div>
            </div>
          } @else if (equipment().length === 0) {
            <div class="empty-state" role="status">
              <span class="empty-state__icon material-icons" aria-hidden="true">print</span>
              <div>
                <h3>Aucun équipement associé à vos achats</h3>
                <p>Les produits de vos commandes apparaîtront ici.</p>
              </div>
            </div>
          } @else {
            <div class="machine-grid">
              @for (machine of equipment(); track machine.reference) {
                <article class="machine-card">
                  <div class="machine-card__icon" aria-hidden="true">
                    <span class="material-icons">print</span>
                  </div>
                  <div class="machine-card__content">
                    <span class="reference">{{ machine.reference }}</span>
                    <h3>{{ machine.name }}</h3>
                    @if (machine.brand || machine.model) {
                      <p class="identity">
                        @if (machine.brand) { <span>{{ machine.brand }}</span> }
                        @if (machine.model) { <span>{{ machine.model }}</span> }
                      </p>
                    }
                    <dl>
                      <div><dt>Quantité achetée</dt><dd>{{ machine.quantity }}</dd></div>
                      <div><dt>Commande{{ machine.orderCount > 1 ? 's' : '' }}</dt><dd>{{ machine.orderCount }}</dd></div>
                    </dl>
                  </div>
                  <footer class="machine-card__footer">
                    <span>Dernier achat le {{ machine.lastOrderedAt | date:'dd/MM/yyyy' }}</span>
                    <a [routerLink]="['/client/orders', machine.lastOrderId]">
                      Voir la commande
                      <span class="material-icons" aria-hidden="true">arrow_forward</span>
                    </a>
                  </footer>
                </article>
              }
            </div>
          }
        </section>
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .machines-page { padding: 1.5rem 2rem 2rem; max-width: var(--dashboard-max-width, 1680px); margin: 0 auto; }
    .machines-panel { overflow: hidden; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06); }
    .machines-panel__header { padding: 1.5rem; display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; border-bottom: 1px solid #e2e8f0; }
    .eyebrow, .reference { display: block; color: #af0f50; font-size: .72rem; font-weight: 750; letter-spacing: .08em; text-transform: uppercase; }
    .eyebrow { margin-bottom: .35rem; }
    h2, h3, p, dl, dd { margin: 0; }
    h2 { color: #0f172a; font-size: clamp(1.15rem, 2vw, 1.4rem); font-weight: 700; }
    .result-count { padding: .35rem .75rem; border-radius: 999px; background: #f1f5f9; border: 1px solid #e2e8f0; color: #475569; font-size: .78rem; font-weight: 600; white-space: nowrap; }
    .state-card, .empty-state { min-height: 280px; padding: 3rem 1.5rem; display: grid; place-items: center; align-content: center; gap: 1rem; text-align: center; color: #64748b; }
    .state-card > .material-icons { color: #af0f50; font-size: 2.2rem; }
    .state-card--error > .material-icons { color: #ef4444; }
    .state-card h3, .empty-state h3 { margin-bottom: .45rem; color: #0f172a; font-size: 1.1rem; font-weight: 700; }
    .empty-state__icon { width: 64px; height: 64px; display: grid; place-items: center; border: 1px solid #fbcfe8; border-radius: 16px; background: #fdf2f6; color: #af0f50; font-size: 2rem; }
    .retry-button { margin-top: 1rem; padding: .65rem 1.25rem; border: 1px solid #af0f50; border-radius: 8px; background: #af0f50; color: #fff; font: inherit; font-weight: 700; cursor: pointer; transition: all 0.18s ease; }
    .retry-button:hover { background: #930b42; transform: translateY(-1px); }
    .machine-grid { padding: 1.5rem; display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 1.25rem; }
    .machine-card { min-width: 0; display: grid; grid-template-columns: auto 1fr; align-content: start; gap: 1rem; padding: 1.35rem; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04); transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease; }
    .machine-card:hover { transform: translateY(-2px); border-color: #cbd5e1; box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.08); }
    .machine-card__icon { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 10px; background: #fdf2f6; color: #af0f50; }
    .machine-card__content { min-width: 0; }
    .machine-card h3 { margin-top: .35rem; overflow-wrap: anywhere; color: #0f172a; font-size: 1.05rem; font-weight: 700; line-height: 1.35; }
    .identity { margin-top: .35rem; display: flex; flex-wrap: wrap; gap: .35rem .65rem; color: #64748b; font-size: .84rem; }
    dl { margin-top: 1.1rem; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
    dl div { padding: .75rem; border-radius: 8px; background: #f8fafc; border: 1px solid #e2e8f0; }
    dt { color: #64748b; font-size: .72rem; font-weight: 600; text-transform: uppercase; }
    dd { margin-top: .2rem; color: #0f172a; font-size: 1rem; font-weight: 750; }
    .machine-card__footer { grid-column: 1 / -1; padding-top: 1rem; display: flex; align-items: center; justify-content: space-between; gap: .75rem; border-top: 1px solid #f1f5f9; color: #64748b; font-size: .78rem; }
    .machine-card__footer a { display: inline-flex; align-items: center; gap: .3rem; color: #af0f50; font-weight: 700; text-decoration: none; white-space: nowrap; }
    .machine-card__footer a:hover, .machine-card__footer a:focus-visible { color: #8f123f; text-decoration: underline; }
    .machine-card__footer .material-icons { font-size: 1rem; }
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    @media (max-width: 768px) {
      .machines-page { padding: 1rem; }
      .machines-panel__header, .machine-grid { padding: 1.25rem; }
      .state-card, .empty-state { min-height: 240px; padding: 2.25rem 1rem; }
    }
    @media (max-width: 480px) {
      .machines-panel__header, .machine-card__footer { align-items: flex-start; flex-direction: column; }
      .machine-card { grid-template-columns: 40px 1fr; padding: 1rem; }
      .machine-card__icon { width: 40px; height: 40px; }
      dl { grid-template-columns: 1fr; }
    }
  `]
})
export class ClientMachinesComponent implements OnInit {
  private readonly orderApi = inject(OrderApiService);
  private readonly productApi = inject(ProductApiService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly equipment = signal<readonly PurchasedEquipment[]>([]);

  ngOnInit(): void { this.loadEquipment(); }

  protected loadEquipment(): void {
    this.loading.set(true);
    this.error.set(null);
    this.loadAllOrders().pipe(
      map((orders) => this.aggregateOrderProducts(orders.filter((order) => order.status !== 'CANCELLED'))),
      switchMap((products) => this.enrichProducts(products)),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (equipment) => this.equipment.set(equipment),
      error: () => {
        this.equipment.set([]);
        this.error.set('Impossible de charger les équipements associés à vos commandes.');
      }
    });
  }

  private loadAllOrders(): Observable<readonly OrderResponse[]> {
    const pageSize = 50;
    return this.orderApi.getMyOrders(0, pageSize).pipe(
      switchMap((firstPage) => {
        if (firstPage.totalPages <= 1) return of(firstPage.content);
        return from(Array.from({ length: firstPage.totalPages - 1 }, (_, index) => index + 1)).pipe(
          concatMap((page) => this.orderApi.getMyOrders(page, pageSize)),
          map((page) => page.content),
          reduce((orders, content) => [...orders, ...content], [...firstPage.content])
        );
      })
    );
  }

  private aggregateOrderProducts(orders: readonly OrderResponse[]): readonly AggregatedOrderProduct[] {
    const products = new Map<string, AggregatedOrderProduct>();
    for (const order of orders) {
      for (const item of order.items) {
        const existing = products.get(item.productReference);
        if (!existing) {
          products.set(item.productReference, {
            reference: item.productReference,
            quantity: item.quantity,
            orderIds: new Set([order.id]),
            lastOrderId: order.id,
            lastOrderedAt: order.createdAt
          });
          continue;
        }
        existing.quantity += item.quantity;
        existing.orderIds.add(order.id);
        if (Date.parse(order.createdAt) > Date.parse(existing.lastOrderedAt)) {
          existing.lastOrderId = order.id;
          existing.lastOrderedAt = order.createdAt;
        }
      }
    }
    return [...products.values()];
  }

  private enrichProducts(products: readonly AggregatedOrderProduct[]): Observable<readonly PurchasedEquipment[]> {
    if (products.length === 0) return of([]);
    return forkJoin(products.map((entry) => this.productApi.getByReference(entry.reference).pipe(
      map((product): PurchasedEquipment => ({
        reference: entry.reference,
        name: product.name,
        brand: product.brand,
        model: product.model,
        quantity: entry.quantity,
        orderCount: entry.orderIds.size,
        lastOrderId: entry.lastOrderId,
        lastOrderedAt: entry.lastOrderedAt
      })),
      catchError(() => of<PurchasedEquipment>({
        reference: entry.reference,
        name: entry.reference,
        brand: null,
        model: null,
        quantity: entry.quantity,
        orderCount: entry.orderIds.size,
        lastOrderId: entry.lastOrderId,
        lastOrderedAt: entry.lastOrderedAt
      }))
    )));
  }
}
