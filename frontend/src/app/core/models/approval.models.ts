/** Generic approval engine types — mirror com.hrgenius.approval.dto.ApprovalDtos. */

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface InboxItem {
  stepId: number;
  requestId: number;
  subjectType: string;
  subjectId: number;
  title: string;
  requesterEmpId: number | null;
  requesterName: string;
  roleHint: string | null;
  stepNo: number;
  totalSteps: number;
  createdAt: string;
}

export interface StepView {
  stepNo: number;
  approverEmpId: number | null;
  approverName: string | null;
  roleHint: string | null;
  status: string;
  comment: string | null;
  decidedAt: string | null;
}

export interface MyRequestItem {
  requestId: number;
  subjectType: string;
  subjectId: number;
  title: string;
  status: string;
  currentStep: number;
  totalSteps: number;
  steps: StepView[];
  createdAt: string;
  resolvedAt: string | null;
}

export interface DecisionRequest {
  approve: boolean;
  comment?: string | null;
}
