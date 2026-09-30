import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatStepperModule } from '@angular/material/stepper';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { debounceTime, distinctUntilChanged, filter, of, switchMap } from 'rxjs';
import { EmployeeService } from '../../core/services/employee.service';
import { OrgService } from '../../core/services/org.service';
import { AuthService } from '../../core/services/auth.service';
import {
  BLOOD_GROUPS, CreateEmployeeResponse, EMPLOYMENT_TYPES, EmployeeDetail, EmployeeLookup, EmployeeRequest,
  GENDERS, MARITAL_STATUSES
} from '../../core/models/employee.models';
import { ApiError } from '../../core/models/common.models';
import { fromIsoDate, toIsoDate } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

/** Date must not be in the future. */
function notFuture(c: AbstractControl): ValidationErrors | null {
  return c.value && (c.value as Date) > new Date() ? { future: true } : null;
}

/**
 * Multi-step Add/Edit employee wizard. Route /employees/new creates; /employees/:id/edit edits
 * (the `id` route param is bound as an input via withComponentInputBinding).
 */
@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, RouterLink, MatStepperModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatAutocompleteModule, MatCheckboxModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, PageHeaderComponent, HumanizePipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-form.component.html'
})
export class EmployeeFormComponent implements OnInit {
  /** Route param; absent when creating. */
  id = input<string>();

  private fb = inject(FormBuilder);
  private employees = inject(EmployeeService);
  private org = inject(OrgService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);
  private clipboard = inject(Clipboard);

  readonly genders = GENDERS;
  readonly maritalStatuses = MARITAL_STATUSES;
  readonly bloodGroups = BLOOD_GROUPS;
  readonly types = EMPLOYMENT_TYPES;
  readonly editableStatuses = ['PROBATION', 'ACTIVE', 'ON_NOTICE'] as const;
  readonly today = new Date();

  lookups = toSignal(this.org.lookups());
  loading = signal(false);
  saving = signal(false);
  existing = signal<EmployeeDetail | null>(null);
  created = signal<CreateEmployeeResponse | null>(null);
  showCtc = signal(this.auth.hasPermission('EMPLOYEE_SENSITIVE_READ'));
  managerOptions = signal<EmployeeLookup[]>([]);

