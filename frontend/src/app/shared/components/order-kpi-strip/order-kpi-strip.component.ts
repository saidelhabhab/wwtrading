import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, concatMap, from, map, of, reduce, switchMap } from 'rxjs';

import { AuthService, WwtradingRole } from '../../../core/auth/auth.service';
import { OrderApiService } from '../../../features/orders/order-api.service';
import { DeliveryMethod, OrderResponse, PageResponse, PaymentStatus, orderStatusLabel } from '../../../features/orders/order.models';

@Component({
  selector: 'wwt-order-kpi-strip',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="order-summary" aria-labelledby="order-summary-title">
      <div class="order-summary__heading">
        <div>
          <span class="order-summary__eyebrow">Commandes</span>
          <h2 id="order-summary-title">{{ role() === 'CLIENT' ? 'Résumé de mes commandes' : 'Résumé des commandes' }}</h2>
          <p>{{ role() === 'CLIENT' ? 'Données limitées à vos commandes.' : 'Vue consolidée des commandes clients.' }}</p>
        </div>
        <a [routerLink]="ordersPath()">Voir toutes les commandes</a>
      </div>

      @if (loading()) {
        <div class="order-summary__state" role="status">
          <span class="order-summary__loader" aria-hidden="true"></span>
          Chargement des commandes…
        </div>
      } @else if (error()) {
        <div class="order-summary__state order-summary__state--error" role="alert">
          <span>{{ error() }}</span>
          <button type="button" (click)="load()">Réessayer</button>
        </div>
      } @else {
        <div class="order-kpis" aria-label="Indicateurs des commandes">
          <article class="order-kpi order-kpi--total"><span>Total</span><strong>{{ total() }}</strong></article>
          <article class="order-kpi"><span>En attente</span><strong>{{ countStatus('DRAFT') }}</strong></article>
          <article class="order-kpi"><span>Confirmées</span><strong>{{ countStatus('CONFIRMED') }}</strong></article>
          <article class="order-kpi"><span>En traitement</span><strong>{{ countStatus('PROCESSING') }}</strong></article>
          <article class="order-kpi"><span>Prêtes</span><strong>{{ countStatus('READY') }}</strong></article>
          <article class="order-kpi order-kpi--completed"><span>Terminées</span><strong>{{ countStatus('COMPLETED') }}</strong></article>
          <article class="order-kpi order-kpi--cancelled"><span>Annulées</span><strong>{{ countStatus('CANCELLED') }}</strong></article>
          <article class="order-kpi order-kpi--paid"><span>Payées</span><strong>{{ countPayment('PAID') }}</strong></article>
          <article class="order-kpi order-kpi--unpaid"><span>Non payées</span><strong>{{ countPayment('UNPAID') }}</strong></article>
        </div>

        <div class="recent-orders">
          <div class="recent-orders__heading">
            <div><span>Activité récente</span><h3>Commandes récentes</h3></div>
            <small>Les {{ recentOrders().length }} dernières commandes</small>
          </div>

          @if (recentOrders().length === 0) {
            <p class="recent-orders__empty">Aucune commande disponible.</p>
          } @else {
            <div class="recent-orders__table" role="table" aria-label="Commandes récentes">
              <div class="recent-orders__row recent-orders__row--header" role="row">
                <span role="columnheader">Commande</span>
                <span role="columnheader">Date</span>
                <span role="columnheader">Statut</span>
                <span role="columnheader">Paiement</span>
                <span role="columnheader">Total</span>
              </div>
              @for (order of recentOrders(); track order.id) {
                <a class="recent-orders__row" role="row" [routerLink]="[ordersPath(), order.id]" [attr.aria-label]="'Consulter la commande ' + (order.orderNumber || order.id)">
                  <div class="recent-orders__id-col" role="cell">
                    <strong class="recent-orders__id">{{ order.orderNumber || order.id }}</strong>
                    @if (order.orderNumber) {
                      <small class="recent-orders__subid">{{ order.id }}</small>
                    }
                  </div>
                  <span role="cell" class="recent-orders__date">{{ order.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
                  <span role="cell" [class]="statusClass(order.status)">{{ statusLabel(order.status, order.deliveryMethod) }}</span>
                  <span role="cell" [class]="paymentClass(order.paymentStatus)">{{ paymentLabel(order.paymentStatus) }}</span>
                  <strong class="recent-orders__total" role="cell">{{ order.total | number:'1.2-2' }} DH</strong>
                </a>
              }
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    :host { display: block; margin: 1.75rem 0; }
    .order-summary {
      min-width: 0;
      padding: clamp(1rem, 3vw, 1.5rem);
      border: 1px solid var(--dashboard-border);
      border-radius: var(--dashboard-radius);
      background: var(--dashboard-surface);
      box-shadow: var(--dashboard-shadow-sm);
    }

    .order-summary__heading {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      justify-content: space-between;
      gap: .85rem;
      margin-bottom: 1.25rem;
    }

    .order-summary__heading h2, .recent-orders__heading h3 { margin: .2rem 0 0; color: var(--dashboard-text); }
    .order-summary__heading h2 { font-size: clamp(1.2rem, 1rem + .8vw, 1.75rem); }
    .order-summary__heading p { margin: .35rem 0 0; color: var(--dashboard-text-muted); line-height: 1.55; }
    .order-summary__eyebrow, .recent-orders__heading span { color: var(--dashboard-primary); font-size: .72rem; font-weight: 850; letter-spacing: .1em; text-transform: uppercase; }

    /* Full-width action on phones so it is not clipped next to the title. */
    .order-summary__heading a, .order-summary__state button {
      display: inline-flex;
      min-height: 2.75rem;
      align-items: center;
      justify-content: center;
      padding: .65rem .9rem;
      border: 1px solid var(--dashboard-primary);
      border-radius: 999px;
      background: #fff;
      color: var(--dashboard-primary-dark);
      font: inherit;
      font-weight: 750;
      text-decoration: none;
      text-align: center;
    }

    .order-summary__heading a:hover, .order-summary__state button:hover { background: var(--dashboard-primary); color: #fff; }
    .order-summary__heading a:focus-visible, .order-summary__state button:focus-visible, .recent-orders__row:focus-visible { outline: 3px solid #d02870; outline-offset: 3px; }
    .order-summary__state { display: flex; align-items: center; gap: .7rem; min-height: 6rem; color: var(--dashboard-text-muted); }
    .order-summary__state--error { justify-content: space-between; flex-wrap: wrap; color: var(--dashboard-primary-dark); }
    .order-summary__loader { flex: 0 0 auto; width: 1.25rem; height: 1.25rem; border: 2px solid #ded8d5; border-top-color: var(--dashboard-primary); border-radius: 50%; animation: spin .8s linear infinite; }

    /*
     * KPI tiles: responsive grid across all screen sizes
     */
    .order-kpis { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
    .order-kpi {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: .4rem;
      min-width: 0;
      padding: .85rem 1rem;
      border: 1px solid var(--dashboard-border, #ded8d5);
      border-top: 3px solid #817a76;
      border-radius: 12px;
      background: var(--dashboard-surface, #fff);
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      transition: all .16s ease;
    }
    .order-kpi:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06); }
    .order-kpi span { color: var(--dashboard-text-muted); font-size: .72rem; font-weight: 750; line-height: 1.3; text-transform: uppercase; letter-spacing: .04em; }
    .order-kpi strong { color: var(--dashboard-text); font-size: clamp(1.25rem, 1rem + .8vw, 1.75rem); font-weight: 850; line-height: 1; }
    .order-kpi--total { border-top-color: var(--dashboard-primary, #af0f50); background: #fffbfe; }
    .order-kpi--total strong { color: var(--dashboard-primary, #af0f50); }
    .order-kpi--completed { border-top-color: #059669; }
    .order-kpi--cancelled { border-top-color: #dc2626; }
    .order-kpi--paid { border-top-color: #059669; }
    .order-kpi--unpaid { border-top-color: #d97706; }

    .recent-orders { margin-top: 1.5rem; padding-top: 1.25rem; border-top: 1px solid var(--dashboard-border); }
    .recent-orders__heading { display: flex; flex-direction: column; align-items: flex-start; gap: .35rem; margin-bottom: .8rem; }
    .recent-orders__heading h3 { font-size: 1.15rem; font-weight: 800; margin: 0; }
    .recent-orders__heading small, .recent-orders__empty { color: var(--dashboard-text-muted); }
    .recent-orders__table { display: grid; gap: .7rem; }

    /* Mobile: each order becomes a card; nothing scrolls horizontally. */
    .recent-orders__row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: .5rem .75rem;
      min-height: 3.5rem;
      padding: .9rem;
      border: 1px solid var(--dashboard-border);
      border-radius: 12px;
      color: var(--dashboard-text);
      text-decoration: none;
      background: #fff;
      transition: all .16s ease;
    }

    .recent-orders__row--header { display: none; }
    a.recent-orders__row:hover { background: #fbf4f7; border-color: var(--dashboard-primary); }
    .recent-orders__id-col { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
    .recent-orders__id { font-weight: 800; color: var(--dashboard-primary-dark, #8f123f); overflow-wrap: anywhere; }
    .recent-orders__subid { font-size: 0.72rem; color: #8c8985; }
    .recent-orders__total { grid-column: 2; grid-row: 2; text-align: right; white-space: nowrap; font-size: 0.95rem; font-weight: 800; }
    .recent-orders__row > span[role='cell'] { min-width: 0; font-size: .84rem; }

    .badge { justify-self: start; padding: .3rem .6rem; border-radius: 999px; background: #ece8e6; color: #57514e; font-size: .72rem; font-weight: 800; white-space: nowrap; }
    .badge--draft { background: #f3f4f6; color: #4b5563; }
    .badge--confirmed { background: #eff6ff; color: #1d4ed8; }
    .badge--processing, .badge--unpaid { background: #fef3c7; color: #b45309; }
    .badge--ready { background: #f3e8ff; color: #7e22ce; }
    .badge--completed { background: #ecfdf5; color: #047857; }
    .badge--cancelled { background: #fef2f2; color: #b91c1c; }
    .badge--paid { background: #ecfdf5; color: #047857; }

    @keyframes spin { to { transform: rotate(360deg); } }

    /* From a large phone upward the heading becomes a row. */
    @media (min-width: 481px) {
      .order-summary__heading { flex-direction: row; align-items: flex-end; }
      .order-summary__heading a { flex: 0 0 auto; }
      .recent-orders__heading { flex-direction: row; align-items: flex-end; justify-content: space-between; }
      .order-kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    }

    @media (min-width: 768px) {
      .order-kpis { grid-template-columns: repeat(5, minmax(0, 1fr)); }
      .order-kpi--total { grid-column: auto; }

      /* Tablet and up: a real table again. */
      .recent-orders__table { display: block; overflow-x: auto; border: 1px solid var(--dashboard-border); border-radius: 12px; }
      .recent-orders__row {
        grid-template-columns: minmax(10rem, 1.6fr) minmax(7.5rem, .9fr) minmax(6.5rem, .75fr) minmax(6.5rem, .75fr) minmax(6.5rem, .75fr);
        gap: .8rem;
        padding: .85rem 1.1rem;
        border: 0;
        border-bottom: 1px solid var(--dashboard-border);
        border-radius: 0;
      }
      .recent-orders__row:last-child { border-bottom: 0; }
      .recent-orders__row--header {
        display: grid;
        min-height: 2.6rem;
        background: var(--dashboard-surface-muted, #f7f5f4);
        color: var(--dashboard-text-muted);
        font-size: .72rem;
        font-weight: 800;
        letter-spacing: .06em;
        text-transform: uppercase;
      }
      .recent-orders__id-col { grid-column: auto; }
      .recent-orders__total { grid-column: auto; grid-row: auto; }
    }

    @media (min-width: 1200px) {
      .order-kpis { grid-template-columns: repeat(9, minmax(0, 1fr)); }
    }

    @media (prefers-reduced-motion: reduce) { .order-summary__loader { animation: none; } }
  `]
})
export class OrderKpiStripComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly orderApi = inject(OrderApiService);
  protected readonly role = computed<WwtradingRole | null>(() => this.auth.roles()[0] ?? null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private readonly orders = signal<readonly OrderResponse[]>([]);

  protected readonly total = computed(() => this.orders().length);
  protected readonly recentOrders = computed(() => [...this.orders()]
    .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt))
    .slice(0, 5));
  protected readonly ordersPath = computed(() => {
    switch (this.role()) {
      case 'ADMIN': return '/admin/orders';
      case 'MANAGER': return '/manager/orders';
      case 'COMMERCIAL': return '/commercial/orders';
      default: return '/client/orders';
    }
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    if (this.role() === 'TECHNICIAN') {
      this.error.set("Vous n'avez pas accès aux commandes.");
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.loadAllOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Les informations de commandes ne peuvent pas être chargées.');
        this.loading.set(false);
      }
    });
  }

  protected countStatus(status: OrderResponse['status']): number {
    return this.orders().filter((order) => order.status === status).length;
  }

  protected countPayment(status: PaymentStatus): number {
    return this.orders().filter((order) => order.paymentStatus === status).length;
  }

  protected statusLabel(status: OrderResponse['status'], deliveryMethod: DeliveryMethod): string {
    return orderStatusLabel(status, deliveryMethod);
  }

  protected paymentLabel(status: PaymentStatus): string {
    return status === 'PAID' ? 'Payée' : 'Non payée';
  }

  protected statusClass(status: OrderResponse['status']): string {
    return `badge badge--${status.toLowerCase()}`;
  }

  protected paymentClass(status: PaymentStatus): string {
    return `badge badge--${status.toLowerCase()}`;
  }

  private loadAllOrders(): Observable<readonly OrderResponse[]> {
    const pageSize = this.role() === 'CLIENT' ? 20 : 100;
    return this.loadPage(0, pageSize).pipe(
      switchMap((firstPage) => {
        if (firstPage.totalPages <= 1) return of(firstPage.content);
        const remainingPages = Array.from({ length: firstPage.totalPages - 1 }, (_, index) => index + 1);
        return from(remainingPages).pipe(
          concatMap((page) => this.loadPage(page, pageSize)),
          map((page) => page.content),
          reduce((orders, content) => [...orders, ...content], [...firstPage.content])
        );
      })
    );
  }

  private loadPage(page: number, size: number): Observable<PageResponse<OrderResponse>> {
    return this.role() === 'CLIENT'
      ? this.orderApi.getMyOrders(page, size)
      : this.orderApi.getAllOrders(page, size);
  }
}
