import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";

import { RUNTIME_CONFIG } from "../../core/config/runtime-config";

export type StockMovementType = "IN" | "OUT" | "ADJUST" | "RESERVE" | "RELEASE";
export type StockStatus = "OUT_OF_STOCK" | "LOW_STOCK" | "IN_STOCK";

export interface Stock {
  productId: string;
  productReference: string;
  productName: string;
  onHandQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  status: StockStatus;
  lowStockThreshold: number;
  updatedAt: string;
}

export interface StockMovement {
  id: string;
  productId: string;
  type: StockMovementType;
  quantity: number;
  onHandDelta: number | null;
  reference: string | null;
  reason: string | null;
  actor: string;
  createdAt: string;
}

export interface StockPage {
  content: Stock[];
  totalPages: number;
  totalElements: number;
  number: number;
}
export interface StockMovementPage {
  content: StockMovement[];
  totalPages: number;
  totalElements: number;
  number: number;
}

@Injectable({ providedIn: "root" })
export class StockApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, "")}/api/stock`;

  list(page = 0, size = 20): Observable<StockPage> {
    const params = new HttpParams()
      .set("page", page)
      .set("size", size)
      .set("sort", "name,asc");
    return this.http.get<StockPage>(this.resourceUrl, { params });
  }

  get(productId: string): Observable<Stock> {
    return this.http.get<Stock>(`${this.resourceUrl}/${productId}`);
  }

  movements(
    productId: string,
    page = 0,
    size = 20,
  ): Observable<StockMovementPage> {
    const params = new HttpParams()
      .set("page", page)
      .set("size", size)
      .set("sort", "createdAt,desc");
    return this.http.get<StockMovementPage>(
      `${this.resourceUrl}/${productId}/movements`,
      { params },
    );
  }

  operate(
    productId: string,
    type: "in" | "out" | "reserve" | "release",
    body: { quantity: number; reference?: string; reason?: string },
  ): Observable<Stock> {
    return this.http.post<Stock>(
      `${this.resourceUrl}/${productId}/${type}`,
      body,
    );
  }

  adjust(
    productId: string,
    targetQuantity: number,
    reference?: string,
    reason?: string,
  ): Observable<Stock> {
    return this.http.post<Stock>(`${this.resourceUrl}/${productId}/adjust`, {
      targetQuantity,
      reference,
      reason,
    });
  }

  threshold(productId: string, lowStockThreshold: number): Observable<void> {
    return this.http.put<void>(`${this.resourceUrl}/${productId}/threshold`, {
      lowStockThreshold,
    });
  }
}
