import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService, WwtradingRole } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { OrderKpiStripComponent } from '../../shared/components/order-kpi-strip/order-kpi-strip.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { OrderApiService } from './order-api.service';
import {
  ClaimGuestOrdersResponse,
  DeliveryMethod,
  DeliverySettings,
  OrderResponse,
  PageResponse,
  PaymentStatus,
  deliveryAreaLabel,
  deliveryFeeWaivedLabel,
  orderStatusLabel,
  paymentPlanLabel,
  paymentStatusLabel
} from './order.models';

@Component({
  selector: 'wwt-order-history',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, DashboardLayoutComponent, PageHeaderComponent, OrderKpiStripComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-history.component.html',
  styleUrl: './order-history.component.scss',
})
export class OrderHistoryComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly orderApi = inject(OrderApiService);
  protected readonly role = computed<WwtradingRole | null>(() => this.auth.roles()[0] ?? null);
  protected readonly isClient = computed(() => this.role() === 'CLIENT');
  protected readonly isAdmin = computed(() => this.role() === 'ADMIN');
  protected readonly deliverySettings = signal<DeliverySettings | null>(null);
  protected readonly deliverySettingsSaving = signal(false);
  protected readonly claimingGuestOrders = signal(false);
  protected readonly claimBannerMessage = signal<string | null>(null);
  protected readonly claimErrorMessage = signal<string | null>(null);
  protected readonly capabilityMessage = signal<string | null>(null);
  protected readonly page = signal<PageResponse<OrderResponse> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly statusFilter = signal<'ALL' | OrderResponse['status']>('ALL');
  protected readonly paymentFilter = signal<'ALL' | PaymentStatus>('ALL');
  protected readonly skeletonRows = [1, 2, 3, 4, 5] as const;
  protected readonly basePath = computed(() => {
    switch (this.role()) {
      case 'ADMIN': return '/admin/orders';
      case 'MANAGER': return '/manager/orders';
      case 'COMMERCIAL': return '/commercial/orders';
      case 'TECHNICIAN': return '/technician/orders';
      default: return '/client/orders';
    }
  });
  protected readonly pageDescription = computed(() => this.isClient()
    ? 'Suivez vos commandes et consultez leur progression.'
    : 'Consultez et pilotez les commandes selon les autorisations de votre rôle.');
  protected readonly breadcrumb = computed(() => this.isClient() ? 'Espace client / Commandes' : `${this.roleLabel()} / Commandes`);
  protected readonly hasActiveFilters = computed(() => this.search().trim() !== '' || this.statusFilter() !== 'ALL' || this.paymentFilter() !== 'ALL');
  protected readonly visibleOrders = computed(() => {
    const term = this.search().trim().toLowerCase();
    return (this.page()?.content ?? []).filter((order) => {
      const matchesOrderId = order.id.toLowerCase().includes(term)
        || (order.orderNumber?.toLowerCase().includes(term) ?? false);
      const matchesCustomer = !this.isClient() && (
        order.customerReference.toLowerCase().includes(term)
        || (order.contactFullName?.toLowerCase().includes(term) ?? false)
        || (order.contactEmail?.toLowerCase().includes(term) ?? false)
        || (order.contactPhone?.toLowerCase().includes(term) ?? false)
      );
      const matchesSearch = !term || matchesOrderId || matchesCustomer;
      const matchesStatus = this.statusFilter() === 'ALL' || order.status === this.statusFilter();
      const matchesPayment = this.paymentFilter() === 'ALL' || order.paymentStatus === this.paymentFilter();
      return matchesSearch && matchesStatus && matchesPayment;
    });
  });

  ngOnInit(): void {
    if (this.role() === 'TECHNICIAN') {
      this.capabilityMessage.set("Vous n'avez pas accès aux commandes.");
      this.loading.set(false);
      return;
    }
    if (this.isAdmin()) {
      this.orderApi.adminGetDeliverySettings().subscribe({
        next: (settings) => this.deliverySettings.set(settings),
        error: () => this.deliverySettings.set(null)
      });
    }
    if (this.isClient()) {
      this.orderApi.claimGuestOrders().subscribe({
        next: (claimResult: ClaimGuestOrdersResponse) => {
          if (claimResult.linkedCount > 0) {
            this.claimBannerMessage.set(
              `${claimResult.linkedCount} commande(s) invitée(s) associée(s) à votre adresse e-mail vérifiée ont été rattachées à votre compte.`
            );
            this.load();
          }
        },
        error: () => {}
      });
    }
    this.load();
  }

  protected claimGuestOrders(): void {
    if (this.claimingGuestOrders()) return;
    this.claimingGuestOrders.set(true);
    this.claimBannerMessage.set(null);
    this.claimErrorMessage.set(null);
    this.orderApi.claimGuestOrders().pipe(finalize(() => this.claimingGuestOrders.set(false))).subscribe({
      next: (result) => {
        if (result.linkedCount > 0) {
          this.claimBannerMessage.set(
            `${result.linkedCount} commande(s) invitée(s) ont été rattachées à votre compte.`
          );
          this.load();
        } else {
          this.claimBannerMessage.set(
            `Aucune nouvelle commande invitée en attente de rattachement pour votre adresse e-mail vérifiée.`
          );
        }
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.claimErrorMessage.set(
            err.error?.message || "Votre adresse e-mail doit être vérifiée avant de pouvoir rattacher des commandes invitées."
          );
        } else {
          this.claimErrorMessage.set("Impossible de rattacher les commandes invitées pour le moment.");
        }
      }
    });
  }

  protected toggleDeliveryFees(): void {
    const current = this.deliverySettings();
    if (!current || this.deliverySettingsSaving()) return;
    this.deliverySettingsSaving.set(true);
    this.orderApi.adminUpdateDeliverySettings({
      feesEnabled: !current.feesEnabled,
      kenitraAreaFee: current.kenitraAreaFee,
      casablancaFee: current.casablancaFee,
      otherAreasFee: current.otherAreasFee,
      freeDeliveryThreshold: current.freeDeliveryThreshold
    }).pipe(finalize(() => this.deliverySettingsSaving.set(false))).subscribe({
      next: (updated) => this.deliverySettings.set(updated),
      error: () => {}
    });
  }

  protected load(page = 0): void {
    this.loading.set(true);
    this.error.set(null);
    const request = this.isClient() ? this.orderApi.getMyOrders(page) : this.orderApi.getAllOrders(page);
    request.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (result) => this.page.set(result),
      error: (error: unknown) => this.error.set(this.message(error))
    });
  }

  protected resetFilters(): void {
    this.search.set('');
    this.statusFilter.set('ALL');
    this.paymentFilter.set('ALL');
  }

  protected statusLabel(status: OrderResponse['status'], deliveryMethod: DeliveryMethod): string {
    return orderStatusLabel(status, deliveryMethod);
  }

  protected paymentLabel(status: PaymentStatus): string {
    return paymentStatusLabel(status);
  }

  protected paymentPlanLabel = paymentPlanLabel;
  protected deliveryAreaLabel = deliveryAreaLabel;
  protected deliveryFeeWaivedLabel = deliveryFeeWaivedLabel;

  protected deliveryLabel(method: OrderResponse['deliveryMethod']): string {
    return method === 'DELIVERY' ? 'Livraison' : 'Retrait';
  }

  protected statusClass(status: OrderResponse['status']): string {
    return `badge badge--${status.toLowerCase()}`;
  }

  protected paymentClass(status: PaymentStatus): string {
    return `badge badge--${status.toLowerCase()}`;
  }

  private roleLabel(): string {
    switch (this.role()) {
      case 'ADMIN': return 'Administration';
      case 'MANAGER': return 'Management';
      case 'COMMERCIAL': return 'Commercial';
      default: return 'Espace client';
    }
  }

  private message(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.';
    if (error instanceof HttpErrorResponse && error.status === 403) return 'Cette liste de commandes n’est pas autorisée pour votre rôle.';
    if (error instanceof HttpErrorResponse && error.status === 404) return 'La liste de commandes est introuvable.';
    return 'Vos commandes ne peuvent pas être chargées pour le moment.';
  }
}
