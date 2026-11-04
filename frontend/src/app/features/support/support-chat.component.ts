import { DatePipe } from "@angular/common";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from "@angular/core";
import { FormsModule } from "@angular/forms";
import { finalize, switchMap } from "rxjs";

import { RUNTIME_CONFIG } from "../../core/config/runtime-config";

import {
  SUPPORT_STATUS_BADGES,
  SUPPORT_STATUS_LABELS,
  SupportApiService,
  SupportAttachment,
  SupportConversation,
  SupportMessage,
  supportErrorMessage,
} from "./support-api.service";

const IMAGE_MIME_PREFIX = "image/";
const IMAGE_EXTENSIONS = ["jpg", "jpeg", "png", "webp"];
const ALLOWED_EXTENSIONS = [...IMAGE_EXTENSIONS, "pdf"];
const ALLOWED_MIME_TYPES = [
  "image/jpeg",
  "image/png",
  "image/webp",
  "application/pdf",
];

interface AttachmentView {
  readonly attachment: SupportAttachment;
  readonly safeUrl: string | null;
  readonly isImage: boolean;
  readonly failed: boolean;
}

/**
 * Shared conversation chat used by the client support page and the
 * commercial/manager/admin support inbox. Loads messages for the selected
 * conversation and lets the authenticated user send messages with optional
 * attachments (JPEG/PNG/WEBP/PDF only).
 */
