import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, of } from 'rxjs';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';
import { AuthService, type WwtradingRole } from '../../core/auth/auth.service';
import { Stock, StockApiService, StockMovement, StockMovementType, StockStatus } from './stock-api.service';

const STOCK_MANAGER_ROLES: readonly WwtradingRole[] = ['ADMIN', 'MANAGER'];
const STOCK_PAGE_SIZE = 20;
const MOVEMENT_PAGE_SIZE = 10;

type StockOperation = 'IN' | 'OUT' | 'ADJUST';
type StatusFilter = 'ALL' | StockStatus;

const STATUS_LABELS: Record<StatusFilter, string> = {
  ALL: 'Tous',
  IN_STOCK: 'En stock',
  LOW_STOCK: 'Stock faible',
  OUT_OF_STOCK: 'Rupture'
};

const STATUS_BADGE: Record<StockStatus, string> = {
  IN_STOCK: 'badge--in',
  LOW_STOCK: 'badge--low',
  OUT_OF_STOCK: 'badge--out'
};

const STATUS_TEXT: Record<StockStatus, string> = {
  IN_STOCK: 'En stock',
  LOW_STOCK: 'Stock faible',
  OUT_OF_STOCK: 'Rupture'
};

const MOVEMENT_LABELS: Record<StockMovementType, string> = {
  IN: 'Entrée',
  OUT: 'Sortie',
  ADJUST: 'Ajustement',
  RESERVE: 'Réservation',
  RELEASE: 'Libération'
};

