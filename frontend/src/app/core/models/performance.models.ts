/** Performance types — mirror com.hrgenius.performance.dto.PerformanceDtos. */

export type CycleStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED';
export type ReviewStatus = 'NOT_STARTED' | 'SELF_SUBMITTED' | 'MANAGER_SUBMITTED' | 'ACKNOWLEDGED';
export type GoalStatus = 'NOT_STARTED' | 'ON_TRACK' | 'AT_RISK' | 'OFF_TRACK' | 'DONE';
export const GOAL_STATUSES: GoalStatus[] = ['NOT_STARTED', 'ON_TRACK', 'AT_RISK', 'OFF_TRACK', 'DONE'];

export interface ReviewCycle {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  selfReviewDue: string | null;
  managerReviewDue: string | null;
  status: CycleStatus;
  launchedAt: string | null;
  closedAt: string | null;
  reviewCount: number;
  completedCount: number;
}

export interface CycleSummary {
  cycle: ReviewCycle;
  byStatus: Record<ReviewStatus, number>;
  ratingDistribution: Record<string, number>;
  averageScore: number | null;
}

export interface ReviewSummary {
  id: number;
  cycleId: number;
  cycleName: string;
  cycleStatus: CycleStatus;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  designation: string | null;
  reviewerId: number;
  reviewerName: string;
  status: ReviewStatus;
  goalCount: number;
  managerRating: number | null;
  finalScore: number | null;
  selfSubmittedAt: string | null;
  managerSubmittedAt: string | null;
}

export interface Goal {
  id: number;
  title: string;
  description: string | null;
  weight: number;
  targetDate: string | null;
  progress: number;
  status: GoalStatus;
  selfRating: number | null;
  selfComment: string | null;
  managerRating: number | null;
  managerComment: string | null;
}

export interface Review {
  id: number;
  cycleId: number;
  cycleName: string;
  cycleStatus: CycleStatus;
  selfReviewDue: string | null;
  managerReviewDue: string | null;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  designation: string | null;
  reviewerId: number;
  reviewerName: string;
  status: ReviewStatus;
  selfRating: number | null;
  selfComments: string | null;
  managerRating: number | null;
  managerComments: string | null;
  finalScore: number | null;
  ackComment: string | null;
  selfSubmittedAt: string | null;
  managerSubmittedAt: string | null;
  acknowledgedAt: string | null;
  goals: Goal[];
  totalWeight: number;
  viewerRole: 'SELF' | 'MANAGER' | 'ADMIN';
  canEditGoals: boolean;
  canUpdateProgress: boolean;
  canSelfReview: boolean;
  canManagerReview: boolean;
  canAcknowledge: boolean;
}

export interface Assessment {
  overallRating: number | null;
  comments: string | null;
  goals: { goalId: number; rating: number | null; comment: string | null }[];
  submit: boolean;
}

export interface FeedbackNote {
  id: number;
  fromId: number;
  fromName: string;
  toId: number;
  toName: string;
  kind: 'PRAISE' | 'CONSTRUCTIVE';
  visibility: 'PUBLIC' | 'PRIVATE';
  message: string;
  createdAt: string;
}
