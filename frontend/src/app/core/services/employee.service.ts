import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageQuery, PageResponse } from '../models/common.models';
import {
  AssetAssignment, CreateEmployeeResponse, EmergencyContact, EmergencyContactRequest, EmployeeDetail,
  EmployeeDocument, EmployeeFilter, EmployeeLookup, EmployeeRequest, EmployeeSummary, ExpiringDocument,
  ImportReport, OrgChartNode, Statutory, StatutoryRequest, TimelineEvent
} from '../models/employee.models';
import { toParams } from '../utils/http.utils';

/** Employee directory, profile tabs, documents and bulk operations. */
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/employees`;

  list(filter: EmployeeFilter, page: PageQuery): Observable<PageResponse<EmployeeSummary>> {
    return this.http.get<PageResponse<EmployeeSummary>>(this.base, { params: toParams({ ...filter, ...page }) });
  }

  lookup(q: string, limit = 10): Observable<EmployeeLookup[]> {
    return this.http.get<EmployeeLookup[]>(`${this.base}/lookup`, { params: toParams({ q, limit }) });
  }

  get(id: number): Observable<EmployeeDetail> {
    return this.http.get<EmployeeDetail>(`${this.base}/${id}`);
  }

  me(): Observable<EmployeeDetail> {
    return this.http.get<EmployeeDetail>(`${this.base}/me`);
  }

  create(req: EmployeeRequest): Observable<CreateEmployeeResponse> {
    return this.http.post<CreateEmployeeResponse>(this.base, req);
  }

  update(id: number, req: EmployeeRequest): Observable<EmployeeDetail> {
    return this.http.put<EmployeeDetail>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  orgChart(): Observable<OrgChartNode[]> {
    return this.http.get<OrgChartNode[]>(`${environment.apiBaseUrl}/org-chart`);
  }

  // ---- profile tabs ----
  timeline(id: number): Observable<TimelineEvent[]> {
    return this.http.get<TimelineEvent[]>(`${this.base}/${id}/timeline`);
  }

  statutory(id: number, reveal = false): Observable<Statutory> {
    return this.http.get<Statutory>(`${this.base}/${id}/statutory`, { params: toParams({ reveal }) });
  }

  updateStatutory(id: number, req: StatutoryRequest): Observable<Statutory> {
    return this.http.patch<Statutory>(`${this.base}/${id}/statutory`, req);
  }

  contacts(id: number): Observable<EmergencyContact[]> {
    return this.http.get<EmergencyContact[]>(`${this.base}/${id}/emergency-contacts`);
  }

  addContact(id: number, req: EmergencyContactRequest): Observable<EmergencyContact> {
    return this.http.post<EmergencyContact>(`${this.base}/${id}/emergency-contacts`, req);
  }

  updateContact(id: number, contactId: number, req: EmergencyContactRequest): Observable<EmergencyContact> {
    return this.http.put<EmergencyContact>(`${this.base}/${id}/emergency-contacts/${contactId}`, req);
  }

  deleteContact(id: number, contactId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/emergency-contacts/${contactId}`);
  }

  assets(id: number): Observable<AssetAssignment[]> {
    return this.http.get<AssetAssignment[]>(`${this.base}/${id}/assets`);
  }

  // ---- documents ----
  documents(id: number): Observable<EmployeeDocument[]> {
    return this.http.get<EmployeeDocument[]>(`${this.base}/${id}/documents`);
  }

  uploadDocument(id: number, file: File, meta: { category: string; title: string; expiryDate?: string | null; notes?: string | null }): Observable<EmployeeDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('category', meta.category);
    form.append('title', meta.title);
    if (meta.expiryDate) form.append('expiryDate', meta.expiryDate);
    if (meta.notes) form.append('notes', meta.notes);
    return this.http.post<EmployeeDocument>(`${this.base}/${id}/documents`, form);
  }

  downloadDocument(documentId: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/documents/${documentId}/download`,
      { responseType: 'blob', observe: 'response' });
  }

  verifyDocument(documentId: number, verified: boolean): Observable<EmployeeDocument> {
    return this.http.patch<EmployeeDocument>(`${environment.apiBaseUrl}/documents/${documentId}/verify`, null,
      { params: toParams({ verified }) });
  }

  deleteDocument(documentId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/documents/${documentId}`);
  }

  expiringDocuments(days = 30): Observable<ExpiringDocument[]> {
    return this.http.get<ExpiringDocument[]>(`${environment.apiBaseUrl}/documents/expiring`, { params: toParams({ days }) });
  }

  // ---- Excel ----
  export(filter: EmployeeFilter): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/export`, { params: toParams(filter), responseType: 'blob', observe: 'response' });
  }

  importTemplate(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/import/template`, { responseType: 'blob', observe: 'response' });
  }

  import(file: File, dryRun: boolean): Observable<ImportReport> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportReport>(`${this.base}/import`, form, { params: toParams({ dryRun }) });
  }
}
