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
  selector: 'wwt-client-maintenance-list',
  standalone: true,
  imports: [RouterLink, DatePipe, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Mes interventions">
      <wwt-page-header
        title="Mes interventions"
        subtitle="Demandes de maintenance et suivi des travaux de vos équipements"
        breadcrumb="Espace client / Mes interventions">
      </wwt-page-header>

      <main class="maint-page" aria-label="Liste de mes interventions">

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
        } @else {
          <section class="maint-panel" aria-labelledby="maint-list-title">
            <header class="maint-panel__header">
              <div>
                <span class="eyebrow">Espace client</span>
                <h2 id="maint-list-title">Mes demandes de maintenance</h2>
              </div>
              <div class="header-right">
                @if (page(); as p) {
                  <span class="result-count">{{ p.totalElements }} demande{{ p.totalElements > 1 ? 's' : '' }}</span>
                }
                <a class="btn btn--primary" routerLink="/client/maintenance/new">
                  <span class="material-icons" aria-hidden="true">add</span>
                  Nouvelle demande
                </a>
              </div>
            </header>

            @if (confirmingId()) {
              <div class="alert alert--info" role="status">
                <span class="material-icons spin" aria-hidden="true">sync</span>
                Confirmation en cours…
              </div>
            }

            @if (page()?.content?.length === 0) {
              <div class="empty-state" role="status">
                <span class="material-icons" aria-hidden="true">engineering</span>
                <p>Aucune demande de maintenance enregistrée pour le moment.</p>
                <a class="btn btn--primary" routerLink="/client/maintenance/new">Créer une demande</a>
              </div>
            } @else {
              <div class="maint-table-wrap" role="region" aria-label="Liste des interventions">
                <table class="maint-table" aria-labelledby="maint-list-title">
                  <thead>
                    <tr>
                      <th scope="col">Réf. / Sujet</th>
                      <th scope="col">Équipement / N° Série</th>
                      <th scope="col">Priorité</th>
                      <th scope="col">Statut</th>
                      <th scope="col">Date de demande</th>
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
                        <td class="cell-date">{{ item.createdAt | date:'dd/MM/yyyy' }}</td>
                        <td class="cell-action">
                          <a class="btn btn--ghost btn--sm" [routerLink]="['/client/maintenance', item.id]">Voir</a>
                          @if (item.status === 'COMPLETION_PENDING_CONFIRMATION') {
                            <button
                              type="button"
                              class="btn btn--primary btn--sm"
                              (click)="confirmCompletion(item.id)"
                              [disabled]="confirmingId() === item.id">
                              Confirmer la fin
                            </button>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>
        }
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .maint-page {
      padding: 1.5rem 2rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      max-width: var(--dashboard-max-width, 1680px);
      margin: 0 auto;
    }

    .state-card {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      padding: 2rem;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      color: #64748b;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }
    .state-card .material-icons { font-size: 2.5rem; color: #64748b; }
    .state-card h2 { margin: 0 0 .25rem; font-size: 1rem; color: #0f172a; font-weight: 700; }
    .state-card p { margin: 0 0 .75rem; font-size: .875rem; }
    .state-card--error .material-icons { color: #ef4444; }
    .state-card--loading .material-icons { color: #af0f50; }
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    .maint-panel {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
    }
    .maint-panel__header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      margin-bottom: 1.5rem;
      padding-bottom: 1.15rem;
      border-bottom: 1px solid #e2e8f0;
    }
    .eyebrow {
      font-size: .72rem;
      text-transform: uppercase;
      letter-spacing: .08em;
      color: #af0f50;
      font-weight: 750;
    }
    .maint-panel__header h2 {
      margin: .25rem 0 0;
      font-size: 1.25rem;
      font-weight: 750;
      color: #0f172a;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .result-count {
      font-size: .82rem;
      color: #64748b;
      font-weight: 600;
    }

    .alert--info {
      display: flex;
      align-items: center;
      gap: .75rem;
      padding: .875rem 1.25rem;
      background: #fdf2f6;
      border: 1px solid #fce7f0;
      border-radius: 8px;
      color: #af0f50;
      margin-bottom: 1rem;
      font-size: .875rem;
    }

    .empty-state {
      text-align: center;
      padding: 4rem 2rem;
      color: #64748b;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1rem;
    }
    .empty-state .material-icons { font-size: 3.5rem; color: #94a3b8; }
    .empty-state p { margin: 0; font-size: .95rem; }

    .maint-table-wrap { overflow-x: auto; }
    .maint-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: .875rem;
    }
    .maint-table th {
      padding: .75rem 1rem;
      color: #64748b;
      font-weight: 650;
      border-bottom: 1px solid #e2e8f0;
      font-size: .775rem;
      text-transform: uppercase;
      letter-spacing: .05em;
    }
    .maint-row {
      border-bottom: 1px solid #f1f5f9;
      transition: background .15s ease;
    }
    .maint-row:hover { background: #f8fafc; }
    .maint-table td { padding: 1rem; color: #334155; vertical-align: middle; }

    .cell-primary { display: flex; flex-direction: column; gap: .2rem; }
    .cell-primary .ref { font-family: monospace; font-size: .75rem; color: #af0f50; font-weight: 700; }
    .cell-primary .subject { font-weight: 650; color: #0f172a; }

    .product { display: block; color: #0f172a; font-weight: 500; }
    .serial { display: block; font-family: monospace; font-size: .75rem; color: #64748b; }
    .cell-date { color: #64748b; white-space: nowrap; font-size: 0.82rem; }

    .badge {
      display: inline-block;
      padding: .25rem .625rem;
      border-radius: 999px;
      font-size: .72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: .03em;
    }
    .badge--priority.badge--low { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
    .badge--priority.badge--medium { background: #fef3c7; color: #92400e; border: 1px solid #fde68a; }
    .badge--priority.badge--high { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }

    .badge--status.badge--open { background: #f1f5f9; color: #475569; border: 1px solid #e2e8f0; }
    .badge--status.badge--assigned { background: #f0f9ff; color: #075985; border: 1px solid #bae6fd; }
    .badge--status.badge--accepted { background: #ecfeff; color: #155e75; border: 1px solid #a5f3fc; }
    .badge--status.badge--in_progress { background: #faf5ff; color: #6b21a8; border: 1px solid #e9d5ff; }
    .badge--status.badge--waiting_for_part { background: #fffbeb; color: #92400e; border: 1px solid #fde68a; }
    .badge--status.badge--completion_pending_confirmation { background: #fff7ed; color: #9a3412; border: 1px solid #ffedd5; }
    .badge--status.badge--completed { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
    .badge--status.badge--rejected { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: .5rem;
      padding: .5rem 1rem;
      border-radius: 8px;
      font-size: .85rem;
      font-weight: 650;
      cursor: pointer;
      border: 1px solid transparent;
      text-decoration: none;
      transition: all .18s ease;
    }
    .btn--sm { padding: .35rem .75rem; font-size: .8rem; }
    .btn--primary { background: #af0f50; color: #fff; box-shadow: 0 1px 2px rgba(175, 15, 80, 0.25); }
    .btn--primary:hover:not(:disabled) { background: #930b42; transform: translateY(-1px); }
    .btn--secondary { background: #ffffff; border: 1px solid #e2e8f0; color: #334155; }
    .btn--secondary:hover:not(:disabled) { border-color: #cbd5e1; background: #f8fafc; }
    .btn--danger { background: #ef4444; color: #fff; }
    .btn--danger:hover:not(:disabled) { background: #dc2626; }
    .btn--primary:disabled { opacity: .5; cursor: not-allowed; }
    .btn--ghost { border: 1px solid #444; background: transparent; color: #ddd; }

    .sr-only {
      position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
      overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;
    }
  `]
})
export class ClientMaintenanceListComponent implements OnInit {
  private readonly api = inject(MaintenanceApiService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal<PageResponse<MaintenanceResponse> | null>(null);
  protected readonly confirmingId = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.getMyRequests(0, 20).subscribe({
      next: (res) => {
        this.page.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Impossible de charger vos demandes de maintenance.');
        this.loading.set(false);
      }
    });
  }

  protected confirmCompletion(id: string): void {
    this.confirmingId.set(id);
    this.api.confirmCompletion(id).subscribe({
      next: () => {
        this.confirmingId.set(null);
        this.load();
      },
      error: (err) => {
        this.confirmingId.set(null);
        alert(err?.error?.message ?? 'Erreur lors de la confirmation.');
      }
    });
  }

  protected statusLabel(status: string): string {
    return STATUS_LABELS[status as keyof typeof STATUS_LABELS] ?? status;
  }

  protected priorityLabel(priority: string): string {
    return PRIORITY_LABELS[priority as keyof typeof PRIORITY_LABELS] ?? priority;
  }
}