@Component({
  selector: 'wwt-stock',
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule, PageHeaderComponent, StatCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock.component.html',
  styles: [`
    :host { display: block; }
    .stock { max-width: 1240px; margin: 0 auto; padding: 2rem clamp(1rem, 4vw, 3rem); }

    .header-actions { display: flex; align-items: center; gap: .5rem; }

    .btn { display: inline-flex; align-items: center; gap: .4rem; border: 1px solid #d9e0e4; border-radius: 6px;
      padding: .5rem .9rem; background: #fff; color: #1a1a18; font-size: .875rem; font-weight: 600; cursor: pointer;
      transition: border-color .15s ease, background .15s ease; }
    .btn:hover:not(:disabled) { border-color: #af0f50; color: #af0f50; }
    .btn:focus-visible { outline: 2px solid #af0f50; outline-offset: 2px; }
    .btn--primary { background: #af0f50; border-color: #af0f50; color: #fff; }
    .btn--primary:hover:not(:disabled) { background: #780b38; color: #fff; }
    .btn--danger { border-color: #b32d3e; color: #b32d3e; }
    .btn--danger:hover:not(:disabled) { background: #b32d3e; color: #fff; }
    .btn:disabled { opacity: .5; cursor: not-allowed; }
    .btn--icon { padding: .5rem; }

    .kpi-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    @media (max-width: 1100px) { .kpi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
    @media (max-width: 900px) { .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
    @media (max-width: 640px) { .kpi-grid { grid-template-columns: 1fr; } }

    .toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; padding: .75rem 1rem;
      background: #fff; border: 1px solid #d9e0e4; border-radius: 8px; margin-bottom: 1.25rem; }
    .toolbar__search { flex: 1 1 260px; display: flex; align-items: center; gap: .5rem; border: 1px solid #d9e0e4;
      border-radius: 6px; padding: .45rem .75rem; background: #fff; }
    .toolbar__search:focus-within { border-color: #af0f50; }
    .toolbar__search input { border: 0; outline: 0; flex: 1; min-width: 0; font-size: .9rem; background: transparent; }
    .toolbar__search .material-icons { color: #65727b; font-size: 20px; }
    .toolbar select { border: 1px solid #d9e0e4; border-radius: 6px; padding: .5rem .75rem; font-size: .875rem;
      background: #fff; color: #1a1a18; cursor: pointer; }
    .toolbar select:focus-visible { outline: 2px solid #af0f50; outline-offset: 1px; }
    .toolbar__count { margin-left: auto; color: #65727b; font-size: .8rem; white-space: nowrap; }
    @media (max-width: 640px) { .toolbar__count { margin-left: 0; width: 100%; } }

    .panel { background: #fff; border: 1px solid #d9e0e4; border-radius: 8px; overflow: hidden; }
    .panel + .panel { margin-top: 1.5rem; }
    .panel__head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .9rem 1.25rem;
      border-bottom: 1px solid #d9e0e4; }
    .panel__head h2 { margin: 0; font-size: 1rem; font-weight: 700; }
    .panel__head span { color: #65727b; font-size: .8rem; }

    .table-wrap { overflow-x: auto; }
    table { width: 100%; min-width: 960px; border-collapse: collapse; }
    thead th { position: sticky; top: 0; background: #f4f6f8; color: #65727b; font-size: .7rem; font-weight: 700;
      text-transform: uppercase; letter-spacing: .06em; text-align: left; padding: .7rem 1rem;
      border-bottom: 1px solid #d9e0e4; white-space: nowrap; }
    tbody td { padding: .8rem 1rem; border-bottom: 1px solid #eef2f4; font-size: .9rem; vertical-align: middle; background: #fff; }
    tbody tr:last-child td { border-bottom: 0; }
    tbody tr:hover td { background: #fafbfc; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    thead th.num { text-align: right; }
    .cell-product strong { display: block; font-weight: 600; }
    .cell-product small { color: #65727b; }
    .cell-ref { color: #65727b; font-size: .8rem; }
    .cell-avail { font-weight: 700; color: #1a1a18; }
    .cell-avail--low { color: #a65b00; }
    .cell-avail--out { color: #b32d3e; }
    .cell-muted { color: #65727b; }
    th:first-child, td:first-child { position: sticky; left: 0; z-index: 1; }
    tbody tr:hover td:first-child { background: #fafbfc; }

    .badge { display: inline-flex; align-items: center; gap: .35rem; padding: .25rem .6rem; border-radius: 999px;
      font-size: .72rem; font-weight: 700; white-space: nowrap; }
    .badge::before { content: ''; width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
    .badge--in { background: #e3f2ea; color: #19704a; }
    .badge--low { background: #fdf1dd; color: #a65b00; }
    .badge--out { background: #fbe9ec; color: #b32d3e; }
    .badge--neutral { background: #eef2f4; color: #47555e; }

    .pager { display: flex; align-items: center; justify-content: flex-end; gap: .5rem; padding: .75rem 1rem;
      border-top: 1px solid #d9e0e4; color: #65727b; font-size: .8rem; }

    .state { display: flex; flex-direction: column; align-items: center; gap: .5rem; padding: 3rem 1rem; text-align: center; }
    .state .material-icons { font-size: 40px; color: #d9e0e4; }
    .state strong { font-size: 1rem; }
    .state p { margin: 0; color: #65727b; font-size: .875rem; }
    .state--error .material-icons { color: #b32d3e; }

    .skel-row td { padding: .95rem 1rem; }
    .skel { display: block; height: .8rem; border-radius: 4px; background: #eef2f4; animation: pulse 1.4s ease-in-out infinite; }
    .skel--w40 { width: 40%; } .skel--w60 { width: 60%; } .skel--w80 { width: 80%; }
    .skel--num { margin-left: auto; width: 3rem; }
    @keyframes pulse { 50% { opacity: .45; } }
    @media (prefers-reduced-motion: reduce) { .skel { animation: none; } }

    .drawer-overlay { position: fixed; inset: 0; background: rgba(26, 26, 24, .45); z-index: 40; border: 0; padding: 0; cursor: pointer; }
    .drawer { position: fixed; top: 0; right: 0; bottom: 0; width: min(560px, 100vw); background: #fff; z-index: 41;
      box-shadow: -8px 0 24px rgba(26, 26, 24, .12); display: flex; flex-direction: column; overflow: hidden; }
    .drawer__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.25rem 1.5rem;
      border-bottom: 1px solid #d9e0e4; }
    .drawer__head h2 { margin: 0; font-size: 1.1rem; }
    .drawer__head small { color: #65727b; }
    .drawer__body { flex: 1; overflow-y: auto; padding: 1.5rem; display: flex; flex-direction: column; gap: 1.5rem; }
    .drawer__section h3 { margin: 0 0 .75rem; font-size: .78rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .06em; color: #65727b; }
    .facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
    .fact { border: 1px solid #eef2f4; border-radius: 6px; padding: .75rem .9rem; }
    .fact span { display: block; color: #65727b; font-size: .72rem; text-transform: uppercase; letter-spacing: .04em; }
    .fact strong { font-size: 1.35rem; font-variant-numeric: tabular-nums; }
    .fact--wide { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; }
    .fact--wide strong { font-size: 1rem; }

    .field { display: flex; flex-direction: column; gap: .3rem; }
    .field label { font-size: .78rem; font-weight: 600; color: #47555e; }
    .field input, .field select, .op-form select { border: 1px solid #d9e0e4; border-radius: 6px; padding: .55rem .75rem;
      font-size: .9rem; background: #fff; color: #1a1a18; }
    .field input:focus-visible, .field select:focus-visible, .op-form select:focus-visible {
      outline: 2px solid #af0f50; outline-offset: 1px; border-color: #af0f50; }
    .field-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
    @media (max-width: 480px) { .field-row { grid-template-columns: 1fr; } }
    .op-form { display: flex; flex-direction: column; gap: .9rem; }
    .op-form__actions { display: flex; justify-content: flex-end; }
    .op-form__hint { margin: 0; font-size: .75rem; color: #65727b; }

    .feedback { display: flex; align-items: center; gap: .4rem; padding: .6rem .9rem; border-radius: 6px; font-size: .82rem; font-weight: 600; }
    .feedback--success { background: #e3f2ea; color: #19704a; }
    .feedback--error { background: #fbe9ec; color: #b32d3e; }
    .feedback .material-icons { font-size: 18px; }

    .notice { display: flex; align-items: center; gap: .5rem; padding: .75rem 1rem; border: 1px dashed #d9e0e4;
      border-radius: 6px; color: #65727b; font-size: .82rem; }
  `]
})
export class StockComponent implements OnInit {
  private readonly api = inject(StockApiService);
  readonly auth = inject(AuthService);
  protected readonly stocks = signal<Stock[]>([]);
  protected readonly selected = signal<Stock | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly canManage = this.auth.roles().some((role: WwtradingRole) => STOCK_MANAGER_ROLES.includes(role));

