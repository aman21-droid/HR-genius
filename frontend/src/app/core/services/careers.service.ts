import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CareerJob } from '../models/recruitment.models';

/** Public careers page API — no login required. */
@Injectable({ providedIn: 'root' })
export class CareersService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/public/careers`;

  jobs(): Observable<CareerJob[]> {
    return this.http.get<CareerJob[]>(`${this.base}/jobs`);
  }

  job(reqCode: string): Observable<CareerJob> {
    return this.http.get<CareerJob>(`${this.base}/jobs/${encodeURIComponent(reqCode)}`);
  }

  apply(reqCode: string, fields: Record<string, string | number | null | undefined>, resume: File): Observable<{ message: string }> {
    const form = new FormData();
    for (const [k, v] of Object.entries(fields)) {
      if (v !== null && v !== undefined && v !== '') {
        form.append(k, String(v));
      }
    }
    form.append('resume', resume);
    return this.http.post<{ message: string }>(`${this.base}/jobs/${encodeURIComponent(reqCode)}/apply`, form);
  }
}
