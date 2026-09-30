import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AttendanceDay, AttendanceEditRequest, CalendarDay, Holiday, HolidayRequest, PunchRequest,
  Regularization, RegularizationRequestBody
} from '../models/attendance.models';
import { toParams } from '../utils/http.utils';

/** Punch-based attendance, calendar, regularizations and the holiday calendar. */
@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/attendance`;
  private holidayBase = `${environment.apiBaseUrl}/holidays`;

  // ---- punch (self) ----
  checkIn(req: PunchRequest = {}): Observable<AttendanceDay> {
    return this.http.post<AttendanceDay>(`${this.base}/check-in`, req);
  }

  checkOut(): Observable<AttendanceDay> {
    return this.http.post<AttendanceDay>(`${this.base}/check-out`, null);
  }

  // ---- views (self) ----
  calendar(from: string, to: string): Observable<CalendarDay[]> {
    return this.http.get<CalendarDay[]>(`${this.base}/calendar`, { params: toParams({ from, to }) });
  }

  myDays(from: string, to: string): Observable<AttendanceDay[]> {
    return this.http.get<AttendanceDay[]>(`${this.base}/days`, { params: toParams({ from, to }) });
  }

  // ---- regularization (self) ----
  myRegularizations(): Observable<Regularization[]> {
    return this.http.get<Regularization[]>(`${this.base}/regularizations`);
  }

  regularize(body: RegularizationRequestBody): Observable<Regularization> {
    return this.http.post<Regularization>(`${this.base}/regularizations`, body);
  }

  // ---- HR ----
  employeeDays(employeeId: number, from: string, to: string): Observable<AttendanceDay[]> {
    return this.http.get<AttendanceDay[]>(`${this.base}/employees/${employeeId}`, { params: toParams({ from, to }) });
  }

  editDay(employeeId: number, date: string, req: AttendanceEditRequest): Observable<AttendanceDay> {
    return this.http.put<AttendanceDay>(`${this.base}/employees/${employeeId}/${date}`, req);
  }

  // ---- holidays ----
  holidays(year?: number): Observable<Holiday[]> {
    return this.http.get<Holiday[]>(this.holidayBase, { params: toParams({ year }) });
  }

  createHoliday(req: HolidayRequest): Observable<Holiday> {
    return this.http.post<Holiday>(this.holidayBase, req);
  }

  updateHoliday(id: number, req: HolidayRequest): Observable<Holiday> {
    return this.http.put<Holiday>(`${this.holidayBase}/${id}`, req);
  }

  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`${this.holidayBase}/${id}`);
  }
}
