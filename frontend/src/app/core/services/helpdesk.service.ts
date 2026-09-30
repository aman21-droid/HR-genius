import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageQuery, PageResponse } from '../models/common.models';
import {
  QueueStats, Ticket, TicketCategory, TicketPriority, TicketStatus, TicketSummary
} from '../models/services.models';
import { toParams } from '../utils/http.utils';

/** Helpdesk: raise and follow tickets; agents work the queue. */
@Injectable({ providedIn: 'root' })
export class HelpdeskService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/helpdesk`;

  create(body: { category: TicketCategory; priority: TicketPriority; subject: string; description: string }): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets`, body);
  }

  mine(): Observable<TicketSummary[]> {
    return this.http.get<TicketSummary[]>(`${this.base}/tickets/mine`);
  }

  queue(filter: { search?: string | null; status?: TicketStatus | null; category?: TicketCategory | null; mine?: boolean | null; overdue?: boolean | null },
        page: PageQuery): Observable<PageResponse<TicketSummary>> {
    return this.http.get<PageResponse<TicketSummary>>(`${this.base}/tickets`, { params: toParams({ ...filter, ...page }) });
  }

  stats(): Observable<QueueStats> {
    return this.http.get<QueueStats>(`${this.base}/stats`);
  }

  get(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.base}/tickets/${id}`);
  }

  comment(id: number, body: string, internal = false): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/comments`, { body, internal });
  }

  update(id: number, body: { assigneeId?: number | null; unassign?: boolean; priority?: TicketPriority | null; status?: TicketStatus | null; resolutionNote?: string | null }): Observable<Ticket> {
    return this.http.patch<Ticket>(`${this.base}/tickets/${id}`, body);
  }

  close(id: number, satisfaction: number | null): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/close`, { satisfaction });
  }

  reopen(id: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/reopen`, null);
  }
}
