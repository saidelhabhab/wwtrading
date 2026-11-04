import { CommonModule, DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatIconModule } from "@angular/material/icon";
import { catchError, finalize, of } from "rxjs";

import { DashboardLayoutComponent } from "../../shared/components/dashboard-layout/dashboard-layout.component";
import { PageHeaderComponent } from "../../shared/components/page-header/page-header.component";
import {
  PROSPECT_STATUS_LABELS,
  PROSPECT_STATUSES,
  Prospect,
  ProspectApiService,
  ProspectRequest,
  ProspectStatus,
} from "./prospect-api.service";

const EMPTY_FORM: ProspectRequest = {
  companyName: "",
  contactName: "",
  phone: "",
  email: "",
  city: "",
  activity: "",
  source: "",
  status: "NEW",
  notes: "",
};

@Component({
  selector: "wwt-commercial-prospects",
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    MatIconModule,
    DashboardLayoutComponent,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <wwt-dashboard-layout title="Prospects">
      <wwt-page-header
        title="Prospects"
        subtitle="Gérez votre portefeuille de prospects commerciaux."
        breadcrumb="Espace commercial / Prospects"
      ></wwt-page-header>
      <main class="prospects-page" aria-labelledby="prospects-title">
        <section class="intro-card">
          <div>
            <span class="eyebrow">Prospection commerciale</span>
            <h1 id="prospects-title">Vos prospects</h1>
            <p>
              Centralisez les contacts qui peuvent devenir vos prochains
              clients.
            </p>
          </div>
          <button type="button" class="btn btn--primary" (click)="openCreate()">
            <mat-icon>add</mat-icon> Nouveau prospect
          </button>
        </section>

        <section class="panel" aria-labelledby="prospect-list-title">
          <header class="panel-header">
            <div>
              <span class="eyebrow">Portefeuille actif</span>
              <h2 id="prospect-list-title">Liste des prospects</h2>
            </div>
            <span class="count"
              >{{ totalElements() }} résultat{{
                totalElements() > 1 ? "s" : ""
              }}</span
            >
          </header>
          <div class="toolbar">
            <label class="search"
              ><mat-icon>search</mat-icon
              ><input
                type="search"
                [value]="search()"
                (input)="search.set($any($event.target).value)"
                placeholder="Rechercher une société, un contact, une ville…"
                aria-label="Rechercher des prospects"
            /></label>
            <label class="status-filter"
              ><span>Statut</span
              ><select
                [value]="statusFilter()"
                (change)="
                  statusFilter.set($any($event.target).value); applyFilters()
                "
              >
                <option value="ALL">Tous les statuts</option>
                @for (status of statuses; track status) {
                  <option [value]="status">{{ statusLabels[status] }}</option>
                }
              </select></label
            >
          </div>

          @if (loading()) {
            <div class="state" role="status">
              <mat-icon>hourglass_empty</mat-icon
              ><strong>Chargement des prospects…</strong>
            </div>
          } @else if (error()) {
            <div class="state state--error" role="alert">
              <mat-icon>error_outline</mat-icon
              ><strong>Impossible de charger les prospects</strong>
              <p>{{ error() }}</p>
              <button type="button" class="btn" (click)="load()">
                <mat-icon>refresh</mat-icon> Réessayer
              </button>
            </div>
          } @else if (filteredProspects().length === 0) {
            <div class="state">
              <mat-icon>person_search</mat-icon
              ><strong>{{
                search() || statusFilter() !== "ALL"
                  ? "Aucun prospect ne correspond à vos filtres"
                  : "Aucun prospect enregistré"
              }}</strong>
              <p>
                {{
                  search() || statusFilter() !== "ALL"
                    ? "Modifiez vos critères de recherche."
                    : "Créez votre premier prospect pour commencer."
                }}
              </p>
            </div>
          } @else {
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Société</th>
                    <th>Contact</th>
                    <th>Téléphone</th>
                    <th>Email</th>
                    <th>Ville / Activité</th>
                    <th>Statut</th>
                    <th>Mis à jour</th>
                    <th class="actions-col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (prospect of filteredProspects(); track prospect.id) {
                    <tr>
                      <td data-label="Société">
                        <strong>{{ prospect.companyName }}</strong>
                      </td>
                      <td data-label="Contact">{{ prospect.contactName }}</td>
                      <td data-label="Téléphone">
                        @if (prospect.phone) {
                          <a [href]="'tel:' + prospect.phone">{{
                            prospect.phone
                          }}</a>
                        } @else {
                          <span class="muted">—</span>
                        }
                      </td>
                      <td data-label="Email">
                        @if (prospect.email) {
                          <a [href]="'mailto:' + prospect.email">{{
                            prospect.email
                          }}</a>
                        } @else {
                          <span class="muted">—</span>
                        }
                      </td>
                      <td data-label="Ville / Activité">
                        <span>{{ prospect.city || "—" }}</span
                        ><small>{{ prospect.activity || "—" }}</small>
                      </td>
                      <td data-label="Statut">
                        <span
                          class="status"
                          [class]="statusClass(prospect.status)"
                          >{{ statusLabels[prospect.status] }}</span
                        >
                      </td>
                      <td data-label="Mis à jour">
                        {{ prospect.updatedAt | date: "dd/MM/yyyy HH:mm" }}
                      </td>
                      <td data-label="Actions" class="actions">
                        <button
                          type="button"
                          title="Voir les détails"
                          (click)="openDetails(prospect)"
                        >
                          <mat-icon>visibility</mat-icon></button
                        ><button
                          type="button"
                          title="Modifier"
                          (click)="openEdit(prospect)"
                        >
                          <mat-icon>edit</mat-icon></button
                        ><button
                          type="button"
                          title="Supprimer"
                          (click)="remove(prospect)"
                          [disabled]="deletingId() === prospect.id"
                        >
                          <mat-icon>delete_outline</mat-icon>
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (pageTotal() > 1) {
              <footer class="pagination">
                <button
                  type="button"
                  class="btn"
                  (click)="goPage(page() - 1)"
                  [disabled]="page() === 0 || loading()"
                >
                  <mat-icon>chevron_left</mat-icon> Précédent</button
                ><span>Page {{ page() + 1 }} sur {{ pageTotal() }}</span
                ><button
                  type="button"
                  class="btn"
                  (click)="goPage(page() + 1)"
                  [disabled]="page() + 1 >= pageTotal() || loading()"
                >
                  Suivant <mat-icon>chevron_right</mat-icon>
                </button>
              </footer>
            }
          }
        </section>
      </main>

      @if (dialogMode()) {
        <div class="overlay" role="presentation" (click)="closeDialog()">
          <section
            class="dialog"
            role="dialog"
            aria-modal="true"
            (click)="$event.stopPropagation()"
          >
            <header>
              <div>
                <span class="eyebrow">{{
                  dialogMode() === "details"
                    ? "Fiche prospect"
                    : "Prospection commerciale"
                }}</span>
                <h2>
                  {{
                    dialogMode() === "create"
                      ? "Nouveau prospect"
                      : dialogMode() === "edit"
                        ? "Modifier le prospect"
                        : selected()?.companyName
                  }}
                </h2>
              </div>
              <button
                type="button"
                class="icon-button"
                (click)="closeDialog()"
                aria-label="Fermer"
              >
                <mat-icon>close</mat-icon>
              </button>
            </header>
            @if (dialogMode() === "details" && selected(); as prospect) {
              <div class="details">
                <div>
                  <span>Société</span
                  ><strong>{{ prospect.companyName }}</strong>
                </div>
                <div>
                  <span>Contact</span
                  ><strong>{{ prospect.contactName }}</strong>
                </div>
                <div>
                  <span>Téléphone</span
                  ><strong>{{ prospect.phone || "—" }}</strong>
                </div>
                <div>
                  <span>Email</span><strong>{{ prospect.email || "—" }}</strong>
                </div>
                <div>
                  <span>Ville</span><strong>{{ prospect.city || "—" }}</strong>
                </div>
                <div>
                  <span>Activité</span
                  ><strong>{{ prospect.activity || "—" }}</strong>
                </div>
                <div>
                  <span>Source</span
                  ><strong>{{ prospect.source || "—" }}</strong>
                </div>
                <div>
                  <span>Statut</span
                  ><strong>{{ statusLabels[prospect.status] }}</strong>
                </div>
                <div class="wide">
                  <span>Notes</span>
                  <p>{{ prospect.notes || "Aucune note." }}</p>
                </div>
              </div>
            } @else {
              <form (ngSubmit)="save()">
                <div class="form-grid">
                  <label
                    ><span>Société *</span
                    ><input
                      name="companyName"
                      [(ngModel)]="form.companyName"
                      required
                      maxlength="200" /></label
                  ><label
                    ><span>Contact *</span
                    ><input
                      name="contactName"
                      [(ngModel)]="form.contactName"
                      required
                      maxlength="120" /></label
                  ><label
                    ><span>Téléphone</span
                    ><input
                      name="phone"
                      [(ngModel)]="form.phone"
                      maxlength="30" /></label
                  ><label
                    ><span>Email</span
                    ><input
                      name="email"
                      type="email"
                      [(ngModel)]="form.email"
                      maxlength="160" /></label
                  ><label
                    ><span>Ville</span
                    ><input
                      name="city"
                      [(ngModel)]="form.city"
                      maxlength="100" /></label
                  ><label
                    ><span>Activité</span
                    ><input
                      name="activity"
                      [(ngModel)]="form.activity"
                      maxlength="160" /></label
                  ><label
                    ><span>Source</span
                    ><input
                      name="source"
                      [(ngModel)]="form.source"
                      maxlength="80" /></label
                  ><label
                    ><span>Statut</span
                    ><select name="status" [(ngModel)]="form.status">
                      @for (status of statuses; track status) {
                        <option [ngValue]="status">
                          {{ statusLabels[status] }}
                        </option>
                      }
                    </select></label
                  ><label class="wide"
                    ><span>Notes</span
                    ><textarea
                      name="notes"
                      [(ngModel)]="form.notes"
                      maxlength="10000"
                      rows="4"
                    ></textarea>
                  </label>
                </div>
                @if (formError()) {
                  <p class="form-error" role="alert">{{ formError() }}</p>
                }
                <footer class="dialog-actions">
                  <button type="button" class="btn" (click)="closeDialog()">
                    Annuler</button
                  ><button
                    type="submit"
                    class="btn btn--primary"
                    [disabled]="saving()"
                  >
                    {{ saving() ? "Enregistrement…" : "Enregistrer" }}
                  </button>
                </footer>
              </form>
            }
          </section>
        </div>
      }

      @if (confirmTarget(); as prospect) {
        <div class="overlay" role="presentation" (click)="cancelRemove()">
          <section
            class="dialog dialog--confirm"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="prospect-confirm-title"
            (click)="$event.stopPropagation()"
          >
            <header>
              <div>
                <span class="eyebrow">Suppression</span>
                <h2 id="prospect-confirm-title">Supprimer ce prospect ?</h2>
              </div>
              <button
                type="button"
                class="icon-button"
                (click)="cancelRemove()"
                aria-label="Fermer"
              >
                <mat-icon>close</mat-icon>
              </button>
            </header>
            <p class="confirm-copy">
              Le prospect « {{ prospect.companyName }} » sera définitivement
              supprimé. Cette action est irréversible.
            </p>
            <footer class="dialog-actions">
              <button
                type="button"
                class="btn"
                (click)="cancelRemove()"
                [disabled]="deletingId() === prospect.id"
              >
                Annuler</button
              ><button
                type="button"
                class="btn btn--danger"
                (click)="confirmRemove()"
                [disabled]="deletingId() === prospect.id"
              >
                {{ deletingId() === prospect.id ? "Suppression…" : "Supprimer" }}
              </button>
            </footer>
          </section>
        </div>
      }
    </wwt-dashboard-layout>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .prospects-page {
        width: 100%;
        max-width: var(--dashboard-max-width);
        margin-inline: auto;
        padding-block: var(--dashboard-page-padding-block);
        padding-inline: var(--dashboard-page-padding-inline);
      }
      .intro-card {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 1rem 1.5rem;
        padding: clamp(1rem, 3vw, 1.5rem);
        margin-bottom: 1.25rem;
        border: 1px solid var(--dashboard-border);
        border-radius: var(--dashboard-radius-lg);
        background: #fff;
        box-shadow: 0 0.8rem 2rem rgb(26 26 24 / 5%);
      }
      .intro-card > div { min-width: 0; }
      .eyebrow {
        color: #af0f50;
        font-size: 0.72rem;
        font-weight: 800;
        letter-spacing: 0.1em;
        text-transform: uppercase;
      }
      h1,
      h2,
      p {
        margin: 0;
      }
      h1 {
        margin-top: 0.35rem;
        color: #1a1a18;
        font-size: clamp(1.4rem, 1.1rem + 1.5vw, 2.2rem);
        line-height: 1.15;
        overflow-wrap: anywhere;
      }
      h2 {
        margin-top: 0.25rem;
        color: #1a1a18;
        font-size: 1.1rem;
      }
      .intro-card p {
        margin-top: 0.45rem;
        color: #6b6865;
      }
      .btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.35rem;
        min-height: 44px;
        padding: 0.6rem 0.85rem;
        border: 1px solid #d3ced0;
        border-radius: 0.4rem;
        background: #fff;
        color: #373436;
        font: inherit;
        font-weight: 700;
        cursor: pointer;
      }
      .btn:hover:not(:disabled) {
        border-color: #af0f50;
        color: #8f123f;
      }
      .btn--primary {
        border-color: #af0f50;
        background: #af0f50;
        color: #fff;
      }
      .btn--primary:hover:not(:disabled) {
        background: #8f123f;
        color: #fff;
      }
      .btn--danger {
        border-color: #b32d3e;
        color: #b32d3e;
      }
      .btn--danger:hover:not(:disabled) {
        border-color: #b32d3e;
        background: #b32d3e;
        color: #fff;
      }
      .btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .panel {
        overflow: hidden;
        border: 1px solid #dfdcdd;
        background: #fff;
        box-shadow: 0 1rem 2.5rem rgb(26 26 24 / 6%);
      }
      .panel-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1rem 1.25rem;
        border-bottom: 1px solid #eee;
      }
      .count {
        color: #777;
        font-size: 0.82rem;
      }
      .toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: end;
        gap: 1rem;
        padding: 1rem 1.25rem;
        background: #faf9f8;
        border-bottom: 1px solid #eee;
      }
      .search {
        display: flex;
        flex: 1 1 280px;
        align-items: center;
        gap: 0.45rem;
        padding: 0.55rem 0.7rem;
        border: 1px solid #d7d3d4;
        background: #fff;
      }
      .search input {
        width: 100%;
        border: 0;
        outline: 0;
        font: inherit;
      }
      .status-filter {
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        min-width: 180px;
        color: #666;
        font-size: 0.75rem;
        font-weight: 700;
      }
      select,
      input,
      textarea {
        box-sizing: border-box;
        width: 100%;
        padding: 0.58rem 0.65rem;
        border: 1px solid #d7d3d4;
        border-radius: 0.25rem;
        background: #fff;
        color: #252324;
        font: inherit;
      }
      table {
        width: 100%;
        min-width: 1050px;
        border-collapse: collapse;
      }
      th,
      td {
        padding: 0.8rem 0.9rem;
        border-bottom: 1px solid #eee;
        text-align: left;
        vertical-align: middle;
      }
      th {
        background: #282624;
        color: #fff;
        font-size: 0.7rem;
        letter-spacing: 0.05em;
        text-transform: uppercase;
      }
      td {
        color: #504d4b;
        font-size: 0.88rem;
      }
      td strong {
        color: #1a1a18;
      }
      td small {
        display: block;
        margin-top: 0.2rem;
        color: #8b8784;
      }
      td a {
        color: #8f123f;
      }
      /* Wide table: scrolls horizontally until the card layout takes over. */
      .table-wrap {
        width: 100%;
        max-width: 100%;
        overflow-x: auto;
        overscroll-behavior-x: contain;
      }
      .muted {
        color: #999;
      }
      .status {
        display: inline-flex;
        width: max-content;
        max-width: 100%;
        padding: 0.28rem 0.55rem;
        border-radius: 99px;
        font-size: 0.7rem;
        font-weight: 800;
      }
      .status--new {
        background: #edf0f2;
        color: #4d5960;
      }
      .status--contacted {
        background: #e7f0fa;
        color: #2e6090;
      }
      .status--interested {
        background: #fff0d8;
        color: #996000;
      }
      .status--quote {
        background: #f5e3ed;
        color: #8f123f;
      }
      .status--won {
        background: #e0f3e8;
        color: #24724a;
      }
      .status--lost {
        background: #f7e4e5;
        color: #9a3338;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.2rem;
      }
      .actions button,
      .icon-button {
        display: grid;
        place-items: center;
        min-width: 44px;
        min-height: 44px;
        padding: 0.35rem;
        border: 0;
        border-radius: 0.4rem;
        background: transparent;
        color: #706c69;
        cursor: pointer;
      }
      .actions button:hover,
      .icon-button:hover {
        color: #af0f50;
        background: #faf2f5;
      }
      .pagination {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: center;
        gap: 0.5rem 1rem;
        padding: 1rem;
        color: #666;
      }
      .state {
        display: grid;
        justify-items: center;
        gap: 0.5rem;
        min-height: clamp(9rem, 22vh, 14rem);
        align-content: center;
        padding: clamp(1.5rem, 5vw, 4rem) 1rem;
        color: #777;
        text-align: center;
      }
      .state mat-icon {
        color: #af0f50;
        font-size: 2rem;
      }
      .state p {
        max-width: 30rem;
      }
      .state--error {
        color: #8f123f;
      }
      .overlay {
        position: fixed;
        inset: 0;
        z-index: 20;
        display: grid;
        place-items: center;
        padding: 1rem;
        background: rgb(26 26 24 / 48%);
      }
      /*
       * Dialog shell: fits the viewport, scrolls internally, keeps its action
       * row reachable on 360px phones (uses dvh so mobile browser chrome does
       * not push the buttons off-screen).
       */
      .dialog {
        width: min(720px, 100%);
        max-width: calc(100vw - 1.5rem);
        max-height: calc(100dvh - 1.5rem);
        overflow: auto;
        overscroll-behavior: contain;
        padding: clamp(1rem, 2.5vw, 1.25rem);
        border-radius: 0.5rem;
        background: #fff;
        box-shadow: 0 1.5rem 4rem rgb(0 0 0 / 25%);
      }
      .dialog > header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 1rem;
        margin-bottom: 1.25rem;
      }
      .dialog > header h2 { min-width: 0; overflow-wrap: anywhere; }
      .dialog--confirm {
        width: min(460px, 100%);
      }
      .confirm-copy {
        color: #4f4b49;
        line-height: 1.55;
      }
      /* Two form columns from tablet up; a single column on phones. */
      .form-grid,
      .details {
        display: grid;
        grid-template-columns: 1fr;
        gap: 0.85rem;
      }
      @media (min-width: 641px) {
        .form-grid,
        .details {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .wide {
          grid-column: 1/-1;
        }
      }
      .form-grid label,
      .details > div {
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        min-width: 0;
      }
      .form-grid label > span,
      .details span {
        color: #6e6967;
        font-size: 0.75rem;
        font-weight: 700;
      }
      .details strong {
        color: #1a1a18;
        overflow-wrap: anywhere;
      }
      .details p {
        color: #4f4b49;
        line-height: 1.5;
        overflow-wrap: anywhere;
      }
      .wide {
        grid-column: 1/-1;
      }
      .dialog-actions {
        display: flex;
        flex-wrap: wrap;
        justify-content: flex-end;
        gap: 0.6rem;
        margin-top: 1.25rem;
      }
      .form-error {
        margin-top: 1rem;
        color: #a83243;
        font-size: 0.85rem;
        overflow-wrap: anywhere;
      }

      /*
       * Tablet and below: the wide prospect table becomes one card per prospect
       * so no business column is hidden and the page never scrolls sideways.
       */
      @media (max-width: 900px) {
        .panel-header {
          align-items: flex-start;
          flex-direction: column;
          gap: 0.4rem;
        }
        .table-wrap {
          overflow: visible;
        }
        table,
        thead,
        tbody,
        tr,
        th,
        td {
          display: block;
        }
        table {
          min-width: 0;
        }
        thead {
          position: absolute;
          width: 1px;
          height: 1px;
          overflow: hidden;
          clip: rect(0, 0, 0, 0);
        }
        tr {
          padding: 0.7rem;
          border-bottom: 1px solid #ddd;
        }
        td {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 1rem;
          padding: 0.35rem 0.2rem;
          border: 0;
          text-align: right;
        }
        td > * { min-width: 0; }
        td::before {
          flex: 0 0 auto;
          content: attr(data-label);
          color: #777;
          font-size: 0.72rem;
          font-weight: 800;
          text-align: left;
        }
        .actions {
          justify-content: flex-end;
        }
      }

      /* Phones: the intro card stacks and the primary action goes full width. */
      @media (max-width: 700px) {
        .intro-card {
          align-items: stretch;
          flex-direction: column;
        }
        .btn--primary {
          width: 100%;
        }
        .dialog-actions {
          flex-direction: column-reverse;
          align-items: stretch;
        }
        .dialog-actions > * {
          width: 100%;
          justify-content: center;
        }
      }

      @media (min-width: 1921px) {
        .intro-card {
          padding: 2rem;
        }
      }
    `,
  ],
})
export class CommercialProspectsComponent implements OnInit {
  private readonly api = inject(ProspectApiService);
  protected readonly prospects = signal<readonly Prospect[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly search = signal("");
  protected readonly statusFilter = signal<ProspectStatus | "ALL">("ALL");
  protected readonly page = signal(0);
  protected readonly pageTotal = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly dialogMode = signal<"create" | "edit" | "details" | null>(
    null,
  );
  protected readonly selected = signal<Prospect | null>(null);
  protected readonly saving = signal(false);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly confirmTarget = signal<Prospect | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected form: ProspectRequest = { ...EMPTY_FORM };
  protected readonly statuses = PROSPECT_STATUSES;
  protected readonly statusLabels = PROSPECT_STATUS_LABELS;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.page())
      .pipe(
        finalize(() => this.loading.set(false)),
        catchError(() => {
          this.error.set("Vérifiez votre accès ou réessayez plus tard.");
          return of(null);
        }),
      )
      .subscribe((page) => {
        if (!page) return;
        this.prospects.set(page.content);
        this.pageTotal.set(page.totalPages);
        this.totalElements.set(page.totalElements);
      });
  }

  protected filteredProspects(): readonly Prospect[] {
    const query = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    return this.prospects().filter(
      (prospect) =>
        (status === "ALL" || prospect.status === status) &&
        (!query ||
          [
            prospect.companyName,
            prospect.contactName,
            prospect.phone,
            prospect.email,
            prospect.city,
            prospect.activity,
          ].some((value) => value?.toLowerCase().includes(query))),
    );
  }

  protected applyFilters(): void {
    this.page.set(0);
    this.load();
  }
  protected goPage(page: number): void {
    if (page >= 0 && page < this.pageTotal()) {
      this.page.set(page);
      this.load();
    }
  }
  protected openCreate(): void {
    this.selected.set(null);
    this.form = { ...EMPTY_FORM };
    this.formError.set(null);
    this.dialogMode.set("create");
  }
  protected openEdit(prospect: Prospect): void {
    this.selected.set(prospect);
    this.form = this.requestFrom(prospect);
    this.formError.set(null);
    this.dialogMode.set("edit");
  }
  protected openDetails(prospect: Prospect): void {
    this.selected.set(prospect);
    this.dialogMode.set("details");
  }
  protected closeDialog(): void {
    if (!this.saving()) this.dialogMode.set(null);
  }

  protected save(): void {
    if (
      !this.form.companyName.trim() ||
      !this.form.contactName.trim() ||
      this.saving()
    )
      return;
    this.saving.set(true);
    this.formError.set(null);
    const request = this.cleanRequest(this.form);
    const selected = this.selected();
    const operation = selected
      ? this.api.update(selected.id, request)
      : this.api.create(request);
    operation.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.dialogMode.set(null);
        this.load();
      },
      error: () =>
        this.formError.set("Le prospect n’a pas pu être enregistré."),
    });
  }

  protected remove(prospect: Prospect): void {
    if (this.deletingId()) return;
    this.dialogMode.set(null);
    this.selected.set(null);
    this.confirmTarget.set(prospect);
  }

  protected cancelRemove(): void {
    if (this.deletingId()) return;
    this.confirmTarget.set(null);
  }

  protected confirmRemove(): void {
    const prospect = this.confirmTarget();
    if (!prospect || this.deletingId()) return;
    this.deletingId.set(prospect.id);
    this.api
      .delete(prospect.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.confirmTarget.set(null);
          if (this.prospects().length === 1 && this.page() > 0)
            this.page.update((page) => page - 1);
          this.load();
        },
        error: () => {
          this.confirmTarget.set(null);
          this.error.set("Le prospect n’a pas pu être supprimé.");
        },
      });
  }

  protected statusClass(status: ProspectStatus): string {
    return `status--${status.toLowerCase().replace("_sent", "")}`;
  }
  private requestFrom(prospect: Prospect): ProspectRequest {
    return {
      companyName: prospect.companyName,
      contactName: prospect.contactName,
      phone: prospect.phone ?? "",
      email: prospect.email ?? "",
      city: prospect.city ?? "",
      activity: prospect.activity ?? "",
      source: prospect.source ?? "",
      status: prospect.status,
      notes: prospect.notes ?? "",
    };
  }
 
  private cleanRequest(form: ProspectRequest): ProspectRequest {
    return Object.fromEntries(
      Object.entries(form).map(([key, value]) => {
        if (typeof value !== 'string') {
          return [key, value];
        }

        const trimmed = value.trim();

        return [key, trimmed === '' ? null : trimmed];
      }),
    ) as ProspectRequest;
  }


}
