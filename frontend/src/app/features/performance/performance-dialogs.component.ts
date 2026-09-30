import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { PerformanceService } from '../../core/services/performance.service';
import { Goal, ReviewCycle } from '../../core/models/performance.models';
import { fromIsoDate, toIsoDate } from '../../core/utils/http.utils';
import { EmployeePickerComponent, PickedEmployee } from '../../shared/components/employee-picker.component';

// ============================================================ goal create / edit
export interface GoalDialogData {
  reviewId: number;
  goal: Goal | null;
  remainingWeight: number;
}

@Component({
  selector: 'app-goal-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.goal ? 'Edit goal' : 'New goal' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="goalForm" (ngSubmit)="save()" class="hg-form-grid">
        <mat-form-field appearance="outline" class="full"><mat-label>Goal</mat-label>
          <input matInput formControlName="title" required maxlength="200" placeholder="What will be achieved?" /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>How will success be measured?</mat-label>
          <textarea matInput rows="2" formControlName="description" maxlength="1000"></textarea></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Weight (%)</mat-label>
          <input matInput type="number" min="0" max="100" formControlName="weight" required />
          <mat-hint>{{ data.remainingWeight }}% unallocated</mat-hint></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Target date</mat-label>
          <input matInput [matDatepicker]="td" formControlName="targetDate" />
          <mat-datepicker-toggle matIconSuffix [for]="td" /><mat-datepicker #td /></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="goalForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `
})
export class GoalDialogComponent {
  data = inject<GoalDialogData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<GoalDialogComponent, boolean>);
  private performance = inject(PerformanceService);
  private fb = inject(FormBuilder);

  saving = signal(false);
  form = this.fb.group({
    title: [this.data.goal?.title ?? '', Validators.required],
    description: [this.data.goal?.description ?? ''],
    weight: [this.data.goal?.weight ?? this.data.remainingWeight, [Validators.required, Validators.min(0), Validators.max(100)]],
    targetDate: [fromIsoDate(this.data.goal?.targetDate)]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const body = { title: v.title!.trim(), description: v.description || null, weight: Number(v.weight), targetDate: toIsoDate(v.targetDate) };
    this.saving.set(true);
    (this.data.goal ? this.performance.updateGoal(this.data.reviewId, this.data.goal.id, body)
      : this.performance.addGoal(this.data.reviewId, body)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ new cycle
@Component({
  selector: 'app-cycle-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New review cycle</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="cycleForm" (ngSubmit)="save()" class="hg-form-grid">
        <mat-form-field appearance="outline" class="full"><mat-label>Name</mat-label>
          <input matInput formControlName="name" required maxlength="80" placeholder="e.g. H1 2027" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Period start</mat-label>
          <input matInput [matDatepicker]="sd" formControlName="startDate" required />
          <mat-datepicker-toggle matIconSuffix [for]="sd" /><mat-datepicker #sd /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Period end</mat-label>
          <input matInput [matDatepicker]="ed" formControlName="endDate" required [min]="form.controls.startDate.value" />
          <mat-datepicker-toggle matIconSuffix [for]="ed" /><mat-datepicker #ed /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Self-reviews due</mat-label>
          <input matInput [matDatepicker]="sr" formControlName="selfReviewDue" />
          <mat-datepicker-toggle matIconSuffix [for]="sr" /><mat-datepicker #sr /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Manager reviews due</mat-label>
          <input matInput [matDatepicker]="mr" formControlName="managerReviewDue" />
          <mat-datepicker-toggle matIconSuffix [for]="mr" /><mat-datepicker #mr /></mat-form-field>
      </form>
      <p class="muted small">The cycle starts as a draft. Launching it opens a review for every current employee with a manager.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="cycleForm" [disabled]="saving()">Create</button>
    </mat-dialog-actions>
  `,
  styles: `.small { font-size: 0.82rem; }`
})
export class CycleDialogComponent {
  private ref = inject(MatDialogRef<CycleDialogComponent, ReviewCycle>);
  private performance = inject(PerformanceService);
  private fb = inject(FormBuilder);

  saving = signal(false);
  form = this.fb.group({
    name: ['', Validators.required],
    startDate: [null as Date | null, Validators.required],
    endDate: [null as Date | null, Validators.required],
    selfReviewDue: [null as Date | null],
    managerReviewDue: [null as Date | null]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.performance.createCycle({
      name: v.name!.trim(), startDate: toIsoDate(v.startDate)!, endDate: toIsoDate(v.endDate)!,
      selfReviewDue: toIsoDate(v.selfReviewDue), managerReviewDue: toIsoDate(v.managerReviewDue)
    }).subscribe({ next: (c) => this.ref.close(c), error: () => this.saving.set(false) });
  }
}

// ============================================================ give feedback
@Component({
  selector: 'app-feedback-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatButtonToggleModule, EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Give feedback</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="fbForm" (ngSubmit)="save()" class="grid">
        <hg-employee-picker formControlName="to" label="To" [required]="true" />
        <mat-button-toggle-group formControlName="kind" aria-label="Kind of feedback">
          <mat-button-toggle value="PRAISE">🎉 Praise</mat-button-toggle>
          <mat-button-toggle value="CONSTRUCTIVE">💡 Constructive</mat-button-toggle>
        </mat-button-toggle-group>
        @if (form.controls.kind.value === 'PRAISE') {
          <mat-form-field appearance="outline"><mat-label>Who can see it</mat-label>
            <mat-select formControlName="visibility">
              <mat-option value="PUBLIC">Everyone (kudos wall)</mat-option>
              <mat-option value="PRIVATE">Only them and their manager</mat-option>
            </mat-select></mat-form-field>
        } @else {
          <p class="muted small">Constructive feedback is private: only they and their manager can read it.</p>
        }
        <mat-form-field appearance="outline"><mat-label>Message</mat-label>
          <textarea matInput rows="4" formControlName="message" maxlength="1000" required
                    [placeholder]="form.controls.kind.value === 'PRAISE' ? 'What did they do that made a difference?' : 'Be specific and kind: situation, behaviour, impact.'"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="fbForm" [disabled]="saving()">Send</button>
    </mat-dialog-actions>
  `,
  styles: `.grid { display: grid; gap: 0.75rem; } .small { font-size: 0.82rem; margin: 0; }`
})
export class FeedbackDialogComponent {
  private ref = inject(MatDialogRef<FeedbackDialogComponent, boolean>);
  private performance = inject(PerformanceService);
  private fb = inject(FormBuilder);

  saving = signal(false);
  form = this.fb.group({
    to: [inject<PickedEmployee | null>(MAT_DIALOG_DATA, { optional: true }) ?? null, Validators.required],
    kind: ['PRAISE' as 'PRAISE' | 'CONSTRUCTIVE'],
    visibility: ['PUBLIC' as 'PUBLIC' | 'PRIVATE'],
    message: ['', Validators.required]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.performance.give({
      toEmployeeId: v.to!.id, kind: v.kind!, visibility: v.kind === 'CONSTRUCTIVE' ? 'PRIVATE' : v.visibility!,
      message: v.message!.trim()
    }).subscribe({ next: () => this.ref.close(true), error: () => this.saving.set(false) });
  }
}
