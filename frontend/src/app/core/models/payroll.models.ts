/** Payroll types — mirror com.hrgenius.payroll.dto.PayrollDtos. */

export type RunStatus = 'DRAFT' | 'CALCULATED' | 'PENDING_APPROVAL' | 'APPROVED' | 'PAID';
export type CalcType = 'PERCENT_OF_CTC' | 'PERCENT_OF_BASIC' | 'FIXED_MONTHLY' | 'BALANCING';
export const CALC_TYPES: CalcType[] = ['PERCENT_OF_CTC', 'PERCENT_OF_BASIC', 'FIXED_MONTHLY', 'BALANCING'];

export interface PayrollRun {
  id: number;
  period: string;
  periodStart: string;
  periodEnd: string;
  status: RunStatus;
  employeeCount: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  totalEmployerCost: number;
  approvalRequestId: number | null;
  calculatedAt: string | null;
  approvedAt: string | null;
  paidAt: string | null;
  paymentReference: string | null;
  notes: string | null;
  adjustments: number;
  createdAt: string;
}

export interface PayslipSummary {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  department: string | null;
  designation: string | null;
  paidDays: number;
  lopDays: number;
  grossEarnings: number;
  totalDeductions: number;
  netPay: number;
  bankDetailsMissing: boolean;
}

export interface PayslipLine {
  code: string;
  name: string;
  type: 'EARNING' | 'DEDUCTION' | 'EMPLOYER';
  amount: number;
}

export interface Payslip {
  id: number;
  runId: number;
  period: string;
  runStatus: RunStatus;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  designation: string | null;
  department: string | null;
  location: string | null;
  dateOfJoining: string | null;
  pan: string | null;
  uan: string | null;
  daysInPeriod: number;
  lopDays: number;
  paidDays: number;
  annualCtc: number;
  grossEarnings: number;
  totalDeductions: number;
  netPay: number;
  employerPf: number;
  employerEsi: number;
  taxRegime: string | null;
  bankName: string | null;
  accountMasked: string | null;
  bankIfsc: string | null;
  earnings: PayslipLine[];
  deductions: PayslipLine[];
  employer: PayslipLine[];
}

export interface Adjustment {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  type: 'EARNING' | 'DEDUCTION';
  label: string;
  amount: number;
  taxable: boolean;
}

export interface SalaryComponent {
  id: number;
  code: string;
  name: string;
  calcType: CalcType;
  calcValue: number;
  taxable: boolean;
  sortOrder: number;
  active: boolean;
}

export interface SalaryComponentRequest {
  code: string;
  name: string;
  calcType: CalcType;
  calcValue: number;
  taxable?: boolean;
  sortOrder: number;
  active?: boolean;
}

export interface SalaryStructure {
  annualCtc: number;
  monthlyGross: number;
  monthlyNet: number;
  taxRegime: string;
  earnings: PayslipLine[];
  deductions: PayslipLine[];
  employer: PayslipLine[];
}
