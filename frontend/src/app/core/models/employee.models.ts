export type EmployeeStatus = 'PROBATION' | 'ACTIVE' | 'ON_NOTICE' | 'EXITED';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERN';
export type Gender = 'MALE' | 'FEMALE' | 'NON_BINARY' | 'UNDISCLOSED';
export type MaritalStatus = 'SINGLE' | 'MARRIED' | 'DIVORCED' | 'WIDOWED' | 'UNDISCLOSED';
export type DocumentCategory =
  | 'ID_PROOF' | 'ADDRESS_PROOF' | 'EDUCATION' | 'EXPERIENCE' | 'CONTRACT'
  | 'VISA' | 'CERTIFICATION' | 'POLICY' | 'OTHER';

export const EMPLOYEE_STATUSES: EmployeeStatus[] = ['PROBATION', 'ACTIVE', 'ON_NOTICE', 'EXITED'];
export const EMPLOYMENT_TYPES: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN'];
export const GENDERS: Gender[] = ['FEMALE', 'MALE', 'NON_BINARY', 'UNDISCLOSED'];
export const MARITAL_STATUSES: MaritalStatus[] = ['SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'UNDISCLOSED'];
export const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
export const DOCUMENT_CATEGORIES: DocumentCategory[] = [
  'ID_PROOF', 'ADDRESS_PROOF', 'EDUCATION', 'EXPERIENCE', 'CONTRACT', 'VISA', 'CERTIFICATION', 'POLICY', 'OTHER'
];

export interface EmployeeSummary {
  id: number;
  employeeCode: string;
  fullName: string;
  firstName: string;
  lastName: string;
  workEmail: string;
  phone: string | null;
  designationId: number | null;
  designationName: string | null;
  departmentId: number | null;
  departmentName: string | null;
  locationId: number | null;
  locationName: string | null;
  gradeCode: string | null;
  managerId: number | null;
  managerName: string | null;
  status: EmployeeStatus;
  employmentType: EmploymentType;
  dateOfJoining: string;
}

export interface EmployeeDetail {
  id: number;
  employeeCode: string;
  firstName: string;
  middleName: string | null;
  lastName: string;
  fullName: string;
  workEmail: string;
  phone: string | null;
  designationId: number | null;
  designationName: string | null;
  departmentId: number | null;
  departmentName: string | null;
  gradeId: number | null;
  gradeName: string | null;
  locationId: number | null;
  locationName: string | null;
  businessUnitId: number | null;
  businessUnitName: string | null;
  costCenterId: number | null;
  costCenterName: string | null;
  managerId: number | null;
  managerName: string | null;
  managerCode: string | null;
  employmentType: EmploymentType;
  status: EmployeeStatus;
  dateOfJoining: string;
  probationEndDate: string | null;
  confirmationDate: string | null;
  exitDate: string | null;
  noticePeriodDays: number | null;
  personalEmail: string | null;
  gender: Gender | null;
  dateOfBirth: string | null;
  maritalStatus: MaritalStatus | null;
  bloodGroup: string | null;
  nationality: string | null;
  currentAddress: string | null;
  permanentAddress: string | null;
  annualCtc: number | null;
  directReports: number;
  hasLogin: boolean;
  limitedView: boolean;
  canViewCompensation: boolean;
  canViewSensitive: boolean;
  canEdit: boolean;
}

export interface EmployeeRequest {
  firstName: string;
  middleName?: string | null;
  lastName: string;
  workEmail: string;
  personalEmail?: string | null;
  phone?: string | null;
  gender?: Gender | null;
  dateOfBirth?: string | null;
  maritalStatus?: MaritalStatus | null;
  bloodGroup?: string | null;
  nationality?: string | null;
  currentAddress?: string | null;
  permanentAddress?: string | null;
  departmentId: number;
  designationId: number;
  gradeId?: number | null;
  locationId: number;
  managerId?: number | null;
  employmentType: EmploymentType;
  status?: EmployeeStatus | null;
  dateOfJoining: string;
  probationEndDate?: string | null;
  noticePeriodDays?: number | null;
  annualCtc?: number | null;
  createLogin?: boolean;
}

export interface CreateEmployeeResponse {
  employee: EmployeeDetail;
  loginEmail: string | null;
  temporaryPassword: string | null;
}

export interface EmployeeLookup {
  id: number;
  employeeCode: string;
  fullName: string;
  designation: string | null;
}

export interface EmployeeFilter {
  search?: string;
  departmentId?: number | null;
  designationId?: number | null;
  locationId?: number | null;
  gradeId?: number | null;
  managerId?: number | null;
  status?: EmployeeStatus | null;
  employmentType?: EmploymentType | null;
  includeExited?: boolean;
  teamOnly?: boolean;
}

export interface OrgChartNode {
  id: number;
  employeeCode: string;
  name: string;
  designation: string | null;
  department: string | null;
  location: string | null;
  managerId: number | null;
  directReports: number;
}

export interface TimelineEvent {
  id: number;
  eventType: string;
  eventDate: string;
  title: string;
  description: string | null;
  recordedBy: string | null;
}

export interface Statutory {
  employeeId: number;
  pan: string | null;
  aadhaar: string | null;
  uan: string | null;
  esiNumber: string | null;
  bankName: string | null;
  accountHolderName: string | null;
  bankAccountNumber: string | null;
  bankIfsc: string | null;
  taxRegime: 'OLD' | 'NEW' | null;
  masked: boolean;
}

export type StatutoryRequest = Partial<Omit<Statutory, 'employeeId' | 'masked'>>;

export interface EmergencyContact {
  id: number;
  name: string;
  relationship: string;
  phone: string;
  email: string | null;
  primary: boolean;
}

export type EmergencyContactRequest = Omit<EmergencyContact, 'id'>;

export interface EmployeeDocument {
  id: number;
  employeeId: number;
  category: DocumentCategory;
  title: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  expiryDate: string | null;
  daysToExpiry: number | null;
  verified: boolean;
  notes: string | null;
  uploadedBy: string | null;
  uploadedAt: string;
}

export interface ExpiringDocument {
  documentId: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  category: DocumentCategory;
  title: string;
  expiryDate: string;
  daysToExpiry: number;
}

export interface AssetAssignment {
  id: number;
  assetId: number;
  assetTag: string;
  assetName: string;
  category: string;
  serialNumber: string | null;
  employeeId: number;
  employeeName: string | null;
  employeeCode: string | null;
  assignedOn: string;
  returnedOn: string | null;
  assignNotes: string | null;
  returnCondition: string | null;
  returnNotes: string | null;
}

export interface ImportError {
  row: number;
  column: string;
  message: string;
}

export interface ImportReport {
  totalRows: number;
  validRows: number;
  created: number;
  dryRun: boolean;
  errors: ImportError[];
}