@Component({
  selector: "app-support-chat",
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule],
  template: `
    @if (conversation(); as conversation) {
      <header class="chat__header">
        <div class="chat__heading">
          <h2>{{ conversation.subject }}</h2>
          <p>
            <span
              class="support-status"
              [class]="statusClass(conversation.status)"
              >{{ statusLabel(conversation.status) }}</span
            >
            <span
              >Mise à jour :
              {{ conversation.updatedAt | date: "dd/MM/yyyy HH:mm" }}</span
            >
          </p>
        </div>
      </header>

      <div class="chat__messages" #messagesContainer>
        @if (messagesLoading()) {
          <p class="chat__state" role="status">Chargement des messages…</p>
        } @else if (messagesError()) {
          <p class="chat__state chat__state--error" role="alert">
            {{ messagesError() }}
          </p>
        } @else if (messages().length === 0) {
          <p class="chat__state">Aucun message dans cette conversation.</p>
        } @else {
          @for (message of messages(); track message.id) {
            <article
              class="chat__bubble"
              [class.chat__bubble--own]="isOwnMessage(message)"
            >
              <p class="chat__bubble-author">
                {{
                  isOwnMessage(message)
                    ? "Vous"
                    : message.senderRole === "CLIENT"
                      ? "Client"
                      : "Support WWTRADING"
                }}
              </p>
              <p class="chat__bubble-content">{{ message.content }}</p>
              @if (attachmentViewsByMessage[message.id]?.length) {
                <div class="chat__attachments">
                  @for (
                    view of attachmentViewsByMessage[message.id];
                    track view.attachment.id
                  ) {
                    @if (view.isImage && view.safeUrl) {
                      <button
                        type="button"
                        class="chat__attachment chat__attachment--image"
                        (click)="openAttachment(view.attachment.id)"
                      >
                        <img
                          [src]="view.safeUrl"
                          [alt]="view.attachment.originalFilename"
                        />
                      </button>
                    } @else {
                      <a
                        class="chat__attachment chat__attachment--file"
                        [href]="view.safeUrl || '#'"
                        [download]="view.attachment.originalFilename"
                        (click)="!view.safeUrl && $event.preventDefault()"
                      >
                        <span class="material-icons" aria-hidden="true">
                          {{
                            view.attachment.contentType === "application/pdf"
                              ? "picture_as_pdf"
                              : "insert_drive_file"
                          }}
                        </span>
                        <span class="chat__attachment-name">{{
                          view.attachment.originalFilename
                        }}</span>
                        <span class="chat__attachment-size">{{
                          formatSize(view.attachment.size)
                        }}</span>
                      </a>
                    }
                  }
                </div>
              }
              <time class="chat__bubble-date">{{
                message.createdAt | date: "dd/MM/yyyy HH:mm"
              }}</time>
            </article>
          }
        }
      </div>

      <footer class="chat__composer">
        @if (sendError()) {
          <p class="chat__state chat__state--error" role="alert">
            {{ sendError() }}
          </p>
        }
        <div class="chat__composer-row">
          <label
            class="chat__attach"
            aria-label="Joindre un fichier (JPEG, PNG, WEBP ou PDF)"
          >
            <input
              #fileInput
              type="file"
              [accept]="acceptedTypes"
              multiple
              hidden
              (change)="onFilesSelected($event)"
            />
            <span class="material-icons" aria-hidden="true">attach_file</span>
          </label>
          <textarea
            class="chat__input"
            [(ngModel)]="draft"
            (keydown.enter)="onEnter($event)"
            placeholder="Écrire un message…"
            rows="2"
          ></textarea>
          <button
            class="chat__send"
            type="button"
            (click)="sendMessage()"
            [disabled]="sending() || uploading() || !draft.trim()"
          >
            <span class="material-icons" aria-hidden="true">send</span>
          </button>
        </div>
        @if (uploading()) {
          <p class="chat__state" role="status">Envoi des pièces jointes…</p>
        }
      </footer>
    } @else {
      <div class="chat__placeholder">
        <span class="material-icons" aria-hidden="true">support_agent</span>
        <p>Sélectionnez une conversation pour afficher l’échange.</p>
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .chat {
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
      .chat__header {
        padding: 0.75rem 1rem;
        border-bottom: 1px solid #292929;
        background: #1c1c1c;
      }
      .chat__heading h2 {
        margin: 0;
        color: #fff;
        font-size: 1.05rem;
      }
      .chat__heading p {
        margin: 0.25rem 0 0;
        color: #909090;
        font-size: 0.75rem;
        display: flex;
        gap: 0.5rem;
        align-items: center;
        flex-wrap: wrap;
      }
      .chat__messages {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        padding: 1rem;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .chat__state {
        margin: 0;
        color: #999;
        font-size: 0.85rem;
      }
      .chat__state--error {
        color: #ef8a8a;
      }
      .chat__bubble {
        max-width: 75%;
        align-self: flex-start;
        background: #232323;
        border: 1px solid #303030;
        border-radius: 12px;
        padding: 0.6rem 0.8rem;
      }
      .chat__bubble--own {
        align-self: flex-end;
        background: #3a1428;
        border-color: #633048;
      }
      .chat__bubble-author {
        margin: 0 0 0.25rem;
        color: #e91e8c;
        font-size: 0.7rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }
      .chat__bubble-content {
        margin: 0;
        color: #eee;
        font-size: 0.88rem;
        white-space: pre-wrap;
        word-break: break-word;
      }
      .chat__bubble-date {
        display: block;
        margin-top: 0.35rem;
        color: #777;
        font-size: 0.68rem;
      }
      .chat__attachments {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        margin-top: 0.5rem;
      }
      .chat__attachment {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.35rem 0.55rem;
        border: 1px solid #383838;
        border-radius: 8px;
        background: #1c1c1c;
        color: #ddd;
        text-decoration: none;
        cursor: pointer;
      }
      .chat__attachment--image {
        padding: 0;
        overflow: hidden;
      }
      .chat__attachment--image img {
        display: block;
        max-width: 9rem;
        max-height: 9rem;
        object-fit: cover;
      }
      .chat__attachment--file .material-icons {
        font-size: 1.1rem;
        color: #e91e8c;
      }
      .chat__attachment-name {
        max-width: 10rem;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.75rem;
      }
      .chat__attachment-size {
        color: #888;
        font-size: 0.7rem;
      }
      .chat__composer {
        border-top: 1px solid #292929;
        padding: 0.75rem 1rem;
        background: #1c1c1c;
      }
      .chat__composer-row {
        display: flex;
        align-items: flex-end;
        gap: 0.5rem;
      }
      .chat__attach {
        display: grid;
        place-items: center;
        width: 2.6rem;
        height: 2.6rem;
        border: 1px solid #383838;
        border-radius: 8px;
        color: #aaa;
        background: #242424;
        cursor: pointer;
      }
      .chat__attach:hover {
        border-color: #e91e8c;
        color: #e91e8c;
      }
      .chat__input {
        flex: 1;
        min-width: 0;
        resize: none;
        padding: 0.6rem 0.75rem;
        border: 1px solid #343434;
        border-radius: 8px;
        background: #242424;
        color: #ddd;
        font: inherit;
        font-size: 0.88rem;
        box-sizing: border-box;
      }
      .chat__input::placeholder {
        color: #707070;
      }
      .chat__send {
        display: grid;
        place-items: center;
        width: 2.6rem;
        height: 2.6rem;
        border: 0;
        border-radius: 8px;
        background: #af0f50;
        color: #fff;
        cursor: pointer;
      }
      .chat__send:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .chat__placeholder {
        padding: 2rem;
        text-align: center;
        color: #999;
      }
      .chat__placeholder .material-icons {
        font-size: 2.4rem;
        color: #e91e8c;
      }
      .support-status {
        display: inline-flex;
        padding: 0.2rem 0.55rem;
        border-radius: 999px;
        font-size: 0.68rem;
        font-weight: 700;
      }
      .support-status--open {
        background: rgba(46, 204, 113, 0.15);
        color: #2ecc71;
      }
      .support-status--progress {
        background: rgba(241, 196, 15, 0.15);
        color: #f1c40f;
      }
      .support-status--closed {
        background: rgba(149, 165, 166, 0.15);
        color: #95a5a6;
      }
    `,
  ],
})
export class SupportChatComponent {
  readonly conversation = input.required<SupportConversation>();
  /** True when the authenticated user is the client participant of the conversation. */
  readonly clientParticipant = input(true);

