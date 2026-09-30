/** Helpdesk, policy and analytics types — mirror the Phase 7 backend DTOs. */

// ---- helpdesk ----
export type TicketCategory = 'IT' | 'HR' | 'PAYROLL' | 'FACILITIES' | 'OTHER';
export const TICKET_CATEGORIES: TicketCategory[] = ['IT', 'HR', 'PAYROLL', 'FACILITIES', 'OTHER'];
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

export interface TicketSummary {
  id: number;
  ticketNo: string;
  subject: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  requesterId: number;
  requesterName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  dueAt: string;
  overdue: boolean;
  comments: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface TicketComment {
  id: number;
  authorId: number;
  authorName: string;
  body: string;
  internalNote: boolean;
  byRequester: boolean;
  createdAt: string;
}

export interface Ticket {
  id: number;
  ticketNo: string;
  subject: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  requesterId: number;
  requesterName: string;
  requesterDepartment: string | null;
  assigneeId: number | null;
  assigneeName: string | null;
  dueAt: string;
  overdue: boolean;
  firstResponseAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  resolutionNote: string | null;
  satisfaction: number | null;
  comments: TicketComment[];
  viewerIsAgent: boolean;
  viewerIsRequester: boolean;
  createdAt: string;
}

export interface QueueStats {
  byStatus: Record<TicketStatus, number>;
  openByCategory: Record<TicketCategory, number>;
  overdue: number;
  unassigned: number;
  averageSatisfaction: number | null;
}

// ---- policies ----
export interface Policy {
  id: number;
  code: string;
  title: string;
  category: string;
  summary: string | null;
  body: string;
  versionNo: number;
  requiresAck: boolean;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  effectiveDate: string | null;
  publishedAt: string | null;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  acknowledgedCount: number | null;
  employeeCount: number | null;
}

export interface PolicyRequest {
  code: string;
  title: string;
  category: string;
  summary?: string | null;
  body: string;
  requiresAck?: boolean;
  effectiveDate?: string | null;
}

export interface Compliance {
  policyId: number;
  title: string;
  versionNo: number;
  employeeCount: number;
  acknowledgedCount: number;
  pending: { employeeId: number; employeeCode: string; employeeName: string; department: string | null }[];
}

// ---- analytics ----
export interface Slice { label: string; value: number; }
export interface MoneySlice { label: string; value: number; }
export interface MonthPoint { month: string; joins: number; exits: number; headcount: number; }
export interface PayrollPoint { period: string; status: string; employees: number; gross: number; net: number; employerCost: number; }

export interface AnalyticsOverview {
  kpis: {
    headcount: number;
    joinsLast12m: number;
    exitsLast12m: number;
    attritionRate: number;
    averageTenureYears: number;
    openRequisitions: number;
    openPositions: number;
    averageDaysToHire: number | null;
    openTickets: number;
    overdueTickets: number;
    policyCompliance: number | null;
    latestMonthlyPayroll: number | null;
  };
  byDepartment: Slice[];
  byLocation: Slice[];
  byEmploymentType: Slice[];
  byGender: Slice[];
  tenure: Slice[];
  movement: MonthPoint[];
  leaveDaysByType: Slice[];
  payrollTrend: PayrollPoint[];
  payrollByDepartment: MoneySlice[];
  payrollByDepartmentPeriod: string | null;
  recruitmentFunnel: Slice[];
  candidateSources: Slice[];
  ratingDistribution: Slice[];
  ratingCycle: string | null;
}
