/** Leave module types — mirror com.hrgenius.leave.dto.LeaveDtos. */

export type AccrualMethod = 'NONE' | 'MONTHLY' | 'ANNUAL';
export const ACCRUAL_METHODS: AccrualMethod[] = ['NONE', 'MONTHLY', 'ANNUAL'];

/** Leave workflow status (approval-backed). */
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface LeaveType {
  id: number;
  code: string;
  name: string;
  description: string | null;
  color: string | null;
  paid: boolean;
  annualEntitlement: number;
  accrualMethod: AccrualMethod;
  accrualRate: number;
  carryForwardCap: number;
  maxBalance: number | null;
  allowHalfDay: boolean;
  encashable: boolean;
  requiresApproval: boolean;
  active: boolean;
}

export interface LeaveTypeRequest {
  code: string;
  name: string;
  description?: string | null;
  color?: string | null;
  paid?: boolean;
  annualEntitlement: number;
  accrualMethod: AccrualMethod;
  accrualRate: number;
  carryForwardCap: number;
  maxBalance?: number | null;
  allowHalfDay?: boolean;
  encashable?: boolean;
  requiresApproval?: boolean;
  active?: boolean;
}

export interface LeaveBalance {
  leaveTypeId: number;
  code: string;
  name: string;
  color: string | null;
  year: number;
  opening: number;
  accrued: number;
  used: number;
  pending: number;
  adjustment: number;
  available: number;
  annualEntitlement: number;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  leaveTypeId: number;
  leaveTypeCode: string;
  leaveTypeName: string;
  color: string | null;
  startDate: string;
  endDate: string;
  halfDayStart: boolean;
  halfDayEnd: boolean;
  days: number;
  reason: string | null;
  status: LeaveStatus;
  approvalRequestId: number | null;
  decidedAt: string | null;
  createdAt: string;
}

export interface ApplyLeaveRequest {
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  halfDayStart?: boolean;
  halfDayEnd?: boolean;
  reason?: string | null;
}

export interface BalanceAdjustmentRequest {
  employeeId: number;
  leaveTypeId: number;
  year: number;
  amount: number;
  note?: string | null;
}