  /* Toolbar */
  protected readonly search = signal('');
  protected readonly statusFilter = signal<StatusFilter>('ALL');
  protected readonly statusOptions = (Object.keys(STATUS_LABELS) as StatusFilter[]).map(k => ({ value: k, label: STATUS_LABELS[k] }));
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusBadge = STATUS_BADGE;
  protected readonly statusText = STATUS_TEXT;
  protected readonly movementLabels = MOVEMENT_LABELS;

  /* List pagination (server-side) */
  protected readonly listPage = signal(0);
  protected readonly listTotalPages = signal(1);
  protected readonly listTotal = signal(0);

  /* Detail drawer */
  protected readonly drawerOpen = signal(false);
  protected readonly movements = signal<StockMovement[]>([]);
  protected readonly movementsLoading = signal(false);
  protected readonly movementPage = signal(0);
  protected readonly movementTotalPages = signal(1);
  protected readonly movementTotal = signal(0);

  /* Operation form */
  protected readonly operation = signal<StockOperation>('IN');
  protected quantity = 1;
  protected targetQuantity = 0;
  protected reason = '';
  protected reference = '';
  protected thresholdValue = 5;
  protected readonly saving = signal(false);
  protected readonly feedback = signal<{ kind: 'success' | 'error'; message: string } | null>(null);

  /* KPIs — computed from already-loaded page data only */
  protected readonly kpiTotal = computed(() => this.stocks().length);
  protected readonly kpiInStock = computed(() => this.stocks().filter(s => s.status === 'IN_STOCK').length);
  protected readonly kpiLowStock = computed(() => this.stocks().filter(s => s.status === 'LOW_STOCK').length);
  protected readonly kpiOutOfStock = computed(() => this.stocks().filter(s => s.status === 'OUT_OF_STOCK').length);
  protected readonly kpiReserved = computed(() => this.stocks().reduce((sum, s) => sum + s.reservedQuantity, 0));

