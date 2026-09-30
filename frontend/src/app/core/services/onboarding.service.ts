import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  OnboardingPlan, OnboardingTask, OnboardingTemplate, PlanSummary, TaskStatus, TemplateRequest
} from '../models/onboarding.models';

/** Onboarding templates, plans and checklist tasks. */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/onboarding`;

  templates(): Observable<OnboardingTemplate[]> {
    return this.http.get<OnboardingTemplate[]>(`${this.base}/templates`);
  }

  createTemplate(req: TemplateRequest): Observable<OnboardingTemplate> {
    return this.http.post<OnboardingTemplate>(`${this.base}/templates`, req);
  }

  updateTemplate(id: number, req: TemplateRequest): Observable<OnboardingTemplate> {
    return this.http.put<OnboardingTemplate>(`${this.base}/templates/${id}`, req);
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/templates/${id}`);
  }

  plans(): Observable<PlanSummary[]> {
    return this.http.get<PlanSummary[]>(`${this.base}/plans`);
  }

  plan(id: number): Observable<OnboardingPlan> {
    return this.http.get<OnboardingPlan>(`${this.base}/plans/${id}`);
  }

  startPlan(employeeId: number, templateId?: number | null): Observable<OnboardingPlan> {
    return this.http.post<OnboardingPlan>(`${this.base}/plans`, { employeeId, templateId });
  }

  /** 204 (null body) when the signed-in employee has no onboarding plan. */
  myPlan(): Observable<HttpResponse<OnboardingPlan>> {
    return this.http.get<OnboardingPlan>(`${this.base}/me/plan`, { observe: 'response' });
  }

  myTasks(): Observable<OnboardingTask[]> {
    return this.http.get<OnboardingTask[]>(`${this.base}/me/tasks`);
  }

  setTaskStatus(taskId: number, status: TaskStatus): Observable<OnboardingTask> {
    return this.http.post<OnboardingTask>(`${this.base}/tasks/${taskId}/status`, { status });
  }

  assignTask(taskId: number, assigneeId: number | null, dueDate?: string | null): Observable<OnboardingTask> {
    return this.http.patch<OnboardingTask>(`${this.base}/tasks/${taskId}`, { assigneeId, dueDate });
  }
}
