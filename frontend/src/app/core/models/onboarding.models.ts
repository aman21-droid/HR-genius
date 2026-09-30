/** Onboarding types — mirror com.hrgenius.onboarding.dto.OnboardingDtos. */

export type OwnerRole = 'HR' | 'MANAGER' | 'EMPLOYEE' | 'IT' | 'ADMIN' | 'FINANCE';
export const OWNER_ROLES: OwnerRole[] = ['HR', 'MANAGER', 'EMPLOYEE', 'IT', 'ADMIN', 'FINANCE'];

export type PlanStatus = 'IN_PROGRESS' | 'COMPLETED';
export type TaskStatus = 'PENDING' | 'DONE' | 'SKIPPED';

export interface TemplateTask {
  id: number;
  title: string;
  description: string | null;
  ownerRole: OwnerRole;
  dueOffsetDays: number;
  sortOrder: number;
}

export interface OnboardingTemplate {
  id: number;
  name: string;
  description: string | null;
  defaultTemplate: boolean;
  active: boolean;
  tasks: TemplateTask[];
}

export interface TemplateRequest {
  name: string;
  description?: string | null;
  defaultTemplate?: boolean;
  active?: boolean;
  tasks: { title: string; description?: string | null; ownerRole: OwnerRole; dueOffsetDays: number }[];
}

export interface PlanSummary {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  designation: string | null;
  department: string | null;
  startDate: string;
  status: PlanStatus;
  totalTasks: number;
  doneTasks: number;
  overdueTasks: number;
  completedAt: string | null;
}

export interface OnboardingTask {
  id: number;
  planId: number;
  title: string;
  description: string | null;
  ownerRole: OwnerRole;
  assigneeId: number | null;
  assigneeName: string | null;
  dueDate: string | null;
  status: TaskStatus;
  completedAt: string | null;
  completedBy: string | null;
  overdue: boolean;
  newHireId: number;
  newHireName: string;
}

export interface OnboardingPlan {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  designation: string | null;
  department: string | null;
  managerName: string | null;
  startDate: string;
  status: PlanStatus;
  applicationId: number | null;
  completedAt: string | null;
  tasks: OnboardingTask[];
}
