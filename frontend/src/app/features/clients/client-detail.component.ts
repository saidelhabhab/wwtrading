import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ClientApiService } from './client-api.service';
import { Client } from './client.models';

@Component({
  selector: 'wwt-client-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Détail du client">
      <wwt-page-header
        title="Fiche client"
        subtitle="Informations de contact et profil du compte client professionnel."
        [breadcrumb]="breadcrumb()">
      </wwt-page-header>

      <main class="client-detail-page" aria-labelledby="client-detail-title">
        <div class="nav-bar">
          <a class="back-link" [routerLink]="backPath()">
            <mat-icon>arrow_back</mat-icon>
            <span>Retour à l’annuaire des clients</span>
          </a>
        </div>

        @if (loading()) {
          <section class="state-card state-card--loading" role="status" aria-live="polite">
            <span class="spinner" aria-hidden="true"></span>
            <span>Chargement du profil client…</span>
          </section>
        } @else if (isNotFound()) {
          <section class="state-card state-card--not-found" role="alert">
            <mat-icon class="state-icon">person_off</mat-icon>
            <h2 id="client-detail-title">Client introuvable</h2>
            <p>Ce compte client n’existe pas ou n’est pas associé au rôle client dans l’annuaire.</p>
            <a class="btn btn--primary" [routerLink]="backPath()">
              <mat-icon>arrow_back</mat-icon>
              <span>Retourner à la liste</span>
            </a>
          </section>
        } @else if (error()) {
          <section class="state-card state-card--error" role="alert">
            <mat-icon class="state-icon">error_outline</mat-icon>
            <h2>Erreur de chargement</h2>
            <p>{{ error() }}</p>
            <button type="button" class="btn btn--primary" (click)="loadClient()">
              <mat-icon>refresh</mat-icon>
              <span>Réessayer</span>
            </button>
          </section>
        } @else {
          @if (client(); as c) {
            <div class="detail-grid">
              <!-- Header Profile Card -->
              <section class="profile-card">
                <div class="profile-card__main">
                  <span class="avatar-large" aria-hidden="true">{{ initials(c) }}</span>
                  <div class="profile-card__identity">
                    <span class="eyebrow">Compte client professionnel</span>
                    <h1 id="client-detail-title">{{ c.firstName }} {{ c.lastName }}</h1>
                    <span class="status-badge" [class.status-badge--disabled]="!c.enabled">
                      {{ c.enabled ? 'Compte actif' : 'Compte désactivé' }}
                    </span>
                  </div>
                </div>
                <div class="profile-card__meta">
                  <span class="meta-label">Identifiant Keycloak</span>
                  <code class="meta-code">{{ c.id }}</code>
                </div>
              </section>

              <!-- Contact Information Cards -->
              <section class="contact-grid">
                <!-- Phone Card (Primary highlight) -->
                <article class="info-card info-card--highlight">
                  <div class="info-card__header">
                    <span class="info-card__icon info-card__icon--phone"><mat-icon>phone</mat-icon></span>
                    <div>
                      <h3 class="info-card__title">Téléphone principal</h3>
                      <small class="info-card__desc">Numéro de contact direct pour les devis et interventions</small>
                    </div>
                  </div>
                  <div class="info-card__body">
                    @if (c.phone) {
                      <div class="phone-display">
                        <strong class="phone-number">{{ c.phone }}</strong>
                        <a [href]="'tel:' + c.phone" class="btn btn--call" title="Lancer un appel vers ce numéro">
                          <mat-icon>call</mat-icon>
                          <span>Appeler</span>
                        </a>
                      </div>
                    } @else {
                      <div class="phone-empty">
                        <span class="muted-text">Aucun numéro de téléphone renseigné.</span>
                      </div>
                    }
                  </div>
                </article>

                <!-- Email Card -->
                <article class="info-card">
                  <div class="info-card__header">
                    <span class="info-card__icon info-card__icon--email"><mat-icon>email</mat-icon></span>
                    <div>
                      <h3 class="info-card__title">Adresse email</h3>
                      <small class="info-card__desc">Courriel professionnel associé au compte</small>
                    </div>
                  </div>
                  <div class="info-card__body">
                    <div class="email-display">
                      <strong class="email-value">{{ c.email }}</strong>
                      <a [href]="'mailto:' + c.email" class="btn btn--email" title="Envoyer un email">
                        <mat-icon>send</mat-icon>
                        <span>Contacter par email</span>
                      </a>
                    </div>
                  </div>
                </article>

                <!-- Account Details Card -->
                <article class="info-card">
                  <div class="info-card__header">
                    <span class="info-card__icon info-card__icon--account"><mat-icon>badge</mat-icon></span>
                    <div>
                      <h3 class="info-card__title">Informations du compte</h3>
                      <small class="info-card__desc">État du profil dans l’annuaire WWTRADING</small>
                    </div>
                  </div>
                  <div class="info-card__body">
                    <dl class="account-dl">
                      <div class="account-dl__row">
                        <dt>Prénom</dt>
                        <dd><strong>{{ c.firstName }}</strong></dd>
                      </div>
                      <div class="account-dl__row">
                        <dt>Nom</dt>
                        <dd><strong>{{ c.lastName }}</strong></dd>
                      </div>
                      <div class="account-dl__row">
                        <dt>Statut du compte</dt>
                        <dd>
                          <span class="status-badge status-badge--inline" [class.status-badge--disabled]="!c.enabled">
                            {{ c.enabled ? 'Actif' : 'Désactivé' }}
                          </span>
                        </dd>
                      </div>
                    </dl>
                  </div>
                </article>
              </section>
            </div>
          }
        }
      </main>
    </wwt-dashboard-layout>
  `,
  styles: [`
    :host { display: block; min-height: 100%; }
    .client-detail-page { display: grid; gap: 1.4rem; max-width: 1200px; margin: 0 auto; padding: 24px 32px 48px; }

    .nav-bar { display: flex; align-items: center; }
    .back-link {
      display: inline-flex; align-items: center; gap: .5rem;
      color: #af0f50; text-decoration: none; font-weight: 700; font-size: .9rem;
      padding: .4rem .8rem; border-radius: 8px; background: #f9e8ef;
      transition: background .15s, color .15s;
    }
    .back-link:hover { background: #af0f50; color: #fff; }

    .state-card {
      display: grid; place-items: center; text-align: center; gap: .8rem;
      padding: 4rem 1.5rem; border-radius: 18px; background: #fff; border: 1px solid #ded8d5;
      box-shadow: 0 8px 24px rgb(26 26 24 / 5%);
    }
    .state-icon { font-size: 3.5rem; width: 3.5rem; height: 3.5rem; color: #8c8885; }
    .state-card--not-found .state-icon { color: #8f123f; }
    .state-card--error .state-icon { color: #af0f50; }
    .state-card h2 { margin: 0; font-size: 1.5rem; color: #1a1a18; }
    .state-card p { margin: 0; color: #625f5c; max-width: 28rem; }
    .spinner {
      width: 2.8rem; height: 2.8rem; border: 3px solid rgb(175 15 80 / 20%);
      border-top-color: #af0f50; border-radius: 50%; animation: spin 1s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .detail-grid { display: grid; gap: 1.4rem; }

    .profile-card {
      background: linear-gradient(135deg, #fff 60%, #f9e8ef);
      border: 1px solid #ded8d5; border-radius: 18px; padding: 2rem;
      box-shadow: 0 8px 24px rgb(26 26 24 / 5%);
      display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1.5rem;
    }
    .profile-card__main { display: flex; align-items: center; gap: 1.5rem; }
    .avatar-large {
      width: 4.5rem; height: 4.5rem; border-radius: 50%;
      background: #af0f50; color: #fff; font-size: 1.6rem; font-weight: 800;
      display: grid; place-items: center; flex: 0 0 auto;
      box-shadow: 0 6px 18px rgb(175 15 80 / 30%);
    }
    .profile-card__identity { display: grid; gap: .35rem; }
    .eyebrow { color: #af0f50; font-size: .72rem; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }
    .profile-card__identity h1 { margin: 0; font-size: clamp(1.6rem, 3.5vw, 2.2rem); color: #1a1a18; }
    .profile-card__meta {
      display: grid; gap: .3rem; text-align: right;
      background: #fbfaf9; padding: .8rem 1.2rem; border-radius: 12px; border: 1px solid #e5dfdc;
    }
    .meta-label { font-size: .7rem; text-transform: uppercase; color: #706a67; font-weight: 800; letter-spacing: .06em; }
    .meta-code { font-family: monospace; font-size: .85rem; color: #1a1a18; }

    .contact-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.4rem; }

    .info-card {
      background: #fff; border: 1px solid #ded8d5; border-radius: 18px;
      padding: 1.5rem; box-shadow: 0 4px 16px rgb(26 26 24 / 4%);
      display: grid; gap: 1.2rem;
    }
    .info-card--highlight { border-color: #f2c2d4; background: linear-gradient(180deg, #fff, #fdf8fa); }
    .info-card__header { display: flex; align-items: flex-start; gap: 1rem; }
    .info-card__icon {
      width: 2.8rem; height: 2.8rem; border-radius: 12px;
      display: grid; place-items: center; flex: 0 0 auto;
    }
    .info-card__icon--phone { background: #f9e8ef; color: #af0f50; }
    .info-card__icon--email { background: #e6f0fa; color: #1565c0; }
    .info-card__icon--account { background: #f3f0ea; color: #5d4037; }
    .info-card__title { margin: 0; font-size: 1.1rem; color: #1a1a18; }
    .info-card__desc { color: #706a67; font-size: .78rem; display: block; margin-top: .15rem; }

    .info-card__body { padding-top: .5rem; border-top: 1px solid #f0eae7; }

    .phone-display { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; }
    .phone-number { font-size: 1.4rem; color: #1a1a18; font-family: system-ui, -apple-system, sans-serif; letter-spacing: .02em; }
    .email-display { display: grid; gap: .75rem; }
    .email-value { font-size: 1.05rem; color: #1a1a18; word-break: break-all; }

    .phone-empty { padding: .5rem 0; }
    .muted-text { color: #8c8885; font-style: italic; font-size: .9rem; }

    .account-dl { margin: 0; display: grid; gap: .75rem; }
    .account-dl__row { display: flex; justify-content: space-between; align-items: center; padding-bottom: .5rem; border-bottom: 1px dashed #f0eae7; }
    .account-dl__row:last-child { border-bottom: none; padding-bottom: 0; }
    .account-dl dt { color: #706a67; font-size: .85rem; font-weight: 600; }
    .account-dl dd { margin: 0; font-size: .95rem; color: #1a1a18; }

    .status-badge {
      display: inline-flex; padding: .35rem .75rem; border-radius: 999px;
      font-size: .78rem; font-weight: 800; background: #e4f2e7; color: #286235;
    }
    .status-badge--disabled { background: #ece8e6; color: #625f5c; }
    .status-badge--inline { padding: .25rem .55rem; font-size: .72rem; }

    .btn {
      display: inline-flex; align-items: center; gap: .45rem; padding: .65rem 1.2rem;
      border-radius: 12px; font-size: .88rem; font-weight: 700; cursor: pointer;
      border: none; text-decoration: none; transition: background .15s;
    }
    .btn--primary { background: #af0f50; color: #fff; }
    .btn--primary:hover { background: #8f123f; }
    .btn--call { background: #286235; color: #fff; padding: .65rem 1.4rem; }
    .btn--call:hover { background: #1c4825; }
    .btn--email { background: #1565c0; color: #fff; justify-self: start; }
    .btn--email:hover { background: #0d47a1; }

    @media (max-width: 680px) {
      .client-detail-page { padding: 18px 14px 34px; }
      .profile-card { flex-direction: column; align-items: flex-start; padding: 1.4rem; }
      .profile-card__meta { text-align: left; width: 100%; box-sizing: border-box; }
      .phone-display { flex-direction: column; align-items: flex-start; }
      .btn--call { width: 100%; justify-content: center; }
      .btn--email { width: 100%; justify-content: center; }
    }
  `]
})
export class ClientDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly clientApi = inject(ClientApiService);
  private readonly auth = inject(AuthService);

  readonly loading = signal(true);
  readonly client = signal<Client | null>(null);
  readonly error = signal<string | null>(null);
  readonly isNotFound = signal(false);

  readonly userRole = computed<WwtradingRole>(() => this.auth.roles()[0] ?? 'COMMERCIAL');

  readonly backPath = computed(() => {
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
      case 'ADMIN': return 'Administration / Clients / Détail';
      case 'MANAGER': return 'Management / Clients / Détail';
      case 'COMMERCIAL': return 'Commercial / Clients / Détail';
      default: return 'Clients / Détail';
    }
  });

  ngOnInit(): void {
    this.loadClient();
  }

  loadClient(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.isNotFound.set(true);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.isNotFound.set(false);

    this.clientApi.getClient(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (c) => this.client.set(c),
        error: (err: HttpErrorResponse) => {
          if (err.status === 404) {
            this.isNotFound.set(true);
          } else if (err.status === 403) {
            this.error.set('Vous n’êtes pas autorisé à consulter ce client.');
          } else if (err.status === 401) {
            this.error.set('Votre session a expiré. Veuillez vous reconnecter.');
          } else {
            this.error.set('Impossible de charger les informations du client.');
          }
        }
      });
  }

  initials(client: Client): string {
    const f = client.firstName ? client.firstName.charAt(0).toUpperCase() : '';
    const l = client.lastName ? client.lastName.charAt(0).toUpperCase() : '';
    return `${f}${l}` || 'C';
  }
}

