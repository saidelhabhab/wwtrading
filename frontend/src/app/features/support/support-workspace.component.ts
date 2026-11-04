import { DOCUMENT, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, concatMap, finalize, forkJoin, from, map, of, reduce, switchMap, tap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { DashboardLayoutComponent } from '../../shared/components/dashboard-layout/dashboard-layout.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SupportApiService } from './support-api.service';
import {
  SUPPORT_STATUS_LABELS,
  SupportAttachment,
  SupportConversation,
  SupportMessage,
  SupportStatus
} from './support.models';

interface AttachmentView {
  readonly attachment: SupportAttachment;
  readonly blobUrl: string | null;
  readonly image: boolean;
  readonly failed: boolean;
}

@Component({
  selector: 'wwt-support-workspace',
  standalone: true,
  imports: [DatePipe, FormsModule, DashboardLayoutComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './support-workspace.component.html',
  styleUrl: './support-workspace.component.scss'
})
export class SupportWorkspaceComponent implements OnInit, OnDestroy {
  private readonly api = inject(SupportApiService);
  private readonly auth = inject(AuthService);
  private readonly document = inject(DOCUMENT);

  protected readonly isAgent = computed(() => this.auth.roles().some((role) =>
    role === 'ADMIN' || role === 'MANAGER' || role === 'COMMERCIAL'));
  protected readonly pageTitle = computed(() => this.isAgent() ? 'Support clients' : 'Support');
  protected readonly pageSubtitle = computed(() => this.isAgent()
    ? 'Conversations d’assistance qui vous sont accessibles'
    : 'Assistance WWTRADING');
  protected readonly breadcrumb = computed(() => this.isAgent()
    ? 'Espace professionnel / Support'
    : 'Espace client / Support');

  protected readonly conversations = signal<readonly SupportConversation[]>([]);
  protected readonly selectedConversation = signal<SupportConversation | null>(null);
  protected readonly messages = signal<readonly SupportMessage[]>([]);
  protected readonly attachmentViews = signal<Readonly<Record<string, AttachmentView>>>({});
  protected readonly selectedPreview = signal<AttachmentView | null>(null);
  protected readonly conversationsLoading = signal(true);
  protected readonly messagesLoading = signal(false);
  protected readonly conversationsError = signal<string | null>(null);
  protected readonly messagesError = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly sendError = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected readonly createDialogOpen = signal(false);
  protected readonly selectedFiles = signal<readonly File[]>([]);

  protected newSubject = '';
  protected newMessage = '';
  protected messageDraft = '';

  ngOnInit(): void { this.loadConversations(); }

  ngOnDestroy(): void { this.revokeAttachmentUrls(); }

  protected loadConversations(selectId?: string): void {
    this.conversationsLoading.set(true);
    this.conversationsError.set(null);
    const request = this.isAgent()
      ? this.api.getAgentConversations(0, 100)
      : this.api.getMyConversations(0, 100);
    request.pipe(finalize(() => this.conversationsLoading.set(false))).subscribe({
      next: (page) => {
        this.conversations.set(page.content);
        const target = selectId
          ? page.content.find((conversation) => conversation.id === selectId)
          : this.selectedConversation()
            ? page.content.find((conversation) => conversation.id === this.selectedConversation()!.id)
            : null;
        if (target) this.selectConversation(target);
      },
      error: (failure: unknown) => this.conversationsError.set(this.errorMessage(failure,
        'Les conversations ne peuvent pas être chargées.'))
    });
  }

  protected selectConversation(conversation: SupportConversation): void {
    this.selectedConversation.set(conversation);
    this.messagesLoading.set(true);
    this.messagesError.set(null);
    this.sendError.set(null);
    this.revokeAttachmentUrls();
    forkJoin({
      conversation: this.api.getConversation(conversation.id),
      messages: this.api.getMessages(conversation.id, 0, 100)
    }).subscribe({
      next: ({ conversation: current, messages }) => {
        this.selectedConversation.set(current);
        this.messages.set(messages.content);
        this.loadAttachmentContent(current.id, messages.content);
      },
      error: (failure: unknown) => {
        this.messagesLoading.set(false);
        this.messagesError.set(this.errorMessage(failure, 'La conversation ne peut pas être chargée.'));
      }
    });
  }

  protected closeConversation(): void {
    this.selectedConversation.set(null);
    this.messages.set([]);
    this.messagesError.set(null);
    this.revokeAttachmentUrls();
  }

  protected openCreateDialog(): void {
    if (this.isAgent()) return;
    this.newSubject = '';
    this.newMessage = '';
    this.createError.set(null);
    this.createDialogOpen.set(true);
  }

  protected closeCreateDialog(): void {
    if (!this.creating()) this.createDialogOpen.set(false);
  }

  protected createConversation(): void {
    const subject = this.newSubject.trim();
    const message = this.newMessage.trim();
    if (!subject || !message || this.creating()) return;
    this.creating.set(true);
    this.createError.set(null);
    this.api.createConversation({ subject, message }).pipe(
      finalize(() => this.creating.set(false))
    ).subscribe({
      next: (conversation) => {
        this.createDialogOpen.set(false);
        this.loadConversations(conversation.id);
      },
      error: (failure: unknown) => this.createError.set(this.errorMessage(failure,
        'La conversation ne peut pas être créée.'))
    });
  }

  protected sendMessage(): void {
    const conversation = this.selectedConversation();
    const content = this.messageDraft.trim();
    if (!conversation || conversation.status === 'CLOSED' || !content || this.sending()) return;
    const files = [...this.selectedFiles()];
    let messageCreated = false;
    this.sending.set(true);
    this.sendError.set(null);
    this.api.sendMessage(conversation.id, { content }).pipe(
      tap(() => { messageCreated = true; }),
      switchMap((created) => files.length === 0
        ? of(created)
        : from(files).pipe(
            concatMap((file) => this.api.uploadAttachment(conversation.id, created.id, file)),
            reduce(() => created, created)
          )),
      finalize(() => this.sending.set(false))
    ).subscribe({
      next: () => {
        this.messageDraft = '';
        this.selectedFiles.set([]);
        this.loadConversations(conversation.id);
      },
      error: (failure: unknown) => {
        this.sendError.set(messageCreated
          ? 'Le message a été envoyé, mais une pièce jointe n’a pas pu être transférée.'
          : this.errorMessage(failure, 'Le message ne peut pas être envoyé.'));
        if (messageCreated) this.selectConversation(conversation);
      }
    });
  }

  protected chooseFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    const allowed = new Set(['image/jpeg', 'image/png', 'image/webp', 'application/pdf']);
    const invalid = files.find((file) => !allowed.has(file.type) || file.size <= 0 || file.size > 10 * 1024 * 1024);
    if (invalid) {
      this.sendError.set('Formats acceptés : JPEG, PNG, WEBP ou PDF, 10 Mo maximum par fichier.');
      input.value = '';
      return;
    }
    this.sendError.set(null);
    this.selectedFiles.set(files);
  }

  protected removeFile(index: number): void {
    this.selectedFiles.update((files) => files.filter((_, fileIndex) => fileIndex !== index));
  }

  protected isOwnMessage(message: SupportMessage): boolean {
    return message.senderKeycloakSubject === this.auth.user()?.subject;
  }

  protected senderLabel(message: SupportMessage): string {
    if (this.isOwnMessage(message)) return 'Vous';
    return message.senderRole === 'CLIENT' ? 'Client' : 'Équipe WWTRADING';
  }

  protected statusLabel(status: SupportStatus): string { return SUPPORT_STATUS_LABELS[status]; }
  protected shortIdentity(subject: string | null): string {
    return subject ? subject.slice(0, 8).toUpperCase() : 'Non assigné';
  }
  protected fileSize(size: number): string {
    return size < 1024 * 1024 ? `${Math.max(1, Math.round(size / 1024))} Ko` : `${(size / 1024 / 1024).toFixed(1)} Mo`;
  }
  protected attachmentView(id: string): AttachmentView | null { return this.attachmentViews()[id] ?? null; }
  protected openPreview(view: AttachmentView): void {
    if (view.image && view.blobUrl && !view.failed) {
      this.selectedPreview.set(view);
      this.document.body.style.overflow = 'hidden';
    }
  }
  protected closePreview(): void {
    this.selectedPreview.set(null);
    this.document.body.style.overflow = '';
  }

  private loadAttachmentContent(conversationId: string, messages: readonly SupportMessage[]): void {
    const all = messages.flatMap((message) => message.attachments);
    if (all.length === 0) {
      this.attachmentViews.set({});
      this.messagesLoading.set(false);
      return;
    }
    forkJoin(all.map((attachment) => this.api.getAttachmentContent(
      conversationId, attachment.messageId, attachment.id
    ).pipe(
      map((blob): AttachmentView => ({
        attachment,
        blobUrl: URL.createObjectURL(blob),
        image: attachment.contentType.startsWith('image/'),
        failed: false
      })),
      catchError(() => of<AttachmentView>({ attachment, blobUrl: null,
        image: attachment.contentType.startsWith('image/'), failed: true }))
    ))).pipe(finalize(() => this.messagesLoading.set(false))).subscribe((views) => {
      this.attachmentViews.set(Object.fromEntries(views.map((view) => [view.attachment.id, view])));
    });
  }

  private revokeAttachmentUrls(): void {
    Object.values(this.attachmentViews()).forEach((view) => {
      if (view.blobUrl) URL.revokeObjectURL(view.blobUrl);
    });
    this.attachmentViews.set({});
    this.closePreview();
  }

  private errorMessage(failure: unknown, fallback: string): string {
    if (failure instanceof HttpErrorResponse) {
      if (failure.status === 401) return 'Votre session a expiré. Reconnectez-vous.';
      if (failure.status === 403) return 'Vous n’êtes pas autorisé à accéder à cette conversation.';
    }
    return fallback;
  }
}
