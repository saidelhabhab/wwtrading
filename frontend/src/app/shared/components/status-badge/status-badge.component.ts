import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

export type StatusVariant = 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'neutral';

@Component({
  selector: 'wwt-status-badge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.css'
})
export class StatusBadgeComponent {
  @Input() set status(value: string | undefined | null) {
    this._status.set(value || '');
  }
  @Input() label?: string;
  @Input() variant?: StatusVariant;
  @Input() size: 'sm' | 'md' = 'md';
  @Input() showDot = true;

  private _status = signal<string>('');

  computedVariant = computed<StatusVariant>(() => {
    if (this.variant) return this.variant;
    const s = this._status().toUpperCase().trim();
    if (['VALIDEE', 'CONFIRMED', 'CONFIRMEE', 'TERMINEE', 'LIVREE', 'DELIVERED', 'COMPLETED', 'ACTIVE', 'ACCEPTE', 'PAYEE'].includes(s)) {
      return 'success';
    }
    if (['EN_ATTENTE', 'PENDING', 'WAITING', 'ATTENTE', 'A_CONFIRMER', 'PLANIFIEE'].includes(s)) {
      return 'warning';
    }
    if (['ANNULEE', 'CANCELLED', 'REFUSEE', 'REJECTED', 'ERREUR', 'FAILED', 'BLOQUE'].includes(s)) {
      return 'danger';
    }
    if (['EN_COURS', 'PROCESSING', 'EXPEDIEE', 'SHIPPED', 'DIAGNOSTIC', 'INTERVENTION'].includes(s)) {
      return 'info';
    }
    if (['NOUVEAU', 'NEW', 'DEVIS', 'COMMERCIALE'].includes(s)) {
      return 'primary';
    }
    return 'neutral';
  });

  computedLabel = computed<string>(() => {
    if (this.label) return this.label;
    const s = this._status().trim();
    if (!s) return 'Inconnu';
    switch (s.toUpperCase()) {
      case 'EN_ATTENTE':
      case 'PENDING':
        return 'En attente';
      case 'CONFIRMED':
      case 'CONFIRMEE':
        return 'Confirmée';
      case 'VALIDEE':
        return 'Validée';
      case 'PROCESSING':
      case 'EN_COURS':
        return 'En cours';
      case 'DELIVERED':
      case 'LIVREE':
        return 'Livrée';
      case 'CANCELLED':
      case 'ANNULEE':
        return 'Annulée';
      case 'REFUSEE':
      case 'REJECTED':
        return 'Refusée';
      case 'DRAFT':
      case 'BROUILLON':
        return 'Brouillon';
      default:
        return s;
    }
  });
}
