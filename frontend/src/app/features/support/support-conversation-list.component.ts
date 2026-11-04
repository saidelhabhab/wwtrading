import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { SUPPORT_STATUS_BADGES, SUPPORT_STATUS_LABELS, SupportConversation } from './support-api.service';

/** Renders a single conversation row in a support conversation list. */
@Component({
  selector: 'app-support-conversation-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    <ul class="support-conversation-list">
      @for (conversation of conversations(); track conversation.id) {
        <li>
          <button
            type="button"
            class="support-conversation"
            [class.support-conversation--active]="conversation.id === selectedId()"
            (click)="conversationSelected.emit(conversation.id)"
          >
            <span class="support-conversation__subject">{{ conversation.subject }}</span>
            <span class="support-conversation__meta">
              <span class="support-status" [class]="statusClass(conversation.status)">
                {{ statusLabel(conversation.status) }}
              </span>
              <span class="support-conversation__date">{{ conversation.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</span>
            </span>
          </button>
        </li>
      } @empty {
        <li class="support-conversation-list__empty">
          <ng-content />
        </li>
      }
    </ul>
  `
})
export class SupportConversationListComponent {
  readonly conversations = input.required<readonly SupportConversation[]>();
  readonly selectedId = input<string | null>(null);
  readonly conversationSelected = output<string>();

  statusLabel(status: SupportConversation['status']): string {
    return SUPPORT_STATUS_LABELS[status];
  }

  statusClass(status: SupportConversation['status']): string {
    return SUPPORT_STATUS_BADGES[status];
  }
}
