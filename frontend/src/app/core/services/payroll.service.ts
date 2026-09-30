import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Adjustment, PayrollRun, Payslip, PayslipSummary, SalaryComponent, SalaryComponentRequest, SalaryStructure
} from '../models/payroll.models';

/** Payroll runs, payslips, salary structure and exports. */
@Injectable({ providedIn: 'root' })
export class PayrollService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/payroll`;

  // ---- runs ----
  runs(): Observable<PayrollRun[]> {
    return this.http.get<PayrollRun[]>(`${this.base}/runs`);
  }

  run(id: number): Observable<PayrollRun> {
    return this.http.get<PayrollRun>(`${this.base}/runs/${id}`);
  }

  createRun(period: string, notes?: string | null): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/runs`, { period, notes });
  }

  calculate(id: number): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/runs/${id}/calculate`, null);
  }

  submit(id: number): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/runs/${id}/submit`, null);
  }

  markPaid(id: number, paymentReference: string): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/runs/${id}/paid`, { paymentReference });
  }

  deleteRun(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/runs/${id}`);
  }

  runPayslips(id: number): Observable<PayslipSummary[]> {
    return this.http.get<PayslipSummary[]>(`${this.base}/runs/${id}/payslips`);
  }

  register(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/runs/${id}/register`, { responseType: 'blob', observe: 'response' });
  }

  bankFile(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/runs/${id}/bank-file`, { responseType: 'blob', observe: 'response' });
  }

  // ---- adjustments ----
  adjustments(runId: number): Observable<Adjustment[]> {
    return this.http.get<Adjustment[]>(`${this.base}/runs/${runId}/adjustments`);
  }

  addAdjustment(runId: number, body: { employeeId: number; type: 'EARNING' | 'DEDUCTION'; label: string; amount: number; taxable: boolean }): Observable<Adjustment> {
    return this.http.post<Adjustment>(`${this.base}/runs/${runId}/adjustments`, body);
  }

  removeAdjustment(runId: number, adjustmentId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/runs/${runId}/adjustments/${adjustmentId}`);
  }

  // ---- structure ----
  components(): Observable<SalaryComponent[]> {
    return this.http.get<SalaryComponent[]>(`${this.base}/components`);
  }

  createComponent(req: SalaryComponentRequest): Observable<SalaryComponent> {
    return this.http.post<SalaryComponent>(`${this.base}/components`, req);
  }

  updateComponent(id: number, req: SalaryComponentRequest): Observable<SalaryComponent> {
    return this.http.put<SalaryComponent>(`${this.base}/components/${id}`, req);
  }

  deleteComponent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/components/${id}`);
  }

  // ---- payslips ----
  payslip(id: number): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.base}/payslips/${id}`);
  }

  payslipPdf(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/payslips/${id}/pdf`, { responseType: 'blob', observe: 'response' });
  }

  /** Rows are PayrollRun-shaped; `id` is the payslip id and totals are the employee's own. */
  myPayslips(): Observable<PayrollRun[]> {
    return this.http.get<PayrollRun[]>(`${this.base}/me/payslips`);
  }

  myStructure(): Observable<SalaryStructure> {
    return this.http.get<SalaryStructure>(`${this.base}/me/structure`);
  }
}
