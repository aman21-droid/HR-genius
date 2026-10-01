import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { debounceTime, distinctUntilChanged, of, switchMap, tap, catchError, combineLatest } from 'rxjs';
import { EmployeeService } from '../../core/services/employee.service';
import { OrgService } from '../../core/services/org.service';
import { AuthService } from '../../core/services/auth.service';
import {
  EMPLOYEE_STATUSES, EMPLOYMENT_TYPES, EmployeeFilter, EmployeeSummary
} from '../../core/models/employee.models';
import { PageResponse } from '../../core/models/common.models';
import { saveBlob } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { CellDefDirective, ColumnDef, DataTableComponent } from '../../shared/components/data-table.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe, InitialsPipe } from '../../shared/pipes/labels.pipe';
import { ImportDialogComponent } from './import-dialog.component';

const FILTER_KEY = 'hg_emp_filters';
const VIEW_KEY = 'hg_emp_view';
const EMPTY_PAGE: PageResponse<EmployeeSummary> =
  { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true };

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    FormsModule, RouterLink, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatSlideToggleModule, MatIconModule, MatTooltipModule, MatPaginatorModule,
    PageHeaderComponent, DataTableComponent, CellDefDirective, StatusChipComponent, EmptyStateComponent,
    HumanizePipe, InitialsPipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-list.component.html'
})
export class EmployeeListComponent {
  private employees = inject(EmployeeService);
  private org = inject(OrgService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);

  readonly statuses = EMPLOYEE_STATUSES;
  readonly types = EMPLOYMENT_TYPES;
  readonly canEdit = this.auth.hasPermission('EMPLOYEE_WRITE');
  readonly isManager = this.auth.hasRole('MANAGER');
  readonly fullAccess = this.auth.hasAnyRole(['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'PAYROLL_ADMIN']);

  lookups = toSignal(this.org.lookups());

  filter = signal<EmployeeFilter>(this.readFilter());
  search = signal(this.filter().search ?? '');
  pageIndex = signal(0);
  pageSize = signal(20);
  sort = signal('firstName,asc');
  view = signal<'table' | 'cards'>((localStorage.getItem(VIEW_KEY) as 'table' | 'cards') || 'table');

  loading = signal(true);
  result = signal<PageResponse<EmployeeSummary>>(EMPTY_PAGE);
  exporting = signal(false);

  sortActive = computed(() => this.sort().split(',')[0] ?? '');
  sortDirection = computed(() => (this.sort().split(',')[1] ?? 'asc') as 'asc' | 'desc');
  activeFilterCount = computed(() => {
    const f = this.filter();
    return [f.departmentId, f.locationId, f.designationId, f.status, f.employmentType]
      .filter((v) => v != null).length + (f.teamOnly ? 1 : 0) + (f.includeExited ? 1 : 0);
  });

  readonly columns: ColumnDef<EmployeeSummary>[] = [
    { key: 'name', label: 'Employee', sortable: true, sortKey: 'firstName', pinned: true },
    { key: 'employeeCode', label: 'Code', sortable: true },
    { key: 'designationName', label: 'Designation', sortable: true, sortKey: 'designation.name' },
    { key: 'departmentName', label: 'Department', sortable: true, sortKey: 'department.name' },
    { key: 'locationName', label: 'Location', sortable: true, sortKey: 'location.name' },
    { key: 'managerName', label: 'Manager' },
    { key: 'phone', label: 'Phone', hiddenByDefault: true },
    { key: 'employmentType', label: 'Type', value: (r) => r.employmentType.replace('_', ' ').toLowerCase(), hiddenByDefault: true },
    { key: 'dateOfJoining', label: 'Joined', sortable: true, hiddenByDefault: true },
    { key: 'status', label: 'Status', sortable: true }
  ];

  constructor() {
    const debouncedSearch$ = toObservable(this.search).pipe(debounceTime(300), distinctUntilChanged());
    debouncedSearch$.pipe(takeUntilDestroyed()).subscribe((search) => {
      if ((this.filter().search ?? '') !== search) {
        this.pageIndex.set(0);
        this.filter.update((f) => ({ ...f, search }));
      }
    });

    combineLatest([toObservable(this.filter), toObservable(this.pageIndex), toObservable(this.pageSize), toObservable(this.sort)])
      .pipe(
        debounceTime(0),
        tap(() => this.loading.set(true)),
        switchMap(([filter, page, size, sort]) =>
          this.employees.list(filter, { page, size, sort }).pipe(catchError(() => of(EMPTY_PAGE)))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((res) => {
        this.result.set(res);
        this.loading.set(false);
      });

    // Remember filters and view between visits.
    effect(() => localStorage.setItem(FILTER_KEY, JSON.stringify(this.filter())));
    effect(() => localStorage.setItem(VIEW_KEY, this.view()));
  }

  setFilter(patch: Partial<EmployeeFilter>): void {
    this.pageIndex.set(0);
    this.filter.update((f) => ({ ...f, ...patch }));
  }

  clearFilters(): void {
    this.search.set('');
    this.pageIndex.set(0);
    this.filter.set({});
  }

  onPage(e: PageEvent): void {
    this.pageSize.set(e.pageSize);
    this.pageIndex.set(e.pageIndex);
  }

  onSort(sort: string): void {
    this.sort.set(sort || 'firstName,asc');
  }

  open(row: EmployeeSummary): void {
    this.router.navigate(['/employees', row.id]);
  }

  export(): void {
    this.exporting.set(true);
    this.employees.export(this.filter()).subscribe({
      next: (res) => {
        saveBlob(res, 'employees.xlsx');
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false)
    });
  }

  openImport(): void {
    this.dialog.open(ImportDialogComponent, { width: '720px', maxWidth: '95vw' }).afterClosed()
      .subscribe((created: number | undefined) => {
        if (created) {
          this.filter.update((f) => ({ ...f }));   // refetch
        }
      });
  }

  private readFilter(): EmployeeFilter {
    // A drill-down link (e.g. a department bar on a chart) starts a fresh, focused filter.
    const q = this.route.snapshot.queryParamMap;
    const departmentId = Number(q.get('departmentId')) || null;
    const locationId = Number(q.get('locationId')) || null;
    if (departmentId || locationId) {
      return { departmentId, locationId };
    }
    try {
      const f = JSON.parse(localStorage.getItem(FILTER_KEY) ?? '{}') as EmployeeFilter;
      // Don't restore toggles the user may no longer be entitled to.
      if (!this.auth.hasRole('MANAGER')) f.teamOnly = false;
      if (!this.auth.hasAnyRole(['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'PAYROLL_ADMIN'])) {
        f.includeExited = false;
        if (f.status === 'EXITED') f.status = null;
      }
      return f;
    } catch {
      return {};
    }
  }
}
