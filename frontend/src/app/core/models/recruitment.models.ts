/** Recruitment (ATS) types — mirror com.hrgenius.recruitment.dto.RecruitmentDtos. */
import { EmploymentType } from './employee.models';

export type RequisitionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'OPEN' | 'ON_HOLD' | 'CLOSED' | 'REJECTED' | 'CANCELLED';
export const REQUISITION_STATUSES: RequisitionStatus[] =
  ['DRAFT', 'PENDING_APPROVAL', 'OPEN', 'ON_HOLD', 'CLOSED', 'REJECTED', 'CANCELLED'];

export type CandidateSource = 'CAREERS_PAGE' | 'REFERRAL' | 'LINKEDIN' | 'AGENCY' | 'DIRECT' | 'OTHER';
export const CANDIDATE_SOURCES: CandidateSource[] = ['CAREERS_PAGE', 'REFERRAL', 'LINKEDIN', 'AGENCY', 'DIRECT', 'OTHER'];

export type ApplicationStage = 'APPLIED' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'HIRED' | 'REJECTED' | 'WITHDRAWN';
/** Board columns, left to right. HIRED is reached only through the hire flow. */
export const PIPELINE_STAGES: ApplicationStage[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED'];
export const EXIT_STAGES: ApplicationStage[] = ['REJECTED', 'WITHDRAWN'];

export type InterviewMode = 'IN_PERSON' | 'VIDEO' | 'PHONE';
export const INTERVIEW_MODES: InterviewMode[] = ['VIDEO', 'IN_PERSON', 'PHONE'];
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export type Recommendation = 'STRONG_HIRE' | 'HIRE' | 'NO_HIRE' | 'STRONG_NO_HIRE';
export const RECOMMENDATIONS: Recommendation[] = ['STRONG_HIRE', 'HIRE', 'NO_HIRE', 'STRONG_NO_HIRE'];

export type OfferStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'SENT' | 'ACCEPTED' | 'DECLINED' | 'REJECTED' | 'WITHDRAWN';

export interface Requisition {
  id: number;
  reqCode: string;
  title: string;
  departmentId: number;
  departmentName: string;
  designationId: number;
  designationName: string;
  locationId: number;
  locationName: string;
  gradeId: number | null;
  gradeName: string | null;
  hiringManagerId: number;
  hiringManagerName: string;
  employmentType: EmploymentType;
  openings: number;
  filled: number;
  minExperience: number | null;
  maxExperience: number | null;
  salaryMin: number | null;
  salaryMax: number | null;
  skills: string | null;
  description: string | null;
  targetDate: string | null;
  publishOnCareers: boolean;
  status: RequisitionStatus;
  approvalRequestId: number | null;
  openedAt: string | null;
  closedAt: string | null;
  activeApplications: number;
  createdAt: string;
}

export interface RequisitionRequest {
  title: string;
  departmentId: number;
  designationId: number;
  locationId: number;
  gradeId?: number | null;
  hiringManagerId: number;
  employmentType: EmploymentType;
  openings: number;
  minExperience?: number | null;
  maxExperience?: number | null;
  salaryMin?: number | null;
  salaryMax?: number | null;
  skills?: string | null;
  description?: string | null;
  targetDate?: string | null;
  publishOnCareers?: boolean;
}

export interface RequisitionFilter {
  search?: string | null;
  status?: RequisitionStatus | null;
  departmentId?: number | null;
}

export interface CandidateSummary {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  currentTitle: string | null;
  currentCompany: string | null;
  totalExperience: number | null;
  city: string | null;
  source: CandidateSource;
  hasResume: boolean;
  applications: number;
  createdAt: string;
}

export interface CandidateApplication {
  applicationId: number;
  requisitionId: number;
  reqCode: string;
  requisitionTitle: string;
  stage: ApplicationStage;
  stageChangedAt: string;
}

export interface Candidate {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string | null;
  currentCompany: string | null;
  currentTitle: string | null;
  totalExperience: number | null;
  currentCtc: number | null;
  expectedCtc: number | null;
  noticePeriodDays: number | null;
  city: string | null;
  linkedinUrl: string | null;
  source: CandidateSource;
  referredById: number | null;
  referredByName: string | null;
  hasResume: boolean;
  resumeFileName: string | null;
  resumeSize: number | null;
  notes: string | null;
  applications: CandidateApplication[];
  createdAt: string;
}

export interface CandidateRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string | null;
  currentCompany?: string | null;
  currentTitle?: string | null;
  totalExperience?: number | null;
  currentCtc?: number | null;
  expectedCtc?: number | null;
  noticePeriodDays?: number | null;
  city?: string | null;
  linkedinUrl?: string | null;
  source?: CandidateSource | null;
  referredById?: number | null;
  notes?: string | null;
}

