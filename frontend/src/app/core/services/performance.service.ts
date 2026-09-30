import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Assessment, CycleSummary, FeedbackNote, GoalStatus, Review, ReviewCycle, ReviewSummary
} from '../models/performance.models';
import { toParams } from '../utils/http.utils';

/** Review cycles, reviews and goals, and continuous feedback. */
@Injectable({ providedIn: 'root' })
export class PerformanceService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/performance`;

  // ---- cycles (HR) ----
  cycles(): Observable<ReviewCycle[]> {
    return this.http.get<ReviewCycle[]>(`${this.base}/cycles`);
  }

  createCycle(body: { name: string; startDate: string; endDate: string; selfReviewDue?: string | null; managerReviewDue?: string | null }): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.base}/cycles`, body);
  }

  launch(id: number): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.base}/cycles/${id}/launch`, null);
  }

  close(id: number): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.base}/cycles/${id}/close`, null);
  }

  summary(id: number): Observable<CycleSummary> {
    return this.http.get<CycleSummary>(`${this.base}/cycles/${id}/summary`);
  }

  cycleReviews(id: number): Observable<ReviewSummary[]> {
    return this.http.get<ReviewSummary[]>(`${this.base}/cycles/${id}/reviews`);
  }

  // ---- reviews ----
  myReviews(): Observable<ReviewSummary[]> {
    return this.http.get<ReviewSummary[]>(`${this.base}/me/reviews`);
  }

  teamReviews(): Observable<ReviewSummary[]> {
    return this.http.get<ReviewSummary[]>(`${this.base}/team/reviews`);
  }

  review(id: number): Observable<Review> {
    return this.http.get<Review>(`${this.base}/reviews/${id}`);
  }

  addGoal(reviewId: number, body: { title: string; description?: string | null; weight: number; targetDate?: string | null }): Observable<Review> {
    return this.http.post<Review>(`${this.base}/reviews/${reviewId}/goals`, body);
  }

  updateGoal(reviewId: number, goalId: number, body: { title: string; description?: string | null; weight: number; targetDate?: string | null }): Observable<Review> {
    return this.http.put<Review>(`${this.base}/reviews/${reviewId}/goals/${goalId}`, body);
  }

  deleteGoal(reviewId: number, goalId: number): Observable<Review> {
    return this.http.delete<Review>(`${this.base}/reviews/${reviewId}/goals/${goalId}`);
  }

  progress(reviewId: number, goalId: number, progress: number, status: GoalStatus): Observable<Review> {
    return this.http.patch<Review>(`${this.base}/reviews/${reviewId}/goals/${goalId}/progress`, { progress, status });
  }

  selfAssessment(reviewId: number, body: Assessment): Observable<Review> {
    return this.http.put<Review>(`${this.base}/reviews/${reviewId}/self`, body);
  }

  managerAssessment(reviewId: number, body: Assessment): Observable<Review> {
    return this.http.put<Review>(`${this.base}/reviews/${reviewId}/manager`, body);
  }

  acknowledge(reviewId: number, comment: string | null): Observable<Review> {
    return this.http.post<Review>(`${this.base}/reviews/${reviewId}/acknowledge`, { comment });
  }

  // ---- feedback ----
  give(body: { toEmployeeId: number; kind: 'PRAISE' | 'CONSTRUCTIVE'; visibility: 'PUBLIC' | 'PRIVATE'; message: string }): Observable<FeedbackNote> {
    return this.http.post<FeedbackNote>(`${this.base}/feedback`, body);
  }

  wall(limit = 30): Observable<FeedbackNote[]> {
    return this.http.get<FeedbackNote[]>(`${this.base}/feedback/wall`, { params: toParams({ limit }) });
  }

  received(): Observable<FeedbackNote[]> {
    return this.http.get<FeedbackNote[]>(`${this.base}/feedback/received`);
  }

  given(): Observable<FeedbackNote[]> {
    return this.http.get<FeedbackNote[]>(`${this.base}/feedback/given`);
  }

  aboutTeam(): Observable<FeedbackNote[]> {
    return this.http.get<FeedbackNote[]>(`${this.base}/feedback/team`);
  }
}
