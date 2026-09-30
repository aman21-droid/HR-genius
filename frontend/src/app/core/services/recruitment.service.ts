import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageQuery, PageResponse } from '../models/common.models';
import {
  ApplicationDetail, ApplicationStage, Candidate, CandidateRequest, CandidateSource, CandidateSummary,
  HireRequest, HireResponse, Interview, InterviewUpdateRequest, MyInterview, Offer, OfferRequest, Pipeline,
  Requisition, RequisitionFilter, RequisitionRequest, RequisitionStatus, ScheduleInterviewRequest,
  SubmitFeedbackRequest
} from '../models/recruitment.models';
import { toParams } from '../utils/http.utils';

/** Requisitions, candidates, pipeline, interviews and offers (the ATS). */
@Injectable({ providedIn: 'root' })
export class RecruitmentService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/recruitment`;

  // ---- requisitions ----
  requisitions(filter: RequisitionFilter, page: PageQuery): Observable<PageResponse<Requisition>> {
    return this.http.get<PageResponse<Requisition>>(`${this.base}/requisitions`, { params: toParams({ ...filter, ...page }) });
  }

  requisition(id: number): Observable<Requisition> {
    return this.http.get<Requisition>(`${this.base}/requisitions/${id}`);
  }

  createRequisition(req: RequisitionRequest): Observable<Requisition> {
    return this.http.post<Requisition>(`${this.base}/requisitions`, req);
  }

  updateRequisition(id: number, req: RequisitionRequest): Observable<Requisition> {
    return this.http.put<Requisition>(`${this.base}/requisitions/${id}`, req);
  }

  submitRequisition(id: number): Observable<Requisition> {
    return this.http.post<Requisition>(`${this.base}/requisitions/${id}/submit`, null);
  }

  setRequisitionStatus(id: number, status: RequisitionStatus): Observable<Requisition> {
    return this.http.post<Requisition>(`${this.base}/requisitions/${id}/status`, { status });
  }

  pipeline(requisitionId: number): Observable<Pipeline> {
    return this.http.get<Pipeline>(`${this.base}/requisitions/${requisitionId}/pipeline`);
  }

  // ---- candidates ----
  candidates(filter: { search?: string | null; source?: CandidateSource | null }, page: PageQuery): Observable<PageResponse<CandidateSummary>> {
    return this.http.get<PageResponse<CandidateSummary>>(`${this.base}/candidates`, { params: toParams({ ...filter, ...page }) });
  }

  candidate(id: number): Observable<Candidate> {
    return this.http.get<Candidate>(`${this.base}/candidates/${id}`);
  }

  createCandidate(req: CandidateRequest): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.base}/candidates`, req);
  }

  updateCandidate(id: number, req: CandidateRequest): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.base}/candidates/${id}`, req);
  }

  uploadResume(id: number, file: File): Observable<Candidate> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Candidate>(`${this.base}/candidates/${id}/resume`, form);
  }

  downloadResume(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/candidates/${id}/resume`, { responseType: 'blob', observe: 'response' });
  }

  // ---- applications ----
  createApplication(requisitionId: number, candidateId: number, source?: CandidateSource | null,
                    coverNote?: string | null): Observable<ApplicationDetail> {
    return this.http.post<ApplicationDetail>(`${this.base}/applications`, { requisitionId, candidateId, source, coverNote });
  }

  application(id: number): Observable<ApplicationDetail> {
    return this.http.get<ApplicationDetail>(`${this.base}/applications/${id}`);
  }

  moveStage(id: number, stage: ApplicationStage, note?: string | null): Observable<ApplicationDetail> {
    return this.http.post<ApplicationDetail>(`${this.base}/applications/${id}/stage`, { stage, note });
  }

  addNote(id: number, message: string): Observable<ApplicationDetail> {
    return this.http.post<ApplicationDetail>(`${this.base}/applications/${id}/notes`, { message });
  }

  // ---- interviews ----
  scheduleInterview(applicationId: number, req: ScheduleInterviewRequest): Observable<Interview> {
    return this.http.post<Interview>(`${this.base}/applications/${applicationId}/interviews`, req);
  }

  updateInterview(id: number, req: InterviewUpdateRequest): Observable<Interview> {
    return this.http.patch<Interview>(`${this.base}/interviews/${id}`, req);
  }

  myInterviews(): Observable<MyInterview[]> {
    return this.http.get<MyInterview[]>(`${this.base}/interviews/mine`);
  }

  submitFeedback(interviewId: number, req: SubmitFeedbackRequest): Observable<MyInterview> {
    return this.http.put<MyInterview>(`${this.base}/interviews/${interviewId}/feedback`, req);
  }

  // ---- offers ----
  createOffer(applicationId: number, req: OfferRequest): Observable<Offer> {
    return this.http.post<Offer>(`${this.base}/applications/${applicationId}/offers`, req);
  }

  updateOffer(id: number, req: OfferRequest): Observable<Offer> {
    return this.http.put<Offer>(`${this.base}/offers/${id}`, req);
  }

  submitOffer(id: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.base}/offers/${id}/submit`, null);
  }

  sendOffer(id: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.base}/offers/${id}/send`, null);
  }

  respondToOffer(id: number, accepted: boolean, declineReason?: string | null): Observable<Offer> {
    return this.http.post<Offer>(`${this.base}/offers/${id}/response`, { accepted, declineReason });
  }

  withdrawOffer(id: number): Observable<Offer> {
    return this.http.post<Offer>(`${this.base}/offers/${id}/withdraw`, null);
  }

  offerLetter(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/offers/${id}/letter`, { responseType: 'blob', observe: 'response' });
  }

  hire(offerId: number, req: HireRequest): Observable<HireResponse> {
    return this.http.post<HireResponse>(`${this.base}/offers/${offerId}/hire`, req);
  }
}
