import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RUNTIME_CONFIG } from '../../core/config/runtime-config';
import {
  CreateSupportConversationRequest,
  SendSupportMessageRequest,
  SupportAttachment,
  SupportConversation,
  SupportMessage,
  SupportPage,
  SupportStatus
} from './support.models';

@Injectable({ providedIn: 'root' })
export class SupportApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly resourceUrl = `${this.config.apiBaseUrl.replace(/\/$/, '')}/api/support`;

  createConversation(request: CreateSupportConversationRequest): Observable<SupportConversation> {
    return this.http.post<SupportConversation>(`${this.resourceUrl}/conversations`, request);
  }

  getMyConversations(page = 0, size = 50): Observable<SupportPage<SupportConversation>> {
    return this.http.get<SupportPage<SupportConversation>>(`${this.resourceUrl}/conversations`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  getAgentConversations(page = 0, size = 50, status?: SupportStatus): Observable<SupportPage<SupportConversation>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<SupportPage<SupportConversation>>(`${this.resourceUrl}/agent/conversations`, { params });
  }

  getConversation(conversationId: string): Observable<SupportConversation> {
    return this.http.get<SupportConversation>(`${this.resourceUrl}/conversations/${encodeURIComponent(conversationId)}`);
  }

  getMessages(conversationId: string, page = 0, size = 100): Observable<SupportPage<SupportMessage>> {
    return this.http.get<SupportPage<SupportMessage>>(
      `${this.resourceUrl}/conversations/${encodeURIComponent(conversationId)}/messages`,
      { params: new HttpParams().set('page', page).set('size', size) }
    );
  }

  sendMessage(conversationId: string, request: SendSupportMessageRequest): Observable<SupportMessage> {
    return this.http.post<SupportMessage>(
      `${this.resourceUrl}/conversations/${encodeURIComponent(conversationId)}/messages`, request
    );
  }

  uploadAttachment(conversationId: string, messageId: string, file: File): Observable<SupportAttachment> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<SupportAttachment>(
      `${this.resourceUrl}/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/attachments`,
      body
    );
  }

  getAttachmentContent(conversationId: string, messageId: string, attachmentId: string): Observable<Blob> {
    return this.http.get(
      `${this.resourceUrl}/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/attachments/${encodeURIComponent(attachmentId)}/content`,
      { responseType: 'blob' }
    );
  }
}
