import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import {
  MaintenanceResponse,
  PageResponse,
  PRIORITY_LABELS,
  STATUS_LABELS,
} from '../../maintenance/maintenance.models';

@Component({
  selector: 'wwt-technician-maintenance-list',
  standalone: true,
  imports: [RouterLink, DatePipe, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Mes interventions">
      <wwt-page-header
        title="Mes interventions"
        subtitle="Demandes de maintenance qui vous sont assignées"
        breadcrumb="Dashboard / Mes interventions">
      </wwt-page-header>

      <main class="maint-page" aria-label="Mes interventions assignées">

        @if (loading()) {
          <div class="state-card state-card--loading" role="status" aria-live="polite">
            <span class="material-icons spin" aria-hidden="true">sync</span>
            <span>Chargement des interventions…</span>
          </div>
        } @else if (error()) {
          <div class="state-card state-card--error" role="alert">
            <span class="material-icons" aria-hidden="true">error_outline</span>
            <div>
              <h2>Erreur de chargement</h2>
              <p>{{ error() }}</p>
              <button class="btn btn--primary" (click)="load()">Réessayer</button>
            </div>
          </div>
        } @else if (page()?.content?.length === 0) {
          <div class="state-card state-card--empty" role="status">
            <span class="material-icons" aria-hidden="true">engineering</span>
            <div>
              <h2>Aucune intervention assignée</h2>
              <p>Vous n'avez actuellement aucune demande de maintenance qui vous est assignée.</p>
            </div>
          </div>
        } @else {
          <section class="maint-panel" aria-labelledby="maint-list-title">
            <header class="maint-panel__header">
              <div>
                <span class="eyebrow">Technicien</span>
                <h2 id="maint-list-title">Interventions assignées</h2>
              </div>
              @if (page(); as p) {
                <span class="result-count">{{ p.totalElements }} intervention{{ p.totalElements > 1 ? 's' : '' }}</span>
              }
            </header>

            <div class="maint-table-wrap" role="region" aria-label="Liste des interventions">
              <table class="maint-table" aria-labelledby="maint-list-title">
                <thead>
                  <tr>
                    <th scope="col">Réf. / Sujet</th>
                    <th scope="col">Machine / Série</th>
                    <th scope="col">Priorité</th>
                    <th scope="col">Statut</th>
                    <th scope="col">Assigné le</th>
                    <th scope="col">Créé le</th>
                    <th scope="col"><span class="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of page()?.content; track item.id) {
                    <tr class="maint-row">
                      <td class="cell-primary">
                        <span class="ref">{{ item.id.slice(0, 8).toUpperCase() }}</span>
                        <span class="subject">{{ item.subject }}</span>
                      </td>
                      <td>
                        <span class="product">{{ item.productReference ?? '—' }}</span>
                        @if (item.serialNumber) {
                          <span class="serial">SN {{ item.serialNumber }}</span>
                        }
                      </td>
                      <td>
                        <span class="badge badge--priority badge--{{ item.priority.toLowerCase() }}">
                          {{ priorityLabel(item.priority) }}
                        </span>
                      </td>
                      <td>
                        <span class="badge badge--status badge--{{ item.status.toLowerCase() }}">
                          {{ statusLabel(item.status) }}
                        </span>
                      </td>
                      <td class="cell-date">{{ item.assignedAt ? (item.assignedAt | date:'dd/MM/yyyy') : '—' }}</td>
                      <td class="cell-date">{{ item.createdAt | date:'dd/MM/yyyy' }}</td>
                      <td class="cell-action">
                        <a [routerLink]="['/technician/maintenance', item.id]" class="btn btn--ghost btn--sm">
                          Voir
                        </a>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            @if (page(); as p) {
              @if (p.totalPages > 1) {
                <div class="pagination" role="navigation" aria-label="Pagination">
                  <button class="btn btn--ghost btn--sm" [disabled]="p.first" (click)="prevPage()">
                    <span class="material-icons" aria-hidden="true">chevron_left</span>
                    Précédent
                  </button>
                  <span class="page-info">Page {{ p.number + 1 }} / {{ p.totalPages }}</span>
                  <button class="btn btn--ghost btn--sm" [disabled]="p.last" (click)="nextPage()">
                    Suivant
                    <span class="material-icons" aria-hidden="true">chevron_right</span>
                  </button>
                </div>
              }
            }
          </section>
        }
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .maint-page {
      display: flex;
      flex-direction: column;
      gap: var(--dashboard-gap);
      padding-block: var(--dashboard-page-padding-block);
      padding-inline: var(--dashboard-page-padding-inline);
    }

    .state-card {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      min-height: clamp(8rem, 18vh, 11rem);
      padding: clamp(1.25rem, 4vw, 2rem);
      background: #1a1a1a; border: 1px solid #2a2a2a; border-radius: 8px; color: #ccc;
    }
    .state-card > div { min-width: 0; }
    .state-card .material-icons { flex: 0 0 auto; font-size: 2.5rem; color: #888; }
    .state-card h2 { margin: 0 0 .25rem; font-size: 1rem; color: #fff; }
    .state-card p { margin: 0; font-size: .875rem; overflow-wrap: anywhere; }
    .state-card--error .material-icons { color: #e53935; }
    .state-card--loading .material-icons { color: #e91e8c; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .spin { animation: spin 1s linear infinite; }

    .maint-panel { background: #111; border: 1px solid #2a2a2a; border-radius: 8px; overflow: hidden; }
    .maint-panel__header {
      display: flex; align-items: center; justify-content: space-between;
      flex-wrap: wrap;
      gap: .65rem 1rem;
      padding: clamp(1rem, 3vw, 1.25rem) clamp(1rem, 3vw, 1.5rem); border-bottom: 1px solid #2a2a2a;
    }
    .maint-panel__header > div { min-width: 0; }
    .eyebrow { display: block; font-size: .7rem; text-transform: uppercase; letter-spacing: .1em; color: #e91e8c; margin-bottom: .2rem; }
    .maint-panel__header h2 { margin: 0; font-size: clamp(1rem, .95rem + .3vw, 1.15rem); color: #fff; }
    .result-count { flex: 0 0 auto; font-size: .8rem; color: #888; background: #1e1e1e; padding: .25rem .6rem; border-radius: 20px; border: 1px solid #2a2a2a; }

    /* Wide table: scrolls horizontally instead of squeezing the columns. */
    .maint-table-wrap { width: 100%; max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; }
    .maint-table { width: 100%; min-width: 820px; border-collapse: collapse; font-size: .875rem; }
    .maint-table thead { background: #1a1a1a; }
    .maint-table th { padding: .75rem 1rem; text-align: left; font-size: .75rem; font-weight: 600; text-transform: uppercase; letter-spacing: .06em; color: #888; border-bottom: 1px solid #2a2a2a; white-space: nowrap; }
    .maint-table td { padding: .85rem 1rem; border-bottom: 1px solid #1e1e1e; vertical-align: middle; }
    .maint-row:last-child td { border-bottom: none; }
    .maint-row:hover { background: #161616; }

    .cell-primary { display: flex; flex-direction: column; gap: .15rem; min-width: 180px; }
    .ref { font-size: .7rem; font-weight: 700; letter-spacing: .08em; color: #e91e8c; font-family: monospace; }
    .subject { color: #e0e0e0; font-weight: 500; line-height: 1.3; }
    .product { display: block; color: #ccc; }
    .serial { display: block; font-size: .75rem; color: #666; font-family: monospace; }
    .cell-date { color: #888; white-space: nowrap; font-size: .8rem; }
    .cell-action { text-align: right; }

    .badge {
      display: inline-flex; align-items: center; padding: .2rem .55rem;
      border-radius: 4px; font-size: .72rem; font-weight: 600; letter-spacing: .04em; text-transform: uppercase;
    }
    .badge--priority.badge--low { background: #1a2a1a; color: #66bb6a; border: 1px solid #2a3a2a; }
    .badge--priority.badge--medium { background: #1a1f2a; color: #64b5f6; border: 1px solid #2a2f3a; }
    .badge--priority.badge--high { background: #2a1a0a; color: #ffa726; border: 1px solid #3a2a0a; }
    .badge--priority.badge--critical { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }

    .badge--status.badge--submitted { background: #1a1a2a; color: #9575cd; border: 1px solid #2a2a3a; }
    .badge--status.badge--assigned { background: #0a1f2a; color: #26c6da; border: 1px solid #0a2f3a; }
    .badge--status.badge--open { background: #1e1e1e; color: #888; border: 1px solid #2a2a2a; }
    .badge--status.badge--in_progress { background: #1a200a; color: #aed581; border: 1px solid #2a300a; }
    .badge--status.badge--completed { background: #0a2a0a; color: #66bb6a; border: 1px solid #1a3a1a; }
    .badge--status.badge--cancelled { background: #2a0a0a; color: #ef5350; border: 1px solid #3a1a1a; }

    .btn { display: inline-flex; align-items: center; justify-content: center; gap: .35rem; min-height: 44px; padding: .5rem .9rem; border: none; cursor: pointer; border-radius: 6px; font-weight: 600; text-decoration: none; transition: background .15s, color .15s; }
    .btn--sm { padding: .45rem .75rem; font-size: .8rem; }
    .btn--primary { background: #e91e8c; color: #fff; font-size: .875rem; }
    .btn--primary:hover { background: #c2185b; }
    .btn--ghost { background: transparent; color: #ccc; border: 1px solid #333; }
    .btn--ghost:hover:not(:disabled) { border-color: #e91e8c; color: #e91e8c; }
    .btn--ghost:disabled { opacity: .4; cursor: not-allowed; }

    .pagination { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: .5rem 1rem; padding: 1rem; border-top: 1px solid #2a2a2a; }
    .page-info { font-size: .8rem; color: #888; }

    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; }

    @media (max-width: 700px) {
      .state-card { align-items: flex-start; }
      .state-card .material-icons { font-size: 2rem; }
      .maint-panel__header { align-items: flex-start; flex-direction: column; }
    }

    @media (min-width: 1921px) {
      .maint-table { min-width: 0; }
      .maint-panel__header h2 { font-size: 1.3rem; }
    }
  `]
})
export class TechnicianMaintenanceListComponent implements OnInit {
  private readonly api = inject(MaintenanceApiService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal<PageResponse<MaintenanceResponse> | null>(null);

  private currentPage = 0;

  ngOnInit(): void { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getMyAssignments(this.currentPage).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Impossible de charger les interventions.');
        this.loading.set(false);
      }
    });
  }

  protected prevPage(): void {
    if (this.currentPage > 0) { this.currentPage--; this.load(); }
  }

  protected nextPage(): void {
    const p = this.page();
    if (p && !p.last) { this.currentPage++; this.load(); }
  }

  protected priorityLabel(p: string): string {
    return PRIORITY_LABELS[p as keyof typeof PRIORITY_LABELS] ?? p;
  }

  protected statusLabel(s: string): string {
    return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s;
  }
}

