import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DecisionRequest, InboxItem, MyRequestItem } from '../models/approval.models';

/**
 * Generic approval engine client. Holds a live `pendingCount` signal so the shell can
 * badge the Approvals nav item without each screen re-fetching.
 */
@Injectable({ providedIn: 'root' })
export class ApprovalService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/approvals`;

  private readonly _pendingCount = signal(0);
  readonly pendingCount = this._pendingCount.asReadonly();

  inbox(): Observable<InboxItem[]> {
    return this.http.get<InboxItem[]>(`${this.base}/inbox`).pipe(tap((items) => this._pendingCount.set(items.length)));
  }

  /** Refreshes the nav badge; safe to call on login and after any decision. */
  refreshCount(): void {
    this.http.get<{ count: number }>(`${this.base}/inbox/count`).subscribe({
      next: (r) => this._pendingCount.set(r.count ?? 0),
      error: () => this._pendingCount.set(0)
    });
  }

  mine(): Observable<MyRequestItem[]> {
    return this.http.get<MyRequestItem[]>(`${this.base}/mine`);
  }

  decide(stepId: number, req: DecisionRequest): Observable<MyRequestItem> {
    return this.http.post<MyRequestItem>(`${this.base}/steps/${stepId}/decide`, req)
      .pipe(tap(() => this.refreshCount()));
  }
}
