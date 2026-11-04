import { SlicePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, Input, signal } from '@angular/core';

import { OrderAnalyticsResponse } from '../../../features/orders/order-analytics.models';

/** A revenue evolution point normalised for chart rendering. */
interface ChartPoint {
  readonly date: string;
  readonly paidRevenue: number;
  readonly recognizedRevenue: number;
  readonly orders: number;
  /** Height percentages, 0-100, relative to the largest value in the series. */
  readonly paidHeight: number;
  readonly recognizedHeight: number;
}

/**
 * Financial overview shared by the ADMIN and MANAGER workspaces.
 *
 * The three figures are deliberately labelled apart and never merged:
 * order value (what was ordered), paid revenue (what was explicitly paid),
 * and recognized revenue (paid AND completed). The chart draws two separate
 * series so the distinction stays visible.
 */
@Component({
  selector: 'wwt-financial-overview',
  standalone: true,
  imports: [SlicePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="financial" aria-labelledby="financial-title">
      <header class="financial__header">
        <div>
          <span class="eyebrow">Pilotage financier</span>
          <h2 id="financial-title">Financial Overview</h2>
          <p>
            Trois mesures distinctes : la valeur commandée, les paiements confirmés
            et les paiements confirmés sur commandes terminées.
          </p>
        </div>
        @if (rangeLabel) { <span class="range">{{ rangeLabel }}</span> }
      </header>

      @if (loading) {
        <div class="cards" aria-busy="true">
          @for (placeholder of [1, 2, 3, 4]; track placeholder) {
            <div class="card card--loading"><span></span></div>
          }
        </div>
      } @else {
        <div class="cards">
          <article class="card card--value">
            <span class="card__label">Order Value</span>
            <strong class="card__amount">{{ format(data?.totalValue) }}</strong>
            <small>Commandes non annulées — ce qui a été commandé, pas un revenu.</small>
          </article>

          <article class="card card--paid">
            <span class="card__label">Paid Revenue</span>
            <strong class="card__amount">{{ format(data?.paidRevenue) }}</strong>
            <small>Paiements explicitement confirmés.</small>
          </article>

          <article class="card card--recognized">
            <span class="card__label">Recognized Revenue</span>
            <strong class="card__amount">{{ format(data?.recognizedRevenue) }}</strong>
            <small>Payées et terminées (statut COMPLETED).</small>
          </article>

          <article class="card">
            <span class="card__label">Average Order Value</span>
            <strong class="card__amount">{{ format(data?.averageOrderValue) }}</strong>
            <small>
              Order Value ÷ {{ data?.orderValueOrders ?? 0 }} commande(s) non annulée(s).
            </small>
          </article>
        </div>

        <section class="evolution" aria-labelledby="evolution-title">
          <header>
            <div>
              <span class="eyebrow">Évolution</span>
              <h3 id="evolution-title">Revenue Evolution</h3>
            </div>
            <ul class="legend">
              <li><span class="swatch swatch--paid"></span> Paid Revenue</li>
              <li><span class="swatch swatch--recognized"></span> Recognized Revenue</li>
            </ul>
          </header>

          @if (points().length === 0) {
            <p class="empty">No confirmed revenue for this period.</p>
          } @else {
            <div class="chart" role="img" [attr.aria-label]="chartLabel()">
              @for (point of points(); track point.date) {
                <div
                  class="column"
                  tabindex="0"
                  (mouseenter)="hovered.set(point)"
                  (focus)="hovered.set(point)"
                  (mouseleave)="hovered.set(null)"
                  (blur)="hovered.set(null)"
                >
                  <div class="bars">
                    <span class="bar bar--paid" [style.height.%]="point.paidHeight"></span>
                    <span class="bar bar--recognized" [style.height.%]="point.recognizedHeight"></span>
                  </div>
                  <small>{{ point.date | slice: 5 }}</small>

                  @if (hovered() === point) {
                    <div class="tooltip" role="status">
                      <strong>{{ point.date }}</strong>
                      <span>Paid Revenue: {{ format(point.paidRevenue) }}</span>
                      <span>Recognized Revenue: {{ format(point.recognizedRevenue) }}</span>
                      <span>Orders: {{ point.orders }}</span>
                    </div>
                  }
                </div>
              }
            </div>
          }
        </section>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .financial {
      display: grid;
      gap: clamp(1rem, 2.5vw, 1.5rem);
      min-width: 0;
      padding: clamp(1rem, 3vw, 2rem);
      border: 1px solid rgb(26 26 24 / 12%);
      border-top: 4px solid #af0f50;
      border-radius: .5rem;
      background: #fff;
      box-shadow: 0 .75rem 2rem rgb(26 26 24 / 5%);
    }

    .financial__header {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: .85rem;
    }

    .eyebrow { display: block; margin-bottom: .4rem; color: #af0f50; font-size: .72rem; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }
    h2 { margin: 0; color: #1a1a18; font-size: clamp(1.2rem, 1rem + .9vw, 1.45rem); font-weight: 850; line-height: 1.2; }
    h3 { margin: 0; color: #1a1a18; font-size: clamp(1.05rem, .95rem + .5vw, 1.2rem); font-weight: 850; }
    .financial__header p { max-width: 52ch; margin: .5rem 0 0; color: #6b6764; font-size: .88rem; line-height: 1.6; }
    .range { flex: 0 0 auto; padding: .4rem .8rem; border-radius: 999px; background: #f4f2f0; color: #4f4c4d; font-size: .75rem; font-weight: 750; }

    /*
     * One card per row on phones so Order Value / Paid Revenue / Recognized
     * Revenue / Average Order Value each keep a readable amount on screen.
     */
    .cards { display: grid; grid-template-columns: 1fr; gap: clamp(.75rem, 2vw, 1rem); }
    .card { position: relative; display: grid; align-content: start; gap: .45rem; min-width: 0; padding: clamp(1rem, 2.5vw, 1.25rem); border: 1px solid rgb(26 26 24 / 10%); border-radius: .5rem; background: #faf9f8; }
    .card--value { border-left: 3px solid #575653; background: #f7f6f5; }
    .card--paid { border-left: 3px solid #af0f50; background: #fdf3f7; }
    .card--recognized { border-left: 3px solid #1a1a18; background: #f5f4f3; }
    .card__label { color: #6b6764; font-size: .72rem; font-weight: 850; letter-spacing: .1em; text-transform: uppercase; }
    /* Fluid amount: readable at 360px, never overflowing its card. */
    .card__amount { color: #1a1a18; font-size: clamp(1.3rem, 1rem + 1.6vw, 1.85rem); font-weight: 850; letter-spacing: -.01em; line-height: 1.15; overflow-wrap: anywhere; }
    .card small { color: #6b6764; font-size: .76rem; line-height: 1.5; }
    .card--loading span { display: block; height: 4.25rem; border-radius: .35rem; background: linear-gradient(90deg, #e8e5e3 25%, #f7f5f3 50%, #e8e5e3 75%); background-size: 200% 100%; animation: loading 1.5s infinite; }

    .evolution { display: grid; gap: 1rem; padding-top: 1.25rem; border-top: 1px solid rgb(26 26 24 / 10%); }
    .evolution > header { display: flex; flex-direction: column; align-items: flex-start; gap: .75rem; }
    .legend { display: flex; flex-wrap: wrap; gap: .5rem 1rem; margin: 0; padding: 0; list-style: none; color: #575653; font-size: .76rem; font-weight: 700; }
    .legend li { display: flex; align-items: center; gap: .4rem; }
    .swatch { flex: 0 0 auto; width: .8rem; height: .8rem; border-radius: .15rem; }
    .swatch--paid { background: #af0f50; }
    .swatch--recognized { background: #1a1a18; }

    /*
     * Charts keep their minimum readable column width and scroll horizontally
     * inside this container instead of being squeezed to microscopic bars.
     */
    .chart { display: flex; align-items: flex-end; gap: .4rem; min-height: 12rem; max-width: 100%; padding: .75rem .5rem 0; overflow-x: auto; overscroll-behavior-x: contain; }
    .column { position: relative; display: grid; flex: 1 0 2.4rem; gap: .4rem; justify-items: center; outline: none; }
    .column:focus-visible .bars { outline: 2px solid #af0f50; outline-offset: 2px; }
    .bars { display: flex; align-items: flex-end; gap: .18rem; width: 100%; height: clamp(7rem, 22vw, 9rem); }
    .bar { flex: 1 1 0; min-height: 2px; border-radius: .15rem .15rem 0 0; }
    .bar--paid { background: #af0f50; }
    .bar--recognized { background: #1a1a18; }
    .column small { color: #77736f; font-size: .68rem; font-weight: 700; }

    /* Tooltip is clamped to the card so it never creates page overflow. */
    .tooltip { position: absolute; bottom: calc(100% + .4rem); left: 50%; z-index: 3; display: grid; gap: .2rem; width: max-content; min-width: min(12rem, 60vw); max-width: min(15rem, 78vw); padding: .6rem .75rem; transform: translateX(-50%); border-radius: .4rem; background: #1a1a18; color: #fff; font-size: .74rem; line-height: 1.45; box-shadow: 0 .6rem 1.5rem rgb(0 0 0 / 22%); }
    .tooltip strong { font-size: .78rem; }
    .tooltip span { overflow-wrap: anywhere; }

    .empty { margin: 0; padding: 1.5rem; border: 1px dashed rgb(26 26 24 / 18%); border-radius: .4rem; color: #6b6764; font-size: .88rem; text-align: center; }
    @keyframes loading { to { background-position: -200% 0; } }

    /* Tablet: 2 cards per row, header back to a row layout. */
    @media (min-width: 641px) {
      .financial__header { flex-direction: row; align-items: flex-start; justify-content: space-between; gap: 1.5rem; }
      .evolution > header { flex-direction: row; align-items: flex-start; justify-content: space-between; }
      .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }

    /* Desktop: 4 financial cards across. */
    @media (min-width: 1025px) {
      .cards { grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr)); }
    }

    /* Large desktop / TV: extra gap so the chart uses the available space. */
    @media (min-width: 1921px) {
      .cards { gap: 1.25rem; }
      .chart { min-height: 15rem; }
      .bars { height: 11rem; }
    }

    @media (prefers-reduced-motion: reduce) { .card--loading span { animation: none; } }
  `]
})
export class FinancialOverviewComponent {
  /** Analytics payload. Undefined means the source failed — never rendered as zero. */
  @Input() data: OrderAnalyticsResponse | null | undefined = null;
  @Input() loading = false;
  @Input() rangeLabel = '';

  protected readonly hovered = signal<ChartPoint | null>(null);

  protected readonly points = computed<readonly ChartPoint[]>(() => {
    const raw = this.data?.revenueEvolution ?? [];
    if (raw.length === 0) return [];
    const max = Math.max(
      1,
      ...raw.map((point) => Math.max(point.paidRevenue, point.recognizedRevenue))
    );
    return raw.map((point) => ({
      date: point.date,
      paidRevenue: point.paidRevenue,
      recognizedRevenue: point.recognizedRevenue,
      orders: point.orders,
      paidHeight: point.paidRevenue > 0 ? Math.max(2, (point.paidRevenue / max) * 100) : 0,
      recognizedHeight: point.recognizedRevenue > 0 ? Math.max(2, (point.recognizedRevenue / max) * 100) : 0
    }));
  });

  protected readonly chartLabel = computed(() => {
    const points = this.points();
    if (points.length === 0) return 'Aucune donnée de revenu';
    const paid = points.reduce((sum, point) => sum + point.paidRevenue, 0);
    const recognized = points.reduce((sum, point) => sum + point.recognizedRevenue, 0);
    return `Évolution du revenu du ${points[0].date} au ${points[points.length - 1].date}. `
      + `Paid Revenue ${this.format(paid)}, Recognized Revenue ${this.format(recognized)}.`;
  });

  /**
   * Formats an amount using the application's existing DH convention.
   * A null/undefined amount renders "Unavailable" so an API failure is never
   * mistaken for a real zero.
   */
  protected format(amount: number | null | undefined): string {
    if (amount === null || amount === undefined || Number.isNaN(amount)) return 'Unavailable';
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount) + ' DH';
  }
}