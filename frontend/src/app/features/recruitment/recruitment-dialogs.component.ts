import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatSnackBar } from '@angular/material/snack-bar';
import { debounceTime, distinctUntilChanged, filter, of, switchMap } from 'rxjs';
import { OrgService } from '../../core/services/org.service';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { OnboardingService } from '../../core/services/onboarding.service';
import { EMPLOYMENT_TYPES } from '../../core/models/employee.models';
import {
  ApplicationDetail, CANDIDATE_SOURCES, Candidate, CandidateRequest, CandidateSummary, HireResponse, INTERVIEW_MODES,
  Offer, OfferRequest, Requisition, RequisitionRequest
} from '../../core/models/recruitment.models';
import { fromIsoDate, toIsoDate } from '../../core/utils/http.utils';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { EmployeeMultiPickerComponent, EmployeePickerComponent, PickedEmployee } from '../../shared/components/employee-picker.component';

const FIELD_LABELS: Record<string, string> = {
  title: 'Job title', departmentId: 'Department', designationId: 'Designation', locationId: 'Location',
  hiringManager: 'Hiring manager (click a name in the list)', employmentType: 'Employment type', openings: 'Openings'
};

// ============================================================ requisition create / edit
@Component({
  selector: 'app-requisition-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, AsyncPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatCheckboxModule, MatButtonModule, HumanizePipe, EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ req ? 'Edit ' + req.reqCode : 'New requisition' }}</h2>
    <mat-dialog-content>
      @if (lookups$ | async; as lk) {
        <form [formGroup]="form" class="hg-form-grid" id="reqForm" (ngSubmit)="save()">
          <mat-form-field appearance="outline" class="full"><mat-label>Job title</mat-label>
            <input matInput formControlName="title" required maxlength="160" />
            @if (form.controls.title.invalid) { <mat-error>Job title is required</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Department</mat-label>
            <mat-select formControlName="departmentId" required>
              @for (o of lk.departments; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Designation</mat-label>
            <mat-select formControlName="designationId" required>
              @for (o of lk.designations; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Location</mat-label>
            <mat-select formControlName="locationId" required>
              @for (o of lk.locations; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Grade</mat-label>
            <mat-select formControlName="gradeId">
              <mat-option [value]="null">—</mat-option>
              @for (o of lk.grades; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <hg-employee-picker class="full" formControlName="hiringManager" label="Hiring manager" [required]="true"
                              hint="Approves the requisition before HR" />
          <mat-form-field appearance="outline"><mat-label>Employment type</mat-label>
            <mat-select formControlName="employmentType" required>
              @for (t of employmentTypes; track t) { <mat-option [value]="t">{{ t | humanize }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Openings</mat-label>
            <input matInput type="number" min="1" max="500" formControlName="openings" required />
            @if (form.controls.openings.invalid) { <mat-error>1–500</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Min experience (yrs)</mat-label>
            <input matInput type="number" min="0" max="50" step="0.5" formControlName="minExperience" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Max experience (yrs)</mat-label>
            <input matInput type="number" min="0" max="50" step="0.5" formControlName="maxExperience" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Budget min (₹ / yr)</mat-label>
            <input matInput type="number" min="0" formControlName="salaryMin" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Budget max (₹ / yr)</mat-label>
            <input matInput type="number" min="0" formControlName="salaryMax" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Target hire date</mat-label>
            <input matInput [matDatepicker]="td" formControlName="targetDate" />
            <mat-datepicker-toggle matIconSuffix [for]="td" /><mat-datepicker #td /></mat-form-field>
          <mat-checkbox formControlName="publishOnCareers">Show on the careers page once open</mat-checkbox>
          <mat-form-field appearance="outline" class="full"><mat-label>Key skills</mat-label>
            <input matInput formControlName="skills" maxlength="500" placeholder="e.g. Java, Spring Boot, SQL" /></mat-form-field>
          <mat-form-field appearance="outline" class="full"><mat-label>Job description</mat-label>
            <textarea matInput rows="5" formControlName="description" maxlength="4000"></textarea>
            <mat-hint>Shown publicly on the careers page</mat-hint></mat-form-field>
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="reqForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `
})
export class RequisitionDialogComponent {
  req = inject<Requisition | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<RequisitionDialogComponent, Requisition>);
  private recruitment = inject(RecruitmentService);
  private fb = inject(FormBuilder);

  readonly lookups$ = inject(OrgService).lookups();
  readonly employmentTypes = EMPLOYMENT_TYPES;
  private snack = inject(MatSnackBar);
  saving = signal(false);

  form = this.fb.group({
    title: [this.req?.title ?? '', [Validators.required, Validators.maxLength(160)]],
    departmentId: [this.req?.departmentId ?? null as number | null, Validators.required],
    designationId: [this.req?.designationId ?? null as number | null, Validators.required],
    locationId: [this.req?.locationId ?? null as number | null, Validators.required],
    gradeId: [this.req?.gradeId ?? null as number | null],
    hiringManager: [this.req ? { id: this.req.hiringManagerId, fullName: this.req.hiringManagerName } as PickedEmployee : null,
      Validators.required],
    employmentType: [this.req?.employmentType ?? 'FULL_TIME', Validators.required],
    openings: [this.req?.openings ?? 1, [Validators.required, Validators.min(1), Validators.max(500)]],
    minExperience: [this.req?.minExperience ?? null as number | null],
    maxExperience: [this.req?.maxExperience ?? null as number | null],
    salaryMin: [this.req?.salaryMin ?? null as number | null],
    salaryMax: [this.req?.salaryMax ?? null as number | null],
    targetDate: [fromIsoDate(this.req?.targetDate)],
    publishOnCareers: [this.req?.publishOnCareers ?? true],
    skills: [this.req?.skills ?? ''],
    description: [this.req?.description ?? '']
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      const missing = Object.entries(this.form.controls).filter(([, c]) => c.invalid).map(([k]) => FIELD_LABELS[k] ?? k);
      this.snack.open(`Please complete: ${missing.join(', ')}`, 'OK', { duration: 5000 });
      return;
    }
    const v = this.form.getRawValue();
    const body: RequisitionRequest = {
      title: v.title!.trim(), departmentId: v.departmentId!, designationId: v.designationId!, locationId: v.locationId!,
      gradeId: v.gradeId, hiringManagerId: v.hiringManager!.id, employmentType: v.employmentType as RequisitionRequest['employmentType'],
      openings: Number(v.openings), minExperience: numOrNull(v.minExperience), maxExperience: numOrNull(v.maxExperience),
      salaryMin: numOrNull(v.salaryMin), salaryMax: numOrNull(v.salaryMax), targetDate: toIsoDate(v.targetDate),
      publishOnCareers: !!v.publishOnCareers, skills: v.skills?.trim() || null, description: v.description?.trim() || null
    };
    this.saving.set(true);
    (this.req ? this.recruitment.updateRequisition(this.req.id, body) : this.recruitment.createRequisition(body)).subscribe({
      next: (r) => this.ref.close(r),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ candidate create / edit
@Component({
  selector: 'app-candidate-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    HumanizePipe, EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ c ? 'Edit ' + c.fullName : 'Add candidate' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="hg-form-grid" id="candForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>First name</mat-label>
          <input matInput formControlName="firstName" required maxlength="80" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Last name</mat-label>
          <input matInput formControlName="lastName" required maxlength="80" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" required maxlength="160" />
          @if (form.controls.email.hasError('email')) { <mat-error>Enter a valid email</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Phone</mat-label>
          <input matInput formControlName="phone" maxlength="30" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Current company</mat-label>
          <input matInput formControlName="currentCompany" maxlength="120" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Current title</mat-label>
          <input matInput formControlName="currentTitle" maxlength="120" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Total experience (yrs)</mat-label>
          <input matInput type="number" min="0" max="60" step="0.5" formControlName="totalExperience" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Notice period (days)</mat-label>
          <input matInput type="number" min="0" max="365" formControlName="noticePeriodDays" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Current CTC (₹ / yr)</mat-label>
          <input matInput type="number" min="0" formControlName="currentCtc" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Expected CTC (₹ / yr)</mat-label>
          <input matInput type="number" min="0" formControlName="expectedCtc" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>City</mat-label>
          <input matInput formControlName="city" maxlength="80" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Source</mat-label>
          <mat-select formControlName="source">
            @for (s of sources; track s) { <mat-option [value]="s">{{ s | humanize }}</mat-option> }
          </mat-select></mat-form-field>
        @if (form.controls.source.value === 'REFERRAL') {
          <hg-employee-picker class="full" formControlName="referredBy" label="Referred by" />
        }
        <mat-form-field appearance="outline" class="full"><mat-label>LinkedIn URL</mat-label>
          <input matInput formControlName="linkedinUrl" maxlength="300" placeholder="https://" />
          @if (form.controls.linkedinUrl.hasError('pattern')) { <mat-error>Must start with http:// or https://</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Notes</mat-label>
          <textarea matInput rows="2" formControlName="notes" maxlength="2000"></textarea></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="candForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `
})
export class CandidateDialogComponent {
  c = inject<Candidate | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<CandidateDialogComponent, Candidate>);
  private recruitment = inject(RecruitmentService);
  private fb = inject(FormBuilder);

  readonly sources = CANDIDATE_SOURCES.filter((s) => s !== 'CAREERS_PAGE' || this.c?.source === 'CAREERS_PAGE');
  saving = signal(false);

  form = this.fb.group({
    firstName: [this.c?.firstName ?? '', Validators.required],
    lastName: [this.c?.lastName ?? '', Validators.required],
    email: [this.c?.email ?? '', [Validators.required, Validators.email]],
    phone: [this.c?.phone ?? ''],
    currentCompany: [this.c?.currentCompany ?? ''],
    currentTitle: [this.c?.currentTitle ?? ''],
    totalExperience: [this.c?.totalExperience ?? null as number | null],
    noticePeriodDays: [this.c?.noticePeriodDays ?? null as number | null],
    currentCtc: [this.c?.currentCtc ?? null as number | null],
    expectedCtc: [this.c?.expectedCtc ?? null as number | null],
    city: [this.c?.city ?? ''],
    source: [this.c?.source ?? 'DIRECT'],
    referredBy: [this.c?.referredById ? { id: this.c.referredById, fullName: this.c.referredByName ?? '' } as PickedEmployee : null],
    linkedinUrl: [this.c?.linkedinUrl ?? '', Validators.pattern(/^(https?:\/\/.*)?$/)],
    notes: [this.c?.notes ?? '']
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const body: CandidateRequest = {
      firstName: v.firstName!.trim(), lastName: v.lastName!.trim(), email: v.email!.trim(), phone: v.phone || null,
      currentCompany: v.currentCompany || null, currentTitle: v.currentTitle || null,
      totalExperience: numOrNull(v.totalExperience), noticePeriodDays: numOrNull(v.noticePeriodDays),
      currentCtc: numOrNull(v.currentCtc), expectedCtc: numOrNull(v.expectedCtc), city: v.city || null,
      source: v.source as CandidateRequest['source'],
      referredById: v.source === 'REFERRAL' ? v.referredBy?.id ?? null : null,
      linkedinUrl: v.linkedinUrl || null, notes: v.notes || null
    };
    this.saving.set(true);
    (this.c ? this.recruitment.updateCandidate(this.c.id, body) : this.recruitment.createCandidate(body)).subscribe({
      next: (res) => this.ref.close(res),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ add existing candidate to a pipeline
@Component({
  selector: 'app-add-to-pipeline-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Add candidate to {{ req.reqCode }}</h2>
    <mat-dialog-content>
      <p class="muted">Search the talent pool. New to us? Close this and use "New candidate" first.</p>
      <form [formGroup]="form" id="addForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full"><mat-label>Candidate</mat-label>
          <input matInput formControlName="candidate" [matAutocomplete]="auto" placeholder="Name, email or company" cdkFocusInitial />
          <mat-autocomplete #auto [displayWith]="display">
            @for (c of options(); track c.id) {
              <mat-option [value]="c">{{ c.fullName }} <small class="muted">· {{ c.email }}</small></mat-option>
            }
          </mat-autocomplete>
          @if (form.controls.candidate.hasError('pick')) { <mat-error>Pick a candidate from the list</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Note (optional)</mat-label>
          <textarea matInput rows="2" formControlName="coverNote" maxlength="2000"></textarea></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="addForm" [disabled]="saving()">Add to pipeline</button>
    </mat-dialog-actions>
  `,
  styles: `.full { width: 100%; }`
})
export class AddToPipelineDialogComponent {
  req = inject<Requisition>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<AddToPipelineDialogComponent, ApplicationDetail>);
  private recruitment = inject(RecruitmentService);
  private fb = inject(FormBuilder);

  options = signal<CandidateSummary[]>([]);
  saving = signal(false);
  form = this.fb.group({
    candidate: [null as CandidateSummary | string | null, Validators.required],
    coverNote: ['']
  });

  constructor() {
    this.form.controls.candidate.valueChanges.pipe(
      filter((v): v is string => typeof v === 'string'), debounceTime(250), distinctUntilChanged(),
      switchMap((q) => (q.trim().length >= 2 ? this.recruitment.candidates({ search: q }, { size: 8 }) : of(null))),
      takeUntilDestroyed()
    ).subscribe((page) => this.options.set(page?.content ?? []));
  }

  display = (c: CandidateSummary | string | null): string => (c && typeof c !== 'string' ? c.fullName : (c ?? ''));

  save(): void {
    const c = this.form.value.candidate;
    if (!c || typeof c === 'string') {
      this.form.controls.candidate.setErrors({ pick: true });
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.recruitment.createApplication(this.req.id, c.id, null, this.form.value.coverNote || null).subscribe({
      next: (a) => this.ref.close(a),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ free-text reason prompt (reject / decline)
export interface ReasonDialogData {
  title: string;
  message?: string;
  label: string;
  confirmText: string;
  required: boolean;
  danger?: boolean;
}

@Component({
  selector: 'app-reason-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.message) { <p class="muted">{{ data.message }}</p> }
      <mat-form-field appearance="outline" class="full"><mat-label>{{ data.label }}</mat-label>
        <textarea matInput rows="3" [(ngModel)]="text" maxlength="500" cdkFocusInitial></textarea></mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button [color]="data.danger ? 'warn' : 'primary'" [disabled]="data.required && !text.trim()"
              (click)="ref.close(text.trim())">{{ data.confirmText }}</button>
    </mat-dialog-actions>
  `,
  styles: `.full { width: 100%; }`
})
export class ReasonDialogComponent {
  data = inject<ReasonDialogData>(MAT_DIALOG_DATA);
  ref = inject(MatDialogRef<ReasonDialogComponent, string>);
  text = '';
}

// ============================================================ schedule interview
@Component({
  selector: 'app-schedule-interview-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule,
    MatButtonModule, HumanizePipe, EmployeeMultiPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Schedule interview · {{ app.candidate.fullName }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="hg-form-grid" id="intForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>Round</mat-label>
          <input matInput formControlName="roundName" required maxlength="80" placeholder="e.g. Technical round 1" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Mode</mat-label>
          <mat-select formControlName="mode">
            @for (m of modes; track m) { <mat-option [value]="m">{{ m | humanize }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Date</mat-label>
          <input matInput [matDatepicker]="dp" formControlName="date" [min]="today" required />
          <mat-datepicker-toggle matIconSuffix [for]="dp" /><mat-datepicker #dp /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Start time</mat-label>
          <input matInput type="time" formControlName="time" required /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Duration (minutes)</mat-label>
          <mat-select formControlName="durationMinutes">
            @for (d of durations; track d) { <mat-option [value]="d">{{ d }} min</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>{{ form.controls.mode.value === 'IN_PERSON' ? 'Room / address' : 'Meeting link or number' }}</mat-label>
          <input matInput formControlName="locationOrLink" maxlength="300" /></mat-form-field>
        <hg-employee-multi-picker class="full" formControlName="panel" label="Interview panel" />
        @if (form.controls.panel.touched && !form.controls.panel.value?.length) {
          <p class="full err">Add at least one interviewer</p>
        }
        @if (pastError()) { <p class="full err">Pick a time in the future</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="intForm" [disabled]="saving()">Schedule</button>
    </mat-dialog-actions>
  `,
  styles: `.err { color: var(--mat-sys-error, #c62828); font-size: 0.8rem; margin: -0.5rem 0 0; }`
})
export class ScheduleInterviewDialogComponent {
  app = inject<ApplicationDetail>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ScheduleInterviewDialogComponent, boolean>);
  private recruitment = inject(RecruitmentService);
  private fb = inject(FormBuilder);

  readonly modes = INTERVIEW_MODES;
  readonly durations = [30, 45, 60, 90, 120];
  readonly today = new Date();
  saving = signal(false);
  pastError = signal(false);

  form = this.fb.group({
    roundName: ['', Validators.required],
    mode: ['VIDEO'],
    date: [null as Date | null, Validators.required],
    time: ['10:00', Validators.required],
    durationMinutes: [60],
    locationOrLink: [''],
    panel: [[] as PickedEmployee[]]
  });

  save(): void {
    const v = this.form.getRawValue();
    if (this.form.invalid || !v.panel?.length) { this.form.markAllAsTouched(); return; }
    const [hh, mm] = v.time!.split(':').map(Number);
    const d = v.date!;
    const at = new Date(d.getFullYear(), d.getMonth(), d.getDate(), hh, mm);
    this.pastError.set(at.getTime() <= Date.now());
    if (this.pastError()) { return; }
    this.saving.set(true);
    this.recruitment.scheduleInterview(this.app.id, {
      roundName: v.roundName!.trim(), mode: v.mode as 'VIDEO', scheduledAt: at.toISOString(),
      durationMinutes: v.durationMinutes!, locationOrLink: v.locationOrLink || null,
      panelistIds: v.panel.map((p) => p.id)
    }).subscribe({ next: () => this.ref.close(true), error: () => this.saving.set(false) });
  }
}

// ============================================================ offer create / edit
export interface OfferDialogData {
  app: ApplicationDetail;
  offer: Offer | null;
}

@Component({
  selector: 'app-offer-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, AsyncPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatButtonModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.offer ? 'Edit offer' : 'Draft offer' }} · {{ data.app.candidate.fullName }}</h2>
    <mat-dialog-content>
      @if (budget) { <p class="muted">Requisition budget: {{ budget }}</p> }
      @if (lookups$ | async; as lk) {
        <form [formGroup]="form" class="hg-form-grid" id="offerForm" (ngSubmit)="save()">
          <mat-form-field appearance="outline"><mat-label>Designation</mat-label>
            <mat-select formControlName="designationId" required>
              @for (o of lk.designations; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Grade</mat-label>
            <mat-select formControlName="gradeId">
              <mat-option [value]="null">—</mat-option>
              @for (o of lk.grades; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Department</mat-label>
            <mat-select formControlName="departmentId" required>
              @for (o of lk.departments; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Location</mat-label>
            <mat-select formControlName="locationId" required>
              @for (o of lk.locations; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Employment type</mat-label>
            <mat-select formControlName="employmentType">
              @for (t of employmentTypes; track t) { <mat-option [value]="t">{{ t | humanize }}</mat-option> }
            </mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Annual CTC (₹)</mat-label>
            <input matInput type="number" min="0" formControlName="annualCtc" required />
            @if (form.controls.annualCtc.invalid) { <mat-error>Enter the annual CTC</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Joining date</mat-label>
            <input matInput [matDatepicker]="jd" formControlName="joiningDate" [min]="today" required />
            <mat-datepicker-toggle matIconSuffix [for]="jd" /><mat-datepicker #jd /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Offer valid until</mat-label>
            <input matInput [matDatepicker]="ed" formControlName="expiryDate" [min]="today" [max]="form.controls.joiningDate.value" />
            <mat-datepicker-toggle matIconSuffix [for]="ed" /><mat-datepicker #ed /></mat-form-field>
          <mat-form-field appearance="outline" class="full"><mat-label>Internal notes</mat-label>
            <textarea matInput rows="2" formControlName="notes" maxlength="1000"></textarea></mat-form-field>
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="offerForm" [disabled]="saving()">Save draft</button>
    </mat-dialog-actions>
  `
})
export class OfferDialogComponent {
  data = inject<OfferDialogData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<OfferDialogComponent, boolean>);
  private recruitment = inject(RecruitmentService);
  private fb = inject(FormBuilder);

  readonly lookups$ = inject(OrgService).lookups();
  readonly employmentTypes = EMPLOYMENT_TYPES;
  readonly today = new Date();
  saving = signal(false);

  private r = this.data.app.requisition;
  private o = this.data.offer;
  readonly budget = this.r.salaryMin || this.r.salaryMax
    ? `₹${(this.r.salaryMin ?? 0).toLocaleString('en-IN')} – ₹${(this.r.salaryMax ?? 0).toLocaleString('en-IN')}` : '';

  form = this.fb.group({
    designationId: [this.o?.designationId ?? this.r.designationId, Validators.required],
    gradeId: [this.o ? this.o.gradeId : this.r.gradeId],
    departmentId: [this.o?.departmentId ?? this.r.departmentId, Validators.required],
    locationId: [this.o?.locationId ?? this.r.locationId, Validators.required],
    employmentType: [this.o?.employmentType ?? this.r.employmentType],
    annualCtc: [this.o?.annualCtc ?? this.data.app.candidate.expectedCtc ?? null as number | null,
      [Validators.required, Validators.min(0)]],
    joiningDate: [fromIsoDate(this.o?.joiningDate), Validators.required],
    expiryDate: [fromIsoDate(this.o?.expiryDate)],
    notes: [this.o?.notes ?? '']
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const body: OfferRequest = {
      designationId: v.designationId!, gradeId: v.gradeId ?? null, departmentId: v.departmentId!, locationId: v.locationId!,
      employmentType: v.employmentType as OfferRequest['employmentType'], annualCtc: Number(v.annualCtc),
      joiningDate: toIsoDate(v.joiningDate)!, expiryDate: toIsoDate(v.expiryDate), notes: v.notes || null
    };
    this.saving.set(true);
    (this.o ? this.recruitment.updateOffer(this.o.id, body) : this.recruitment.createOffer(this.data.app.id, body)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ convert to employee
export interface HireDialogData {
  app: ApplicationDetail;
  offer: Offer;
}

@Component({
  selector: 'app-hire-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, AsyncPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatCheckboxModule, MatButtonModule, MatIconModule, MatButtonToggleModule, EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!result()) {
      <h2 mat-dialog-title>Convert {{ data.app.candidate.fullName }} to an employee</h2>
      <mat-dialog-content>
        <p class="muted">Creates the employee record from the accepted offer ({{ data.offer.designationName }},
          {{ data.offer.departmentName }}, {{ data.offer.locationName }}).</p>
        <form [formGroup]="form" class="hg-form-grid" id="hireForm" (ngSubmit)="save()">
          <mat-form-field appearance="outline" class="full"><mat-label>Work email</mat-label>
            <input matInput type="email" formControlName="workEmail" required maxlength="160" />
            @if (form.controls.workEmail.invalid) { <mat-error>Enter the new company email</mat-error> }</mat-form-field>
          <hg-employee-picker class="full" formControlName="manager" label="Reporting manager" hint="Defaults to the hiring manager" />
          <mat-form-field appearance="outline"><mat-label>Date of joining</mat-label>
            <input matInput [matDatepicker]="dj" formControlName="dateOfJoining" required />
            <mat-datepicker-toggle matIconSuffix [for]="dj" /><mat-datepicker #dj /></mat-form-field>
          @if (templates$ | async; as templates) {
            <mat-form-field appearance="outline"><mat-label>Onboarding checklist</mat-label>
              <mat-select formControlName="templateId">
                @for (t of templates; track t.id) {
                  @if (t.active) { <mat-option [value]="t.id">{{ t.name }}{{ t.defaultTemplate ? ' (default)' : '' }}</mat-option> }
                }
              </mat-select></mat-form-field>
          }
          <mat-checkbox formControlName="createLogin">Create an HRGenius login</mat-checkbox>
          <mat-checkbox formControlName="startOnboarding">Start onboarding now</mat-checkbox>
        </form>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" type="submit" form="hireForm" [disabled]="saving()">Create employee</button>
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>Welcome aboard, {{ result()!.employeeName }}!</h2>
      <mat-dialog-content>
        <p>Employee <strong>{{ result()!.employeeCode }}</strong> created{{ result()!.onboardingPlanId ? ' and onboarding started' : '' }}.</p>
        @if (result()!.temporaryPassword) {
          <div class="secret" role="group" aria-label="Temporary login">
            <div><small>Login</small><code>{{ result()!.loginEmail }}</code></div>
            <div><small>Temporary password</small><code>{{ result()!.temporaryPassword }}</code></div>
            <button mat-stroked-button type="button" (click)="copy()"><mat-icon>content_copy</mat-icon> Copy</button>
          </div>
          <p class="muted">Share this securely. It is shown only once and cannot be retrieved later.</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-flat-button color="primary" [mat-dialog-close]="result()">Done</button>
      </mat-dialog-actions>
    }
  `,
  styles: `
    .secret { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-end; padding: 0.75rem 1rem; border-radius: 10px;
      background: var(--mat-sys-surface-container, rgba(0,0,0,0.04)); }
    .secret small { display: block; font-size: 0.72rem; opacity: 0.7; }
    .secret code { font-size: 0.95rem; }
  `
})
export class HireDialogComponent {
  data = inject<HireDialogData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<HireDialogComponent, HireResponse>);
  private recruitment = inject(RecruitmentService);
  private clipboard = inject(Clipboard);
  private fb = inject(FormBuilder);

  readonly templates$ = inject(OnboardingService).templates();
  saving = signal(false);
  result = signal<HireResponse | null>(null);

  private c = this.data.app.candidate;
  form = this.fb.group({
    workEmail: [`${slug(this.c.firstName)}.${slug(this.c.lastName)}@hrgenius.com`, [Validators.required, Validators.email]],
    manager: [{ id: this.data.app.requisition.hiringManagerId, fullName: this.data.app.requisition.hiringManagerName } as PickedEmployee | null],
    dateOfJoining: [fromIsoDate(this.data.offer.joiningDate), Validators.required],
    templateId: [null as number | null],
    createLogin: [true],
    startOnboarding: [true]
  });

  constructor() {
    this.templates$.pipe(takeUntilDestroyed()).subscribe((ts) => {
      const def = ts.find((t) => t.defaultTemplate);
      if (def && this.form.controls.templateId.value == null) { this.form.controls.templateId.setValue(def.id); }
    });
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.recruitment.hire(this.data.offer.id, {
      workEmail: v.workEmail!.trim(), managerId: v.manager?.id ?? null, dateOfJoining: toIsoDate(v.dateOfJoining),
      createLogin: !!v.createLogin, startOnboarding: !!v.startOnboarding, onboardingTemplateId: v.templateId
    }).subscribe({
      next: (res) => {
        this.saving.set(false);
        // Keep the dialog open to show the one-time password; otherwise close straight away.
        if (res.temporaryPassword) { this.result.set(res); } else { this.ref.close(res); }
      },
      error: () => this.saving.set(false)
    });
  }

  copy(): void {
    const r = this.result();
    if (r) { this.clipboard.copy(`Login: ${r.loginEmail}\nTemporary password: ${r.temporaryPassword}`); }
  }
}

function numOrNull(v: number | string | null | undefined): number | null {
  return v === null || v === undefined || v === '' ? null : Number(v);
}

function slug(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '');
}
