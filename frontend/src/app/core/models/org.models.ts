import { LookupItem } from './common.models';

export type MasterType =
  | 'business-units' | 'cost-centers' | 'departments' | 'designations' | 'grades' | 'locations';

export interface MasterTypeInfo {
  type: MasterType;
  label: string;
  singular: string;
  icon: string;
}

export const MASTER_TYPES: MasterTypeInfo[] = [
  { type: 'departments', label: 'Departments', singular: 'Department', icon: 'account_tree' },
  { type: 'designations', label: 'Designations', singular: 'Designation', icon: 'badge' },
  { type: 'grades', label: 'Grades', singular: 'Grade', icon: 'stairs' },
  { type: 'locations', label: 'Locations', singular: 'Location', icon: 'place' },
  { type: 'business-units', label: 'Business units', singular: 'Business unit', icon: 'domain' },
  { type: 'cost-centers', label: 'Cost centers', singular: 'Cost center', icon: 'savings' }
];

export interface Master {
  id: number;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  businessUnitId: number | null;
  businessUnitName: string | null;
  costCenterId: number | null;
  costCenterName: string | null;
  parentId: number | null;
  parentName: string | null;
  headEmployeeId: number | null;
  headEmployeeName: string | null;
  levelNo: number | null;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  country: string | null;
  postalCode: string | null;
  timezone: string | null;
  employeeCount: number;
}

export type MasterRequest = Partial<Omit<Master, 'id' | 'employeeCount' | 'businessUnitName' |
  'costCenterName' | 'parentName' | 'headEmployeeName'>> & { code: string; name: string };

export interface OrgLookups {
  businessUnits: LookupItem[];
  costCenters: LookupItem[];
  departments: LookupItem[];
  designations: LookupItem[];
  grades: LookupItem[];
  locations: LookupItem[];
}

export interface Company {
  id: number;
  name: string;
  legalName: string | null;
  registrationNo: string | null;
  website: string | null;
  country: string | null;
  currency: string | null;
  fyStartMonth: number | null;
  employeeCodePrefix: string | null;
}

export type AssetCategory = 'LAPTOP' | 'MONITOR' | 'PHONE' | 'ID_CARD' | 'ACCESS_CARD' | 'HEADSET' | 'OTHER';
export type AssetStatus = 'AVAILABLE' | 'ASSIGNED' | 'IN_REPAIR' | 'RETIRED';
export const ASSET_CATEGORIES: AssetCategory[] = ['LAPTOP', 'MONITOR', 'PHONE', 'ID_CARD', 'ACCESS_CARD', 'HEADSET', 'OTHER'];
export const ASSET_STATUSES: AssetStatus[] = ['AVAILABLE', 'ASSIGNED', 'IN_REPAIR', 'RETIRED'];

export interface Asset {
  id: number;
  assetTag: string;
  name: string;
  category: AssetCategory;
  serialNumber: string | null;
  status: AssetStatus;
  purchaseDate: string | null;
  purchaseCost: number | null;
  notes: string | null;
  currentEmployeeId: number | null;
  currentEmployeeName: string | null;
  currentEmployeeCode: string | null;
}

export interface AssetRequest {
  assetTag: string;
  name: string;
  category: AssetCategory;
  serialNumber?: string | null;
  status?: AssetStatus | null;
  purchaseDate?: string | null;
  purchaseCost?: number | null;
  notes?: string | null;
}

export interface AuditLogEntry {
  id: number;
  entity: string;
  entityId: string | null;
  action: string;
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  actor: string;
  changedAt: string;
}
