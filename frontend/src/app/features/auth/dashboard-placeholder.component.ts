import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';

@Component({
  selector: 'wwt-dashboard-placeholder',
  standalone: true,
  imports: [DashboardLayoutComponent, PageHeaderComponent, StatCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Tableau de bord">
      <section class="dashboard-page">
        <div class="dashboard-content">
          <wwt-page-header title="Vue d’ensemble" subtitle="Votre espace de travail WWTRADING, prêt pour vos prochains modules." breadcrumb="Espace professionnel" />
          <div class="dashboard-grid" aria-label="Indicateurs de démonstration">
            <wwt-stat-card title="Activité" value="--" icon="◌" description="Données à venir" />
            <wwt-stat-card title="Demandes" value="--" icon="□" description="Données à venir" status="success" />
            <wwt-stat-card title="Suivi" value="--" icon="↗" description="Données à venir" status="warning" />
            <wwt-stat-card title="Service" value="--" icon="✓" description="Données à venir" />
          </div>
        </div>
      </section>
    </wwt-dashboard-layout>
  `
})
export class DashboardPlaceholderComponent {
  @Input() role?: string;
}
