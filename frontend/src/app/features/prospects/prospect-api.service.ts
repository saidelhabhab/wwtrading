import { HttpClient, HttpParams } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";

import { RUNTIME_CONFIG } from "../../core/config/runtime-config";

export type ProspectStatus =
  | "NEW"
  | "CONTACTED"
  | "INTERESTED"
  | "QUOTE_SENT"
  | "WON"
  | "LOST";

export interface Prospect {
  readonly id: string;
  readonly companyName: string;
  readonly contactName: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly city: string | null;
  readonly activity: string | null;
  readonly source: string | null;
  readonly status: ProspectStatus;
  readonly notes: string | null;
  readonly assignedCommercialSubject: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface ProspectRequest {
  readonly companyName: string;
  readonly contactName: string;
  readonly phone?: string;
  readonly email?: string;
  readonly city?: string;
  readonly activity?: string;
  readonly source?: string;
  readonly status: ProspectStatus;
  readonly notes?: string;
}

export interface ProspectPage {
  readonly content: readonly Prospect[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly number: number;
  readonly first: boolean;
  readonly last: boolean;
}

@Injectable({ providedIn: "root" })
export class ProspectApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, "")}/api/prospects`;

  list(page = 0, size = 100): Observable<ProspectPage> {
    const params = new HttpParams()
      .set("page", page)
      .set("size", size)
      .set("sort", "updatedAt,desc");
    return this.http.get<ProspectPage>(this.resourceUrl, { params });
  }

  get(id: string): Observable<Prospect> {
    return this.http.get<Prospect>(
      `${this.resourceUrl}/${encodeURIComponent(id)}`,
    );
  }

  create(request: ProspectRequest): Observable<Prospect> {
    return this.http.post<Prospect>(this.resourceUrl, request);
  }

  update(id: string, request: ProspectRequest): Observable<Prospect> {
    return this.http.put<Prospect>(
      `${this.resourceUrl}/${encodeURIComponent(id)}`,
      request,
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(
      `${this.resourceUrl}/${encodeURIComponent(id)}`,
    );
  }
}

export const PROSPECT_STATUS_LABELS: Record<ProspectStatus, string> = {
  NEW: "Nouveau",
  CONTACTED: "Contacté",
  INTERESTED: "Intéressé",
  QUOTE_SENT: "Devis envoyé",
  WON: "Gagné",
  LOST: "Perdu",
};

export const PROSPECT_STATUSES: readonly ProspectStatus[] = [
  "NEW",
  "CONTACTED",
  "INTERESTED",
  "QUOTE_SENT",
  "WON",
  "LOST",
];
