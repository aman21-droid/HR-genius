/** Attendance module types — mirror com.hrgenius.attendance.dto.AttendanceDtos. */

/** Day status as computed by the backend (present, on leave, holiday, weekend, ...). */
export type AttendanceStatus =
  | 'PRESENT'
  | 'HALF_DAY'
  | 'ABSENT'
  | 'ON_LEAVE'
  | 'HOLIDAY'
  | 'WEEKEND'
  | 'REGULARIZED'
  | 'NOT_MARKED';

export type RegularizationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface Holiday {
  id: number;
  holidayDate: string;
  name: string;
  optionalHoliday: boolean;
  locationId: number | null;
  locationName: string | null;
  year: number;
}

export interface HolidayRequest {
  holidayDate: string;
  name: string;
  optionalHoliday?: boolean;
  locationId?: number | null;
}

export interface AttendanceDay {
  id: number;
  employeeId: number;
  employeeName: string;
  workDate: string;
  checkIn: string | null;
  checkOut: string | null;
  workedMinutes: number;
  status: string;
  source: string;
  remarks: string | null;
}

export interface CalendarDay {
  date: string;
  status: string;
  checkIn: string | null;
  checkOut: string | null;
  workedMinutes: number | null;
  label: string | null;
}

export interface PunchRequest {
  remarks?: string | null;
}

export interface AttendanceEditRequest {
  checkIn?: string | null;
  checkOut?: string | null;
  status?: string | null;
  remarks?: string | null;
}

export interface Regularization {
  id: number;
  employeeId: number;
  employeeName: string;
  workDate: string;
  requestedCheckIn: string | null;
  requestedCheckOut: string | null;
  reason: string;
  status: RegularizationStatus;
  approvalRequestId: number | null;
  decidedAt: string | null;
  createdAt: string;
}

export interface RegularizationRequestBody {
  workDate: string;
  requestedCheckIn?: string | null;
  requestedCheckOut?: string | null;
  reason: string;
}
