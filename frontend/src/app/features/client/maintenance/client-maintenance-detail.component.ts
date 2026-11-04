import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { DashboardLayoutComponent } from '../../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MaintenanceApiService } from '../../maintenance/maintenance-api.service';
import { MaintenanceAttachmentsComponent } from '../../maintenance/maintenance-attachments.component';
import { MaintenanceResponse, PRIORITY_LABELS, STATUS_LABELS } from '../../maintenance/maintenance.models';

@Component({
  selector: 'wwt-client-maintenance-detail',
  standalone: true,
  imports: [DatePipe, RouterLink, DashboardLayoutComponent, PageHeaderComponent, MaintenanceAttachmentsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Détail de la demande">
      <wwt-page-header
        title="Détail de la demande"
        subtitle="Consultez les informations transmises et le suivi de votre demande."
        breadcrumb="Espace client / Mes interventions / Détail">
      </wwt-page-header>

      <main class="detail-page">
        <a routerLink="/client/maintenance" class="back">← Mes interventions</a>
        @if (loading()) {
          <section class="state" role="status">Chargement…</section>
        } @else if (error()) {
          <section class="state state--error" role="alert">{{ error() }}</section>
        } @else if (request()) {
          <header class="request-header">
            <div><span>Demande {{ request()!.id.slice(0, 8).toUpperCase() }}</span><h1>{{ request()!.subject }}</h1></div>
            <div class="badges"><strong>{{ priorityLabel(request()!.priority) }}</strong><strong>{{ statusLabel(request()!.status) }}</strong></div>
          </header>

          <div class="grid">
            <section class="card">
              <h2>Machine concernée</h2>
              <dl>
                <dt>Marque, modèle ou référence</dt><dd>{{ request()!.productReference ?? 'Non renseigné' }}</dd>
                <dt>Numéro de série</dt><dd>{{ request()!.serialNumber ?? 'Non renseigné' }}</dd>
                <dt>Référence / N° de bon</dt><dd>{{ request()!.clientReference ?? 'Non renseignée' }}</dd>
              </dl>
            </section>
            <section class="card">
              <h2>Demande</h2>
              <dl>
                <dt>Priorité</dt><dd>{{ priorityLabel(request()!.priority) }}</dd>
                <dt>Créée le</dt><dd>{{ request()!.createdAt | date:'dd/MM/yyyy à HH:mm' }}</dd>
                <dt>Statut</dt><dd>{{ statusLabel(request()!.status) }}</dd>
              </dl>
            </section>
            <section class="card card--wide"><h2>Description du problème</h2><p>{{ request()!.description }}</p></section>
            <wwt-maintenance-attachments class="card--wide" [maintenanceId]="request()!.id"></wwt-maintenance-attachments>
          </div>
        }
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    .detail-page { display: grid; gap: 1.25rem; max-width: 1100px; padding: 1.5rem 2rem 3rem; margin: 0 auto; }
    .back { width: fit-content; color: #af0f50; font-weight: 650; font-size: 0.88rem; text-decoration: none; &:hover { text-decoration: underline; } }
    .state, .card, .request-header { padding: 1.35rem; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff; color: #334155; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04); }
    .state--error { color: #dc2626; background: #fef2f2; border-color: #fecaca; }
    .request-header { display: flex; justify-content: space-between; gap: 1rem; align-items: start; }
    .request-header span { color: #af0f50; font-size: .75rem; font-weight: 750; letter-spacing: 0.05em; text-transform: uppercase; }
    h1 { margin: .3rem 0 0; color: #0f172a; font-size: clamp(1.4rem, 4vw, 1.85rem); font-weight: 750; }
    h2 { margin: 0 0 .9rem; color: #0f172a; font-size: 1rem; font-weight: 700; border-bottom: 1px solid #f1f5f9; padding-bottom: 0.6rem; }
    .badges { display: flex; gap: .5rem; flex-wrap: wrap; }
    .badges strong { padding: .3rem .65rem; border-radius: 999px; background: #fdf2f6; border: 1px solid #fce7f0; color: #af0f50; font-size: .72rem; font-weight: 700; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    .card--wide { grid-column: 1 / -1; }
    dl { display: grid; grid-template-columns: minmax(9rem, .7fr) 1fr; gap: .65rem 1rem; margin: 0; }
    dt { color: #64748b; font-size: .78rem; font-weight: 600; }
    dd { margin: 0; color: #0f172a; font-weight: 650; overflow-wrap: anywhere; }
    p { margin: 0; line-height: 1.65; white-space: pre-wrap; color: #334155; }
    @media (max-width: 700px) { .detail-page { padding: 1rem; } .request-header { flex-direction: column; } .grid { grid-template-columns: 1fr; } .card--wide { grid-column: auto; } dl { grid-template-columns: 1fr; } }
  `]
})
export class ClientMaintenanceDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(MaintenanceApiService);
  protected readonly request = signal<MaintenanceResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Cette demande est introuvable.');
      this.loading.set(false);
      return;
    }
    this.api.getById(id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (request) => this.request.set(request),
      error: () => this.error.set('Cette demande est introuvable ou ne vous appartient pas.')
    });
  }

  protected statusLabel(status: MaintenanceResponse['status']): string { return STATUS_LABELS[status]; }
  protected priorityLabel(priority: MaintenanceResponse['priority']): string { return PRIORITY_LABELS[priority]; }
}
