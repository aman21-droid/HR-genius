import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { OnboardingService } from '../../core/services/onboarding.service';
import {
  OWNER_ROLES, OnboardingPlan, OnboardingTask, OnboardingTemplate, OwnerRole, TemplateRequest
} from '../../core/models/onboarding.models';
import { fromIsoDate, toIsoDate } from '../../core/utils/http.utils';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { EmployeePickerComponent, PickedEmployee } from '../../shared/components/employee-picker.component';

// ============================================================ start a plan for an existing employee
@Component({
  selector: 'app-start-plan-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, AsyncPipe, MatDialogModule, MatFormFieldModule, MatSelectModule, MatButtonModule,
    EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Start onboarding</h2>
    <mat-dialog-content>
      <p class="muted">Hires converted from Recruitment get a plan automatically. Use this for anyone added directly in Employees.</p>
      <form [formGroup]="form" id="startForm" (ngSubmit)="save()" class="grid">
        <hg-employee-picker formControlName="employee" label="New joiner" [required]="true" />
        @if (templates$ | async; as ts) {
          <mat-form-field appearance="outline"><mat-label>Checklist template</mat-label>
            <mat-select formControlName="templateId">
              @for (t of ts; track t.id) {
                @if (t.active) { <mat-option [value]="t.id">{{ t.name }}{{ t.defaultTemplate ? ' (default)' : '' }}</mat-option> }
              }
            </mat-select></mat-form-field>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="startForm" [disabled]="saving()">Start</button>
    </mat-dialog-actions>
  `,
  styles: `.grid { display: grid; gap: 0.25rem; }`
})
export class StartPlanDialogComponent {
  private ref = inject(MatDialogRef<StartPlanDialogComponent, OnboardingPlan>);
  private onboarding = inject(OnboardingService);
  private fb = inject(FormBuilder);

  readonly templates$ = this.onboarding.templates();
  saving = signal(false);
  form = this.fb.group({
    employee: [null as PickedEmployee | null, Validators.required],
    templateId: [null as number | null]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.onboarding.startPlan(v.employee!.id, v.templateId).subscribe({
      next: (p) => this.ref.close(p),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ reassign a task
@Component({
  selector: 'app-reassign-task-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatButtonModule,
    EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Reassign task</h2>
    <mat-dialog-content>
      <p class="muted">{{ task.title }}</p>
      <form [formGroup]="form" id="reForm" (ngSubmit)="save()" class="grid">
        <hg-employee-picker formControlName="assignee" label="Assignee" hint="Leave empty to return it to the team queue" />
        <mat-form-field appearance="outline"><mat-label>Due date</mat-label>
          <input matInput [matDatepicker]="dd" formControlName="dueDate" />
          <mat-datepicker-toggle matIconSuffix [for]="dd" /><mat-datepicker #dd /></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="reForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `.grid { display: grid; gap: 0.25rem; }`
})
export class ReassignTaskDialogComponent {
  task = inject<OnboardingTask>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ReassignTaskDialogComponent, boolean>);
  private onboarding = inject(OnboardingService);
  private fb = inject(FormBuilder);

  saving = signal(false);
  form = this.fb.group({
    assignee: [this.task.assigneeId ? { id: this.task.assigneeId, fullName: this.task.assigneeName ?? '' } as PickedEmployee : null],
    dueDate: [fromIsoDate(this.task.dueDate)]
  });

  save(): void {
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.onboarding.assignTask(this.task.id, v.assignee?.id ?? null, toIsoDate(v.dueDate)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ template editor
@Component({
  selector: 'app-template-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule,
    MatButtonModule, MatIconModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ tpl ? 'Edit template' : 'New template' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="tplForm" (ngSubmit)="save()">
        <div class="hg-form-grid">
          <mat-form-field appearance="outline"><mat-label>Name</mat-label>
            <input matInput formControlName="name" required maxlength="120" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Description</mat-label>
            <input matInput formControlName="description" maxlength="500" /></mat-form-field>
          <mat-checkbox formControlName="defaultTemplate">Default for new hires</mat-checkbox>
          <mat-checkbox formControlName="active">Active</mat-checkbox>
        </div>
        <h3>Tasks <span class="muted small">· due days are relative to the joining date (negative = before day one)</span></h3>
        <div formArrayName="tasks" class="rows">
          @for (row of tasks.controls; track row; let i = $index) {
            <div class="row" [formGroupName]="i">
              <mat-form-field appearance="outline" class="t"><mat-label>Task</mat-label>
                <input matInput formControlName="title" required maxlength="160" /></mat-form-field>
              <mat-form-field appearance="outline" class="o"><mat-label>Owner</mat-label>
                <mat-select formControlName="ownerRole">
                  @for (r of roles; track r) { <mat-option [value]="r">{{ r | humanize }}</mat-option> }
                </mat-select></mat-form-field>
              <mat-form-field appearance="outline" class="d"><mat-label>Due (days)</mat-label>
                <input matInput type="number" min="-60" max="365" formControlName="dueOffsetDays" /></mat-form-field>
              <div class="moves">
                <button mat-icon-button type="button" (click)="move(i, -1)" [disabled]="i === 0" aria-label="Move up"><mat-icon>arrow_upward</mat-icon></button>
                <button mat-icon-button type="button" (click)="move(i, 1)" [disabled]="i === tasks.length - 1" aria-label="Move down"><mat-icon>arrow_downward</mat-icon></button>
                <button mat-icon-button type="button" (click)="tasks.removeAt(i)" [disabled]="tasks.length === 1" aria-label="Remove task"><mat-icon>delete</mat-icon></button>
              </div>
              <mat-form-field appearance="outline" class="desc"><mat-label>Details (optional)</mat-label>
                <input matInput formControlName="description" maxlength="1000" /></mat-form-field>
            </div>
          }
        </div>
        <button mat-stroked-button type="button" (click)="addTask()"><mat-icon>add</mat-icon> Add task</button>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="tplForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `
    h3 { font-size: 0.92rem; margin: 1rem 0 0.5rem; } .small { font-size: 0.78rem; font-weight: 400; }
    .rows { display: grid; gap: 0.5rem; margin-bottom: 0.75rem; }
    .row { display: grid; grid-template-columns: 1fr 150px 110px auto; gap: 0 0.5rem; align-items: start;
      padding: 0.5rem 0.5rem 0; border: 1px solid var(--hg-border, rgba(0,0,0,0.1)); border-radius: 10px; }
    .row .desc { grid-column: 1 / -1; }
    .moves { display: flex; padding-top: 0.25rem; }
    @media (max-width: 700px) { .row { grid-template-columns: 1fr 1fr; } .moves { grid-column: 1 / -1; } }
  `
})
export class TemplateDialogComponent {
  tpl = inject<OnboardingTemplate | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<TemplateDialogComponent, boolean>);
  private onboarding = inject(OnboardingService);
  private fb = inject(FormBuilder);

  readonly roles = OWNER_ROLES;
  saving = signal(false);

  form = this.fb.group({
    name: [this.tpl?.name ?? '', Validators.required],
    description: [this.tpl?.description ?? ''],
    defaultTemplate: [this.tpl?.defaultTemplate ?? false],
    active: [this.tpl?.active ?? true],
    tasks: this.fb.array((this.tpl?.tasks.length ? this.tpl.tasks : [null]).map((t) => this.taskGroup(
      t ? { title: t.title, description: t.description, ownerRole: t.ownerRole, dueOffsetDays: t.dueOffsetDays } : null)))
  });

  constructor() {
    // The default can only move by making another template the default.
    if (this.tpl?.defaultTemplate) { this.form.controls.defaultTemplate.disable(); }
  }

  get tasks(): FormArray {
    return this.form.controls.tasks as FormArray;
  }

  private taskGroup(t: { title: string; description: string | null; ownerRole: OwnerRole; dueOffsetDays: number } | null) {
    return this.fb.group({
      title: [t?.title ?? '', Validators.required],
      description: [t?.description ?? ''],
      ownerRole: [t?.ownerRole ?? 'HR' as OwnerRole, Validators.required],
      dueOffsetDays: [t?.dueOffsetDays ?? 0, [Validators.required, Validators.min(-60), Validators.max(365)]]
    });
  }

  addTask(): void {
    this.tasks.push(this.taskGroup(null));
  }

  move(i: number, delta: number): void {
    const ctrl = this.tasks.at(i);
    this.tasks.removeAt(i);
    this.tasks.insert(i + delta, ctrl);
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const body: TemplateRequest = {
      name: v.name!.trim(), description: v.description || null,
      defaultTemplate: !!v.defaultTemplate || !!this.tpl?.defaultTemplate, active: !!v.active,
      tasks: (v.tasks as { title: string; description: string; ownerRole: OwnerRole; dueOffsetDays: number }[]).map((t) => ({
        title: t.title.trim(), description: t.description || null, ownerRole: t.ownerRole, dueOffsetDays: Number(t.dueOffsetDays)
      }))
    };
    this.saving.set(true);
    (this.tpl ? this.onboarding.updateTemplate(this.tpl.id, body) : this.onboarding.createTemplate(body)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}
