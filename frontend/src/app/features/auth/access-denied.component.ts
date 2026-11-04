import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'wwt-access-denied',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="access-page">
      <div class="access-card">
        <div class="shield-badge">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="shield-icon">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </div>
        <h1 class="access-title">Accès non autorisé</h1>
        <p class="access-desc">
          Votre compte ne dispose pas des privilèges requis pour accéder à cette ressource. Veuillez contacter l'administrateur ou vérifier vos identifiants.
        </p>
        <div class="access-actions">
          <a routerLink="/" class="btn-primary">Retour au tableau de bord</a>
          <a routerLink="/login" class="btn-secondary">Se reconnecter</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .access-page {
      min-height: 100vh;
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: radial-gradient(circle at top left, #1e293b 0%, #0f172a 50%, #020617 100%);
      padding: 1.5rem;
      font-family: inherit;
    }
    .access-card {
      width: 100%;
      max-width: 480px;
      background: #ffffff;
      border-radius: 16px;
      padding: 3rem 2.5rem;
      text-align: center;
      box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.45);
      border: 1px solid rgba(255, 255, 255, 0.1);
    }
    .shield-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: #fef2f2;
      color: #dc2626;
      margin-bottom: 1.5rem;
      box-shadow: 0 4px 12px rgba(220, 38, 38, 0.15);
    }
    .shield-icon {
      width: 32px;
      height: 32px;
    }
    .access-title {
      font-size: 1.6rem;
      font-weight: 750;
      color: #0f172a;
      letter-spacing: -0.025em;
      margin-bottom: 0.75rem;
    }
    .access-desc {
      color: #64748b;
      font-size: 0.925rem;
      line-height: 1.6;
      margin-bottom: 2rem;
    }
    .access-actions {
      display: flex;
      gap: 0.85rem;
      justify-content: center;
      flex-wrap: wrap;
    }
    .btn-primary {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0.82rem 1.5rem;
      background-color: #af0f50;
      color: #ffffff;
      font-weight: 650;
      font-size: 0.925rem;
      border-radius: 8px;
      text-decoration: none;
      transition: all 180ms ease;
      box-shadow: 0 2px 6px rgba(175, 15, 80, 0.25);
    }
    .btn-primary:hover {
      background-color: #8f123f;
      transform: translateY(-1px);
    }
    .btn-secondary {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0.82rem 1.5rem;
      background-color: #f8fafc;
      color: #1e293b;
      border: 1px solid #cbd5e1;
      font-weight: 650;
      font-size: 0.925rem;
      border-radius: 8px;
      text-decoration: none;
      transition: all 180ms ease;
    }
    .btn-secondary:hover {
      background-color: #f1f5f9;
      border-color: #94a3b8;
    }
  `]
})
export class AccessDeniedComponent {}