  personal = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    middleName: ['', Validators.maxLength(80)],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    workEmail: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
    personalEmail: ['', [Validators.email, Validators.maxLength(160)]],
    phone: ['', Validators.pattern(/^[+0-9 ()-]{7,30}$/)],
    gender: [null as string | null],
    dateOfBirth: [null as Date | null, notFuture],
    maritalStatus: [null as string | null],
    bloodGroup: [null as string | null],
    nationality: ['', Validators.maxLength(60)],
    currentAddress: ['', Validators.maxLength(500)],
    permanentAddress: ['', Validators.maxLength(500)]
  });

  job = this.fb.group({
    departmentId: [null as number | null, Validators.required],
    designationId: [null as number | null, Validators.required],
    gradeId: [null as number | null],
    locationId: [null as number | null, Validators.required],
    manager: [null as EmployeeLookup | string | null],
    employmentType: ['FULL_TIME', Validators.required],
    status: ['PROBATION'],
    dateOfJoining: [new Date() as Date | null, Validators.required],
    probationEndDate: [null as Date | null],
    noticePeriodDays: [30 as number | null, [Validators.min(0), Validators.max(365)]]
  });

  pay = this.fb.group({
    annualCtc: [null as number | null, [Validators.min(0)]],
    createLogin: [true]
  });

  get isEdit(): boolean {
    return !!this.id();
  }

  constructor() {
    // Type-ahead manager search.
    this.job.controls.manager.valueChanges.pipe(
      filter((v): v is string => typeof v === 'string'),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => (q.trim().length >= 2 ? this.employees.lookup(q, 8) : of([]))),
      takeUntilDestroyed()
    ).subscribe((list) => this.managerOptions.set(list.filter((e) => String(e.id) !== this.id())));
  }

  ngOnInit(): void {
    const id = this.id();
    if (!id) return;
    this.loading.set(true);
    this.employees.get(+id).subscribe({
      next: (e) => {
        this.existing.set(e);
        this.showCtc.set(e.canViewCompensation);
        this.patch(e);
        this.loading.set(false);
      },
      error: () => this.router.navigate(['/employees'])
    });
  }

  displayManager = (m: EmployeeLookup | string | null): string =>
    !m ? '' : typeof m === 'string' ? m : `${m.fullName} (${m.employeeCode})`;

  submit(): void {
    if (this.personal.invalid || this.job.invalid || this.pay.invalid) {
      [this.personal, this.job, this.pay].forEach((g) => g.markAllAsTouched());
      return;
    }
    const managerValue = this.job.value.manager;
    if (typeof managerValue === 'string' && managerValue.trim()) {
      this.job.controls.manager.setErrors({ pick: true });
      return;
    }
    const req = this.buildRequest();
    this.saving.set(true);

    const id = this.id();
    if (id) {
      this.employees.update(+id, req).subscribe({
        next: (e) => {
          this.snack.open(`${e.fullName} updated`, 'OK', { duration: 3000 });
          this.router.navigate(['/employees', e.id]);
        },
        error: (err: HttpErrorResponse) => this.onError(err)
      });
    } else {
      this.employees.create(req).subscribe({
        next: (res) => {
          this.saving.set(false);
          if (res.temporaryPassword) {
            this.created.set(res);      // show credentials once
          } else {
            this.snack.open(`${res.employee.fullName} added as ${res.employee.employeeCode}`, 'OK', { duration: 4000 });
            this.router.navigate(['/employees', res.employee.id]);
          }
        },
        error: (err: HttpErrorResponse) => this.onError(err)
      });
    }
  }

  copyPassword(): void {
    const c = this.created();
    if (c?.temporaryPassword) {
      this.clipboard.copy(`Login: ${c.loginEmail}\nTemporary password: ${c.temporaryPassword}`);
      this.snack.open('Login details copied', undefined, { duration: 2000 });
    }
  }

  addAnother(): void {
    this.created.set(null);
    this.personal.reset();
    this.job.reset({ employmentType: 'FULL_TIME', status: 'PROBATION', dateOfJoining: new Date(), noticePeriodDays: 30 });
    this.pay.reset({ createLogin: true });
  }

  private buildRequest(): EmployeeRequest {
    const p = this.personal.getRawValue();
    const j = this.job.getRawValue();
    const manager = j.manager as EmployeeLookup | null;
    return {
      firstName: p.firstName!.trim(),
      middleName: p.middleName || null,
      lastName: p.lastName!.trim(),
      workEmail: p.workEmail!.trim(),
      personalEmail: p.personalEmail || null,
      phone: p.phone || null,
      gender: (p.gender as EmployeeRequest['gender']) ?? null,
      dateOfBirth: toIsoDate(p.dateOfBirth),
      maritalStatus: (p.maritalStatus as EmployeeRequest['maritalStatus']) ?? null,
      bloodGroup: p.bloodGroup ?? null,
      nationality: p.nationality || null,
      currentAddress: p.currentAddress || null,
      permanentAddress: p.permanentAddress || null,
      departmentId: j.departmentId!,
      designationId: j.designationId!,
      gradeId: j.gradeId ?? null,
      locationId: j.locationId!,
      managerId: manager?.id ?? null,
      employmentType: j.employmentType as EmployeeRequest['employmentType'],
      status: this.isEdit ? (j.status as EmployeeRequest['status']) : null,
      dateOfJoining: toIsoDate(j.dateOfJoining)!,
      probationEndDate: toIsoDate(j.probationEndDate),
      noticePeriodDays: j.noticePeriodDays ?? null,
      annualCtc: this.showCtc() ? this.pay.value.annualCtc ?? null : null,
      createLogin: !this.isEdit && !!this.pay.value.createLogin
    };
  }

  private patch(e: EmployeeDetail): void {
    this.personal.patchValue({
      firstName: e.firstName, middleName: e.middleName ?? '', lastName: e.lastName, workEmail: e.workEmail,
      personalEmail: e.personalEmail ?? '', phone: e.phone ?? '', gender: e.gender, dateOfBirth: fromIsoDate(e.dateOfBirth),
      maritalStatus: e.maritalStatus, bloodGroup: e.bloodGroup, nationality: e.nationality ?? '',
      currentAddress: e.currentAddress ?? '', permanentAddress: e.permanentAddress ?? ''
    });
    this.job.patchValue({
      departmentId: e.departmentId, designationId: e.designationId, gradeId: e.gradeId, locationId: e.locationId,
      manager: e.managerId ? { id: e.managerId, fullName: e.managerName ?? '', employeeCode: e.managerCode ?? '', designation: null } : null,
      employmentType: e.employmentType, status: e.status, dateOfJoining: fromIsoDate(e.dateOfJoining),
      probationEndDate: fromIsoDate(e.probationEndDate), noticePeriodDays: e.noticePeriodDays
    });
    this.pay.patchValue({ annualCtc: e.annualCtc, createLogin: false });
  }

  /** Maps backend field errors onto the matching form controls (the toast shows the summary). */
  private onError(err: HttpErrorResponse): void {
    this.saving.set(false);
    const body = err.error as ApiError | undefined;
    for (const fe of body?.fieldErrors ?? []) {
      const control = this.personal.get(fe.field) ?? this.job.get(fe.field) ?? this.pay.get(fe.field)
        ?? (fe.field === 'managerId' ? this.job.controls.manager : null);
      control?.setErrors({ server: fe.message });
      control?.markAsTouched();
    }
  }
}
