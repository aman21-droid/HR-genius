import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApplyLeaveRequest, BalanceAdjustmentRequest, LeaveBalance, LeaveRequest, LeaveType, LeaveTypeRequest
} from '../models/leave.models';
import { toParams } from '../utils/http.utils';

/** Leave types, balances, applications and the accrual job. */
@Injectable({ providedIn: 'root' })
export class LeaveService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/leave`;

  // ---- types (config) ----
  types(activeOnly = false): Observable<LeaveType[]> {
    return this.http.get<LeaveType[]>(`${this.base}/types`, { params: toParams({ activeOnly }) });
  }

  createType(req: LeaveTypeRequest): Observable<LeaveType> {
    return this.http.post<LeaveType>(`${this.base}/types`, req);
  }

  updateType(id: number, req: LeaveTypeRequest): Observable<LeaveType> {
    return this.http.put<LeaveType>(`${this.base}/types/${id}`, req);
  }

  deleteType(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/types/${id}`);
  }

  // ---- balances ----
  myBalances(year?: number): Observable<LeaveBalance[]> {
    return this.http.get<LeaveBalance[]>(`${this.base}/balances`, { params: toParams({ year }) });
  }

  employeeBalances(employeeId: number, year?: number): Observable<LeaveBalance[]> {
    return this.http.get<LeaveBalance[]>(`${this.base}/balances/${employeeId}`, { params: toParams({ year }) });
  }

  adjustBalance(req: BalanceAdjustmentRequest): Observable<LeaveBalance> {
    return this.http.post<LeaveBalance>(`${this.base}/balances/adjust`, req);
  }

  // ---- requests ----
  myRequests(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(`${this.base}/requests`);
  }

  apply(req: ApplyLeaveRequest): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/requests`, req);
  }

  cancel(id: number): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/requests/${id}/cancel`, null);
  }

  // ---- accrual job ----
  runAccrual(period?: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.base}/accrual/run`, null, { params: toParams({ period }) });
  }
}