export interface ApplicationCard {
  id: number;
  candidateId: number;
  candidateName: string;
  email: string;
  currentTitle: string | null;
  currentCompany: string | null;
  totalExperience: number | null;
  stage: ApplicationStage;
  stageChangedAt: string;
  source: CandidateSource;
  averageRating: number | null;
  interviews: number;
  feedbackPending: number;
  hasResume: boolean;
}

export interface Pipeline {
  requisition: Requisition;
  applications: ApplicationCard[];
}

export interface ApplicationEvent {
  id: number;
  eventType: string;
  fromStage: ApplicationStage | null;
  toStage: ApplicationStage | null;
  message: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface Feedback {
  id: number;
  interviewerId: number;
  interviewerName: string;
  rating: number | null;
  recommendation: Recommendation | null;
  strengths: string | null;
  concerns: string | null;
  submittedAt: string | null;
}

export interface Interview {
  id: number;
  applicationId: number;
  roundName: string;
  mode: InterviewMode;
  scheduledAt: string;
  durationMinutes: number;
  locationOrLink: string | null;
  status: InterviewStatus;
  panel: Feedback[];
}

export interface Offer {
  id: number;
  applicationId: number;
  candidateName: string;
  requisitionTitle: string;
  designationId: number;
  designationName: string;
  departmentId: number;
  departmentName: string;
  locationId: number;
  locationName: string;
  gradeId: number | null;
  gradeName: string | null;
  employmentType: EmploymentType;
  annualCtc: number;
  joiningDate: string;
  expiryDate: string | null;
  notes: string | null;
  status: OfferStatus;
  approvalRequestId: number | null;
  sentAt: string | null;
  respondedAt: string | null;
  declineReason: string | null;
  createdAt: string;
}

export interface ApplicationDetail {
  id: number;
  requisition: Requisition;
  candidate: Candidate;
  stage: ApplicationStage;
  stageChangedAt: string;
  source: CandidateSource;
  coverNote: string | null;
  rejectionReason: string | null;
  employeeId: number | null;
  events: ApplicationEvent[];
  interviews: Interview[];
  offers: Offer[];
  createdAt: string;
}

export interface ScheduleInterviewRequest {
  roundName: string;
  mode: InterviewMode;
  scheduledAt: string;
  durationMinutes: number;
  locationOrLink?: string | null;
  panelistIds: number[];
}

export interface InterviewUpdateRequest {
  status?: InterviewStatus | null;
  scheduledAt?: string | null;
  locationOrLink?: string | null;
}

export interface SubmitFeedbackRequest {
  rating: number;
  recommendation: Recommendation;
  strengths?: string | null;
  concerns?: string | null;
}

export interface MyInterview {
  interviewId: number;
  feedbackId: number;
  roundName: string;
  mode: InterviewMode;
  scheduledAt: string;
  durationMinutes: number;
  locationOrLink: string | null;
  interviewStatus: InterviewStatus;
  candidateId: number;
  candidateName: string;
  currentTitle: string | null;
  currentCompany: string | null;
  totalExperience: number | null;
  hasResume: boolean;
  reqCode: string;
  requisitionTitle: string;
  submitted: boolean;
  rating: number | null;
  recommendation: Recommendation | null;
  strengths: string | null;
  concerns: string | null;
  submittedAt: string | null;
}

export interface OfferRequest {
  designationId: number;
  departmentId: number;
  locationId: number;
  gradeId?: number | null;
  employmentType: EmploymentType;
  annualCtc: number;
  joiningDate: string;
  expiryDate?: string | null;
  notes?: string | null;
}

export interface HireRequest {
  workEmail: string;
  managerId?: number | null;
  dateOfJoining?: string | null;
  createLogin?: boolean;
  startOnboarding?: boolean;
  onboardingTemplateId?: number | null;
}

export interface HireResponse {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  loginEmail: string | null;
  temporaryPassword: string | null;
  onboardingPlanId: number | null;
}

export interface CareerJob {
  reqCode: string;
  title: string;
  department: string;
  location: string;
  employmentType: EmploymentType;
  minExperience: number | null;
  maxExperience: number | null;
  skills: string | null;
  description: string | null;
  postedAt: string | null;
}
