import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageQuery, PageResponse } from '../models/common.models';
import {
  Asset, AssetCategory, AssetRequest, AssetStatus, AuditLogEntry, Company, Master, MasterRequest,
  MasterType, OrgLookups
} from '../models/org.models';
import { AssetAssignment } from '../models/employee.models';
import { toParams } from '../utils/http.utils';

/** Company profile, org masters, assets and the audit trail. */
@Injectable({ providedIn: 'root' })
export class OrgService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/org`;
  private lookups$?: Observable<OrgLookups>;

  /** Cached for the session; call refreshLookups() after editing masters. */
  lookups(): Observable<OrgLookups> {
    this.lookups$ ??= this.http.get<OrgLookups>(`${this.base}/lookups`).pipe(shareReplay(1));
    return this.lookups$;
  }

  refreshLookups(): void {
    this.lookups$ = undefined;
  }

  company(): Observable<Company> {
    return this.http.get<Company>(`${this.base}/company`);
  }

  updateCompany(req: Omit<Company, 'id'>): Observable<Company> {
    return this.http.put<Company>(`${this.base}/company`, req);
  }

  masters(type: MasterType, search: string, page: PageQuery): Observable<PageResponse<Master>> {
    return this.http.get<PageResponse<Master>>(`${this.base}/${type}`, { params: toParams({ search, ...page }) });
  }

  createMaster(type: MasterType, req: MasterRequest): Observable<Master> {
    return this.http.post<Master>(`${this.base}/${type}`, req);
  }

  updateMaster(type: MasterType, id: number, req: MasterRequest): Observable<Master> {
    return this.http.put<Master>(`${this.base}/${type}/${id}`, req);
  }

  deleteMaster(type: MasterType, id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${type}/${id}`);
  }

  // ---- assets ----
  assets(filter: { search?: string; category?: AssetCategory | null; status?: AssetStatus | null },
         page: PageQuery): Observable<PageResponse<Asset>> {
    return this.http.get<PageResponse<Asset>>(`${environment.apiBaseUrl}/assets`, { params: toParams({ ...filter, ...page }) });
  }

  createAsset(req: AssetRequest): Observable<Asset> {
    return this.http.post<Asset>(`${environment.apiBaseUrl}/assets`, req);
  }

  updateAsset(id: number, req: AssetRequest): Observable<Asset> {
    return this.http.put<Asset>(`${environment.apiBaseUrl}/assets/${id}`, req);
  }

  deleteAsset(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/assets/${id}`);
  }

  assignAsset(id: number, employeeId: number, assignedOn: string | null, notes: string | null): Observable<Asset> {
    return this.http.post<Asset>(`${environment.apiBaseUrl}/assets/${id}/assign`, { employeeId, assignedOn, notes });
  }

  returnAsset(id: number, condition: string, returnedOn: string | null, notes: string | null): Observable<Asset> {
    return this.http.post<Asset>(`${environment.apiBaseUrl}/assets/${id}/return`, { condition, returnedOn, notes });
  }

  assetHistory(id: number): Observable<AssetAssignment[]> {
    return this.http.get<AssetAssignment[]>(`${environment.apiBaseUrl}/assets/${id}/history`);
  }

  // ---- audit ----
  auditLogs(filter: { entity?: string; entityId?: string; actor?: string; action?: string | null },
            page: PageQuery): Observable<PageResponse<AuditLogEntry>> {
    return this.http.get<PageResponse<AuditLogEntry>>(`${environment.apiBaseUrl}/audit-logs`,
      { params: toParams({ ...filter, ...page }) });
  }
}