  private readonly api = inject(SupportApiService);
  private readonly runtimeConfig = inject(RUNTIME_CONFIG);
  private readonly destroyRef = inject(DestroyRef);
  private readonly messagesContainer =
    viewChild<ElementRef<HTMLDivElement>>("messagesContainer");
  private readonly fileInput =
    viewChild<ElementRef<HTMLInputElement>>("fileInput");
  private readonly blobUrls: string[] = [];

  protected readonly messages = signal<readonly SupportMessage[]>([]);
  protected readonly messagesLoading = signal(false);
  protected readonly messagesError = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly uploading = signal(false);
  protected readonly sendError = signal<string | null>(null);
  protected draft = "";
  protected attachmentViewsByMessage: Record<string, AttachmentView[]> = {};
  protected readonly acceptedTypes = ALLOWED_MIME_TYPES.join(",");

  constructor() {
    this.destroyRef.onDestroy(() =>
      this.blobUrls.forEach((url) => URL.revokeObjectURL(url)),
    );
  }

  /** Reloads the conversation messages — called when the selected conversation changes. */
  loadMessages(): void {
    const conversationId = this.conversation().id;
    this.messagesLoading.set(true);
    this.messagesError.set(null);
    this.api
      .messages(conversationId)
      .pipe(finalize(() => this.messagesLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.messages.set(page.content);
          this.loadAttachments(page.content);
          this.scrollToBottom();
        },
        error: (error: unknown) =>
          this.messagesError.set(
            supportErrorMessage(
              error,
              "Les messages ne peuvent pas être chargés pour le moment.",
            ),
          ),
      });
  }

  protected isOwnMessage(message: SupportMessage): boolean {
    // Message ownership is derived from the server-side sender role, matched
    // against the authenticated user's perspective of the conversation.
    return this.clientParticipant()
      ? message.senderRole === "CLIENT"
      : message.senderRole !== "CLIENT";
  }

  protected onEnter(event: Event): void {
    event.preventDefault();
    void this.sendMessage();
  }

  protected sendMessage(): void {
    const content = this.draft.trim();
    const files = this.pendingFiles;
    if (!content || this.sending() || this.uploading()) return;
    this.sending.set(true);
    this.sendError.set(null);
    const conversationId = this.conversation().id;
    this.api
      .send(conversationId, { content })
      .pipe(
        switchMap((message) => {
          if (files.length === 0) return [message];
          this.uploading.set(true);
          return this.uploadAll(conversationId, message.id, files);
        }),
        finalize(() => {
          this.sending.set(false);
          this.uploading.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.draft = "";
          this.clearFileInput();
          this.loadMessages();
        },
        error: (error: unknown) => {
          this.sendError.set(
            supportErrorMessage(
              error,
              "Le message n’a pas pu être envoyé. Réessayez.",
            ),
          );
        },
      });
  }

  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.pendingFiles = Array.from(input.files ?? []).filter(isAllowedFile);
  }

  protected openAttachment(attachmentId: string): void {
    const view = Object.values(this.attachmentViewsByMessage)
      .flat()
      .find((candidate) => candidate.attachment.id === attachmentId);
    if (view?.safeUrl) {
      window.open(view.safeUrl, "_blank", "noopener");
    }
  }

  protected statusLabel(status: SupportConversation["status"]): string {
    return SUPPORT_STATUS_LABELS[status];
  }

  protected statusClass(status: SupportConversation["status"]): string {
    return SUPPORT_STATUS_BADGES[status];
  }

  protected formatSize(size: number): string {
    if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} Mo`;
    if (size >= 1024) return `${Math.round(size / 1024)} Ko`;
    return `${size} o`;
  }

  private pendingFiles: File[] = [];

  private uploadAll(
    conversationId: string,
    messageId: string,
    files: readonly File[],
  ) {
    let index = 0;
    const uploadNext = (): ReturnType<
      SupportApiService["uploadAttachment"]
    > => {
      const file = files[index++];
      return this.api.uploadAttachment(conversationId, messageId, file);
    };
    const requests = Array.from({ length: files.length }, () => uploadNext);
    // Sequential uploads keep the message attachments ordering stable.
    let chain = uploadNext();
    requests.slice(1).forEach(() => {
      chain = chain.pipe(switchMap(() => uploadNext()));
    });
    return chain;
  }

  private clearFileInput(): void {
    this.pendingFiles = [];
    const input = this.fileInput()?.nativeElement;
    if (input) input.value = "";
  }

  private loadAttachments(messages: readonly SupportMessage[]): void {
    const conversationId = this.conversation().id;
    const views: Record<string, AttachmentView[]> = {};
    messages.forEach((message) => {
      views[message.id] = message.attachments.map((attachment) =>
        this.attachmentView(conversationId, message.id, attachment));
    });
    this.attachmentViewsByMessage = views;
  }

  /**
   * Builds a view for an attachment. Non-image attachments link directly to
   * the authorized content endpoint; image previews are fetched through the
   * API service (which attaches the auth header) and rendered as object URLs
   * revoked on component destroy.
   */
  private attachmentView(
    conversationId: string,
    messageId: string,
    attachment: SupportAttachment,
  ): AttachmentView {
    const isImage = Boolean(attachment.contentType?.startsWith(IMAGE_MIME_PREFIX));
    if (!isImage) {
      return { attachment, safeUrl: this.resolveContentUrl(attachment.contentUrl), isImage: false, failed: false };
    }
    this.api
      .attachmentContent(conversationId, messageId, attachment.id)
      .subscribe({
        next: (blob) => {
          const objectUrl = URL.createObjectURL(blob);
          this.blobUrls.push(objectUrl);
          this.patchView(attachment.id, objectUrl);
        },
        error: () => this.patchView(attachment.id, null),
      });
    return { attachment, safeUrl: null, isImage: true, failed: false };
  }

  private patchView(attachmentId: string, objectUrl: string | null): void {
    Object.keys(this.attachmentViewsByMessage).forEach((messageId) => {
      this.attachmentViewsByMessage[messageId] = this.attachmentViewsByMessage[
        messageId
      ].map((view) =>
        view.attachment.id === attachmentId
          ? { ...view, safeUrl: objectUrl, failed: objectUrl === null }
          : view,
      );
    });
  }

  /** Resolves the backend-relative attachment content path against the API base URL. */
  private resolveContentUrl(contentUrl: string): string {
    const base = this.runtimeConfig.apiBaseUrl.replace(/\/$/, "");
    return contentUrl.startsWith("/") ? `${base}${contentUrl}` : contentUrl;
  }

  private scrollToBottom(): void {
    requestAnimationFrame(() => {
      const container = this.messagesContainer()?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    });
  }
}

function extensionOf(filename: string): string {
  const parts = filename.toLowerCase().split(".");
  return parts.length > 1 ? parts[parts.length - 1] : "";
}

function isAllowedFile(file: File): boolean {
  const extension = extensionOf(file.name);
  return (
    ALLOWED_EXTENSIONS.includes(extension) ||
    ALLOWED_MIME_TYPES.includes(file.type)
  );
}
