import { OrderStatus } from './order.models';

/**
 * Order analytics returned by GET /api/analytics/orders.
 *
 * Three financially distinct figures are reported and must never be conflated:
 *   totalValue        - ORDER VALUE: everything non-cancelled that was ordered.
 *   paidRevenue       - PAID REVENUE: orders whose payment was explicitly confirmed.
 *   recognizedRevenue - RECOGNIZED REVENUE: paid AND reached the COMPLETED state.
 *
 * The domain has no currency column; amounts follow the application's existing
 * "DH" display convention.
 */
export interface RevenueEvolutionPoint {
  readonly date: string;
  readonly paidRevenue: number;
  readonly recognizedRevenue: number;
  readonly orders: number;
}

export interface OrderAnalyticsStatusCount {
  readonly status: OrderStatus;
  readonly count: number;
}

export interface OrderAnalyticsDailyCount {
  readonly date: string;
  readonly count: number;
}

export interface OrderAnalyticsResponse {
  readonly totalOrders: number;
  /** Order value - not revenue. */
  readonly totalValue: number;
  readonly paidRevenue: number;
  readonly recognizedRevenue: number;
  readonly averageOrderValue: number;
  /** Denominator of averageOrderValue: the non-cancelled order count. */
  readonly orderValueOrders: number;
  readonly byStatus: readonly OrderAnalyticsStatusCount[];
  readonly overTime: readonly OrderAnalyticsDailyCount[];
  readonly revenueEvolution: readonly RevenueEvolutionPoint[];
}