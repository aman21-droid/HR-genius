export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

/** Standard error body produced by the backend's GlobalExceptionHandler. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: FieldValidationError[];
}

export interface LookupItem {
  id: number;
  code: string;
  name: string;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string; // e.g. "firstName,asc"
}
