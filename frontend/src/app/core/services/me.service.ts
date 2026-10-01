import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Todo {
  kind: 'APPROVAL' | 'FEEDBACK' | 'INTERVIEW' | 'POLICY' | 'ONBOARDING' | 'REVIEW' | 'TICKET';
  title: string;
  detail: string;
  route: string;
  due: string | null;
}

export interface MeSummary {
  todos: Todo[];
  leaveBalances: { code: string; name: string; color: string | null; available: number }[];
  latestPayslip: { id: number; period: string; net: number } | null;
  holidays: { date: string; name: string; optional: boolean }[];
  kudos: { fromName: string; toName: string; message: string; createdAt: string }[];
  reviewStatus: string | null;
  reviewId: number | null;
  openTickets: number;
}

/** The signed-in person's cross-module summary; the shell's to-do bell reads {@link todos}. */
@Injectable({ providedIn: 'root' })
export class MeService {
  private http = inject(HttpClient);

  private readonly _todos = signal<Todo[]>([]);
  readonly todos = this._todos.asReadonly();

  summary(): Observable<MeSummary> {
    return this.http.get<MeSummary>(`${environment.apiBaseUrl}/me/summary`).pipe(tap((s) => this._todos.set(s.todos)));
  }

  /** Fire-and-forget refresh for the bell (e.g. after navigation); failures just leave it empty. */
  refresh(): void {
    this.summary().subscribe({ error: () => this._todos.set([]) });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<{ accessToken: string; refreshToken: string }> {
    return this.http.post<{ accessToken: string; refreshToken: string }>(`${environment.apiBaseUrl}/auth/change-password`,
      { currentPassword, newPassword });
  }
}

/** Icon per to-do kind, shared by the dashboard and the bell menu. */
export const TODO_ICONS: Record<Todo['kind'], string> = {
  APPROVAL: 'fact_check', FEEDBACK: 'rate_review', INTERVIEW: 'record_voice_over', POLICY: 'policy',
  ONBOARDING: 'how_to_reg', REVIEW: 'trending_up', TICKET: 'support_agent'
};
