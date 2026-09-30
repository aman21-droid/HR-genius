import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AnalyticsOverview, Compliance, Policy, PolicyRequest } from '../models/services.models';

/** Policy library and acknowledgements. */
@Injectable({ providedIn: 'root' })
export class PolicyService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/policies`;

  list(): Observable<Policy[]> {
    return this.http.get<Policy[]>(this.base);
  }

  pending(): Observable<Policy[]> {
    return this.http.get<Policy[]>(`${this.base}/pending`);
  }

  acknowledge(id: number): Observable<Policy> {
    return this.http.post<Policy>(`${this.base}/${id}/acknowledge`, null);
  }

  create(req: PolicyRequest): Observable<Policy> {
    return this.http.post<Policy>(this.base, req);
  }

  update(id: number, req: PolicyRequest): Observable<Policy> {
    return this.http.put<Policy>(`${this.base}/${id}`, req);
  }

  publish(id: number): Observable<Policy> {
    return this.http.post<Policy>(`${this.base}/${id}/publish`, null);
  }

  archive(id: number): Observable<Policy> {
    return this.http.post<Policy>(`${this.base}/${id}/archive`, null);
  }

  compliance(id: number): Observable<Compliance> {
    return this.http.get<Compliance>(`${this.base}/${id}/compliance`);
  }
}

/** Organisation analytics (ANALYTICS_VIEW). */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/analytics`;

  overview(): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>(`${this.base}/overview`);
  }

  headcountExport(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/headcount.xlsx`, { responseType: 'blob', observe: 'response' });
  }
}