  /* Local display filtering on the currently loaded page rows only,
     without conflicting with the server-side pagination. */
  protected readonly visibleStocks = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    return this.stocks().filter(stock => {
      const matchesStatus = status === 'ALL' || stock.status === status;
      const matchesTerm = !term
        || stock.productName.toLowerCase().includes(term)
        || stock.productReference.toLowerCase().includes(term);
      return matchesStatus && matchesTerm;
    });
  });
  protected readonly filtered = computed(() => this.search().trim() !== '' || this.statusFilter() !== 'ALL');

  ngOnInit(): void { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.listPage(), STOCK_PAGE_SIZE)
      .pipe(
        finalize(() => this.loading.set(false)),
        catchError(() => { this.error.set('Impossible de charger les stocks'); return of(null); })
      )
      .subscribe(page => {
        if (page) {
          this.stocks.set(page.content);
          this.listTotalPages.set(Math.max(page.totalPages, 1));
          this.listTotal.set(page.totalElements);
        }
      });
  }

  protected goListPage(page: number): void {
    if (page < 0 || page >= this.listTotalPages() || page === this.listPage()) return;
    this.listPage.set(page);
    this.load();
  }

  protected resetFilters(): void {
    this.search.set('');
    this.statusFilter.set('ALL');
  }

  /* ── Detail drawer ──────────────────────────────────────── */
  protected select(stock: Stock): void {
    this.selected.set(stock);
    this.targetQuantity = stock.onHandQuantity;
    this.thresholdValue = stock.lowStockThreshold;
    this.quantity = 1;
    this.reason = '';
    this.reference = '';
    this.operation.set('IN');
    this.feedback.set(null);
    this.drawerOpen.set(true);
    this.loadMovements(0);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  private loadMovements(page: number): void {
    const stock = this.selected();
    if (!stock) return;
    this.movementsLoading.set(true);
    this.api.movements(stock.productId, page, MOVEMENT_PAGE_SIZE)
      .pipe(finalize(() => this.movementsLoading.set(false)))
      .subscribe({
        next: p => {
          this.movements.set(p.content);
          this.movementPage.set(p.number);
          this.movementTotalPages.set(Math.max(p.totalPages, 1));
          this.movementTotal.set(p.totalElements);
        },
        error: () => this.movements.set([])
      });
  }

  protected goMovementPage(page: number): void {
    if (page < 0 || page >= this.movementTotalPages() || page === this.movementPage()) return;
    this.loadMovements(page);
  }

  /* ── Stock operations ───────────────────────────────────── */
  protected saveMovement(): void {
    const stock = this.selected();
    if (!stock || this.saving()) return;
    const op = this.operation();
    this.saving.set(true);
    this.feedback.set(null);

    const done = (message: string) => {
      this.saving.set(false);
      this.feedback.set({ kind: 'success', message });
      this.load();
      this.api.get(stock.productId).subscribe(refreshed => {
        this.selected.set(refreshed);
        this.thresholdValue = refreshed.lowStockThreshold;
      });
      this.loadMovements(0);
    };
    const fail = (message: string) => {
      this.saving.set(false);
      this.feedback.set({ kind: 'error', message });
    };

    if (op === 'ADJUST') {
      if (this.targetQuantity < 0) { fail('La quantité cible ne peut pas être négative.'); return; }
      this.api.adjust(stock.productId, this.targetQuantity, this.reference, this.reason)
        .subscribe({ next: () => done('Ajustement enregistré.'), error: () => fail('Ajustement refusé.') });
    } else {
      if (this.quantity <= 0) { fail('La quantité doit être supérieure à zéro.'); return; }
      this.api.operate(stock.productId, op.toLowerCase() as 'in' | 'out', { quantity: this.quantity, reference: this.reference, reason: this.reason })
        .subscribe({ next: () => done(op === 'IN' ? 'Entrée de stock enregistrée.' : 'Sortie de stock enregistrée.'), error: () => fail('Opération de stock refusée.') });
    }
  }

  protected saveThreshold(): void {
    const stock = this.selected();
    if (!stock || this.saving() || !this.canManage) return;
    if (this.thresholdValue < 0) { this.feedback.set({ kind: 'error', message: 'Le seuil ne peut pas être négatif.' }); return; }
    this.saving.set(true);
    this.feedback.set(null);
    this.api.threshold(stock.productId, this.thresholdValue)
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.feedback.set({ kind: 'success', message: 'Seuil enregistré.' });
          this.load();
          this.api.get(stock.productId).subscribe(refreshed => this.selected.set(refreshed));
        },
        error: () => { this.saving.set(false); this.feedback.set({ kind: 'error', message: 'Mise à jour du seuil refusée.' }); }
      });
  }
}
