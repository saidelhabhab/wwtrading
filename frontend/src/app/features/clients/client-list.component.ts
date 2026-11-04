import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ClientApiService } from './client-api.service';
import { Client, PageResponse } from './client.models';

@Component({
  selector: 'wwt-client-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Annuaire des clients">
      <wwt-page-header
        title="Annuaire clients"
        subtitle="Consultez et recherchez les comptes clients professionnels de la plateforme."
        [breadcrumb]="breadcrumb()">
      </wwt-page-header>

      <main class="clients-page" aria-labelledby="clients-title">
        <section class="intro-card">
          <div>
            <span class="eyebrow">Répertoire professionnel</span>
            <h1 id="clients-title">Clients professionnels</h1>
            <p>Accédez aux coordonnées et profils des clients pour le suivi commercial et opérationnel.</p>
          </div>
          <span class="intro-card__icon" aria-hidden="true"><mat-icon>business</mat-icon></span>
        </section>

        <section class="clients-panel" aria-labelledby="clients-list-title">
          <header class="panel-header">
            <div class="panel-header__titles">
              <span class="eyebrow">Annuaire actif</span>
              <h2 id="clients-list-title">Liste des clients</h2>
            </div>
            @if (page(); as p) {
              <span class="result-badge">
                {{ p.totalElements }} client{{ p.totalElements > 1 ? 's' : '' }}
              </span>
            }
          </header>

          <div class="filter-bar" aria-label="Recherche de clients">
            <div class="search-wrap">
              <mat-icon class="search-icon" aria-hidden="true">search</mat-icon>
              <input
                type="search"
                class="search-input"
                [value]="searchQuery()"
                (input)="onSearchInput($any($event.target).value)"
                placeholder="Rechercher par nom, prénom, email ou téléphone…"
                aria-label="Rechercher des clients"
              />
              @if (searchQuery()) {
                <button type="button" class="clear-search-btn" (click)="resetSearch()" aria-label="Effacer la recherche">
                  <mat-icon>close</mat-icon>
                </button>
              }
            </div>
          </div>

          @if (loading()) {
            <div class="state-box state-box--loading" role="status" aria-live="polite">
              <span class="spinner" aria-hidden="true"></span>
              <span>Chargement des clients…</span>
            </div>
          } @else if (error()) {
            <div class="state-box state-box--error" role="alert">
              <mat-icon class="state-box__icon">error_outline</mat-icon>
              <strong>Impossible de charger les clients</strong>
              <p>{{ error() }}</p>
              <button type="button" class="btn btn--retry" (click)="load()">
                <mat-icon>refresh</mat-icon> Réessayer
              </button>
            </div>
          } @else if (clients().length === 0) {
            <div class="state-box state-box--empty">
              <mat-icon class="state-box__icon">people_outline</mat-icon>
              @if (hasSearch()) {
                <strong>Aucun client ne correspond à votre recherche</strong>
                <p>Essayez de modifier ou réinitialiser vos termes de recherche.</p>
                <button type="button" class="btn btn--secondary" (click)="resetSearch()">
                  Réinitialiser la recherche
                </button>
              } @else {
                <strong>Aucun client enregistré</strong>
                <p>Aucun compte client professionnel n’est actuellement présent dans l’annuaire.</p>
              }
            </div>
          } @else {
            <div class="table-wrap">
              <table class="clients-table">
                <caption class="visually-hidden">Liste des clients professionnels</caption>
                <thead>
                  <tr>
                    <th>Client</th>
                    <th>Email</th>
                    <th>Téléphone</th>
                    <th>Statut</th>
                    <th class="col-action">Action</th>
                  </tr>
                </thead>
                <tbody>
                  @for (client of clients(); track client.id) {
                    <tr>
                      <td data-label="Client">
                        <div class="client-cell">
                          <span class="avatar-badge" aria-hidden="true">
                            {{ initials(client) }}
                          </span>
                          <div class="client-cell__info">
                            <strong>{{ client.lastName }} {{ client.firstName }}</strong>
                            <small class="client-id-sub">{{ client.id }}</small>
                          </div>
                        </div>
                      </td>

                      <td data-label="Email">
                        <a [href]="'mailto:' + client.email" class="email-link">
                          {{ client.email }}
                        </a>
                      </td>

                      <td data-label="Téléphone">
                        @if (client.phone) {
                          <a [href]="'tel:' + client.phone" class="phone-link" title="Appeler ce client">
                            <mat-icon class="phone-icon">phone</mat-icon>
                            <span>{{ client.phone }}</span>
                          </a>
                        } @else {
                          <span class="muted-text">—</span>
                        }
                      </td>

                      <td data-label="Statut">
                        <span class="status-badge" [class.status-badge--disabled]="!client.enabled">
                          {{ client.enabled ? 'Actif' : 'Désactivé' }}
                        </span>
                      </td>

                      <td data-label="Action" class="col-action">
                        <a [routerLink]="[detailBasePath(), client.id]" class="btn btn--detail">
                          <span>Consulter</span>
                          <mat-icon>chevron_right</mat-icon>
                        </a>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            @if (page(); as p) {
              @if (p.totalPages > 1) {
                <footer class="pagination-bar" aria-label="Pagination des clients">
                  <button
                    type="button"
                    class="btn btn--page"
                    [disabled]="p.first || loading()"
                    (click)="goToPage(currentPageIndex() - 1)"
                    aria-label="Page précédente"
                  >
                    <mat-icon>chevron_left</mat-icon>
                    <span>Précédent</span>
                  </button>

                  <span class="page-info">
                    Page {{ p.number + 1 }} sur {{ p.totalPages }}
                  </span>

                  <button
                    type="button"
                    class="btn btn--page"
                    [disabled]="p.last || loading()"
                    (click)="goToPage(currentPageIndex() + 1)"
                    aria-label="Page suivante"
                  >
                    <span>Suivant</span>
                    <mat-icon>chevron_right</mat-icon>
                  </button>
                </footer>
              }
            }
          }
        </section>
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    :host { display: block; min-height: 100%; }
    .clients-page {
      display: grid;
      gap: 1.4rem;
      width: 100%;
      max-width: var(--dashboard-max-width);
      margin: 0 auto;
      padding-block: var(--dashboard-page-padding-block);
      padding-inline: var(--dashboard-page-padding-inline);
    }
    .intro-card {
      display: flex; align-items: center; justify-content: space-between; gap: 1.5rem;
      padding: clamp(1.25rem, 4vw, 2.2rem); border: 1px solid var(--dashboard-border);
      border-radius: 18px; background: linear-gradient(135deg, #fff 55%, #f9e8ef);
      box-shadow: var(--dashboard-shadow);
    }
    .eyebrow { color: var(--dashboard-primary); font-size: .72rem; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }
    h1, h2, p { margin: 0; }
    h1 { margin-top: .35rem; color: var(--dashboard-text); font-size: clamp(1.5rem, 1.2rem + 1.6vw, 2.5rem); line-height: 1.15; overflow-wrap: anywhere; }
    .intro-card > div { min-width: 0; }
    .intro-card p { margin-top: .6rem; color: var(--dashboard-text-muted); line-height: 1.6; }
    .intro-card__icon { display: grid; flex: 0 0 auto; width: 4.2rem; height: 4.2rem; place-items: center; border-radius: 16px; background: #af0f50; color: #fff; }
    .intro-card__icon mat-icon { font-size: 2.2rem; width: 2.2rem; height: 2.2rem; }

    .clients-panel {
      background: #fff; border: 1px solid var(--dashboard-border); border-radius: 18px;
      padding: clamp(1rem, 3vw, 1.5rem); box-shadow: 0 4px 16px rgb(26 26 24 / 4%);
      display: grid; gap: 1.2rem;
      min-width: 0;
    }
    /* Panel header wraps rather than pushing the result count out of the card. */
    .panel-header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .6rem 1rem; }
    .panel-header__titles { min-width: 0; }
    .panel-header__titles h2 { margin-top: .2rem; font-size: clamp(1.15rem, 1rem + .5vw, 1.3rem); color: var(--dashboard-text); }
    .result-badge {
      display: inline-flex; padding: .35rem .75rem; border-radius: 999px;
      background: #f9e8ef; color: #8f123f; font-size: .8rem; font-weight: 800; white-space: nowrap;
    }

    .filter-bar { display: flex; gap: 1rem; min-width: 0; }
    .search-wrap {
      position: relative; flex: 1; display: flex; min-width: 0; align-items: center;
    }
    .search-icon { position: absolute; left: 1rem; color: #8c8885; pointer-events: none; }
    .search-input {
      width: 100%; min-width: 0; min-height: 44px; padding: .8rem 2.8rem .8rem 2.9rem;
      border: 1px solid var(--dashboard-border); border-radius: 12px; font-size: .95rem;
      background: #fbfaf9; transition: border-color .15s, background .15s;
    }
    .search-input:focus { outline: none; border-color: #af0f50; background: #fff; }
    .clear-search-btn {
      position: absolute; right: .8rem; background: none; border: none;
      color: #8c8885; cursor: pointer; display: grid; place-items: center;
      padding: 0; border-radius: 50%; width: 44px; height: 44px;
    }
    .clear-search-btn:hover { background: #ece8e6; color: #1a1a18; }

    .state-box {
      display: grid; place-items: center; text-align: center; gap: .75rem;
      min-height: clamp(10rem, 24vh, 14rem);
      padding: clamp(1.5rem, 5vw, 3.5rem) clamp(1rem, 4vw, 1.5rem);
      border-radius: 14px; background: #fbfaf9; border: 1px dashed var(--dashboard-border);
    }
    .state-box strong, .state-box p { max-width: 46ch; }
    .state-box__icon { font-size: 3rem; width: 3rem; height: 3rem; color: #8c8885; }
    .state-box--error { border-color: #f2b8b5; background: #fff4f4; color: #8f123f; }
    .state-box--error .state-box__icon { color: #af0f50; }
    .spinner {
      width: 2.4rem; height: 2.4rem; border: 3px solid rgb(175 15 80 / 20%);
      border-top-color: #af0f50; border-radius: 50%; animation: spin 1s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    /* Wide table: scrolls horizontally rather than being squeezed unreadable. */
    .table-wrap { width: 100%; max-width: 100%; overflow-x: auto; overscroll-behavior-x: contain; border: 1px solid #e5dfdc; border-radius: 12px; }
    .clients-table { width: 100%; min-width: 720px; border-collapse: collapse; text-align: left; font-size: .88rem; }
    .clients-table th {
      padding: .85rem 1rem; background: #fbfaf9; color: #625f5c;
      font-size: .72rem; font-weight: 800; letter-spacing: .06em; text-transform: uppercase;
      border-bottom: 1px solid #e5dfdc; white-space: nowrap;
    }
    .clients-table td { padding: 1rem; border-top: 1px solid #e5dfdc; vertical-align: middle; color: #292825; overflow-wrap: anywhere; }
    .clients-table tbody tr:hover { background: #fdf8fa; }

    .client-cell { display: flex; align-items: center; gap: .85rem; }
    .avatar-badge {
      width: 2.5rem; height: 2.5rem; border-radius: 50%;
      background: #f9e8ef; color: #8f123f; font-weight: 800; font-size: .82rem;
      display: grid; place-items: center; flex: 0 0 auto;
    }
    .client-cell__info { display: grid; gap: .1rem; }
    .client-id-sub { font-size: .68rem; color: #8c8885; font-family: monospace; max-width: 14rem; overflow: hidden; text-overflow: ellipsis; }

    .email-link { color: #af0f50; text-decoration: none; word-break: break-all; }
    .email-link:hover { text-decoration: underline; }
    .phone-link {
      display: inline-flex; align-items: center; gap: .35rem; color: #1a1a18;
      font-weight: 700; text-decoration: none; padding: .25rem .5rem;
      border-radius: 6px; background: #f4f0ee; font-size: .82rem;
    }
    .phone-link:hover { background: #f9e8ef; color: #af0f50; }
    .phone-icon { font-size: 1rem; width: 1rem; height: 1rem; color: #af0f50; }
    .muted-text { color: #8c8885; }

    .status-badge {
      display: inline-flex; padding: .28rem .6rem; border-radius: 999px;
      font-size: .72rem; font-weight: 800; background: #e4f2e7; color: #286235;
    }
    .status-badge--disabled { background: #ece8e6; color: #625f5c; }

    .col-action { text-align: right; }
    .btn {
      display: inline-flex; align-items: center; justify-content: center; gap: .4rem;
      min-height: 44px; padding: .55rem 1rem;
      border-radius: 10px; font-size: .85rem; font-weight: 700; cursor: pointer;
      border: none; text-decoration: none; transition: background .15s;
    }
    .btn--detail { background: #f9e8ef; color: #8f123f; }
    .btn--detail:hover { background: #af0f50; color: #fff; }
    .btn--retry { background: #af0f50; color: #fff; margin-top: .5rem; }
    .btn--retry:hover { background: #8f123f; }
    .btn--secondary { background: #ece8e6; color: #292825; margin-top: .5rem; }
    .btn--secondary:hover { background: #ded8d5; }
    .btn--page { background: #fbfaf9; border: 1px solid #ded8d5; color: #292825; }
    .btn--page:not(:disabled):hover { background: #f9e8ef; border-color: #af0f50; color: #8f123f; }
    .btn--page:disabled { opacity: .45; cursor: not-allowed; }

    .pagination-bar {
      display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .75rem 1rem;
      padding-top: .75rem; border-top: 1px solid #f0eae7;
    }
    .page-info { font-size: .85rem; color: #625f5c; font-weight: 700; }

    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }

    /*
     * Tablet portrait and below: the table becomes a card list (one client per
     * card) so every field stays readable without horizontal scrolling.
     */
    @media (max-width: 768px) {
      .intro-card__icon { display: none; }
      .table-wrap { overflow: visible; border: none; }
      .clients-table { min-width: 0; }
      .clients-table thead { display: none; }
      .clients-table, .clients-table tbody { display: block; }
      .clients-table tbody { display: grid; gap: .75rem; }
      .clients-table tr {
        display: grid; gap: .6rem; padding: 1rem;
        border: 1px solid var(--dashboard-border); border-radius: 12px; background: #fff;
      }
      .clients-table td {
        display: flex; justify-content: space-between; align-items: center; gap: 1rem;
        padding: 0; border: none;
      }
      .clients-table td::before {
        flex: 0 0 auto;
        content: attr(data-label); font-size: .68rem; font-weight: 800;
        color: #706a67; text-transform: uppercase; letter-spacing: .06em;
      }
      .clients-table td > * { min-width: 0; }
      .client-cell { text-align: right; }
      .col-action { justify-content: flex-end; margin-top: .4rem; }
      .col-action::before { display: none; }
      .btn--detail { width: 100%; }
      .pagination-bar { flex-direction: column; align-items: stretch; text-align: center; }
      .pagination-bar > * { width: 100%; }
    }
  `]
})
export class ClientListComponent implements OnInit {
  private readonly clientApi = inject(ClientApiService);
  private readonly auth = inject(AuthService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal<PageResponse<Client> | null>(null);
  readonly currentPageIndex = signal(0);
  readonly searchQuery = signal('');

  readonly userRole = computed<WwtradingRole>(() => this.auth.roles()[0] ?? 'COMMERCIAL');

  readonly detailBasePath = computed(() => {
    const role = this.userRole();
    switch (role) {
      case 'ADMIN': return '/admin/clients';
      case 'MANAGER': return '/manager/clients';
      case 'COMMERCIAL': return '/commercial/clients';
      default: return '/commercial/clients';
    }
  });

  readonly breadcrumb = computed(() => {
    const role = this.userRole();
    switch (role) {
      case 'ADMIN': return 'Administration / Clients';
      case 'MANAGER': return 'Management / Clients';
      case 'COMMERCIAL': return 'Commercial / Clients';
      default: return 'Clients';
    }
  });

  readonly clients = computed(() => this.page()?.content ?? []);
  readonly hasSearch = computed(() => this.searchQuery().trim().length > 0);

  ngOnInit(): void {
    this.load();
  }

  load(pageIndex = this.currentPageIndex()): void {
    this.loading.set(true);
    this.error.set(null);
    this.currentPageIndex.set(pageIndex);

    const qArg = this.searchQuery().trim() || undefined;

    this.clientApi.getClients(pageIndex, 20, qArg)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => this.page.set(res),
        error: (err: HttpErrorResponse) => {
          if (err.status === 403) {
            this.error.set('Vous n’êtes pas autorisé à consulter l’annuaire des clients.');
          } else if (err.status === 401) {
            this.error.set('Votre session a expiré. Veuillez vous reconnecter.');
          } else {
            this.error.set('Impossible de charger les données clients. Veuillez réessayer.');
          }
        }
      });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    this.load(0);
  }

  resetSearch(): void {
    this.searchQuery.set('');
    this.load(0);
  }

  goToPage(pageIndex: number): void {
    this.load(pageIndex);
  }

  initials(client: Client): string {
    const f = client.firstName ? client.firstName.charAt(0).toUpperCase() : '';
    const l = client.lastName ? client.lastName.charAt(0).toUpperCase() : '';
    return `${f}${l}` || 'C';
  }
}

