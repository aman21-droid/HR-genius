import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { PayrollService } from '../../core/services/payroll.service';
import { CALC_TYPES, PayrollRun, SalaryComponent, SalaryComponentRequest } from '../../core/models/payroll.models';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { EmployeePickerComponent, PickedEmployee } from '../../shared/components/employee-picker.component';

// ============================================================ new run
@Component({
  selector: 'app-new-run-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New payroll run</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="runForm" (ngSubmit)="save()" class="grid">
        <mat-form-field appearance="outline"><mat-label>Month</mat-label>
          <mat-select formControlName="period" required>
            @for (m of months; track m.value) { <mat-option [value]="m.value" [disabled]="taken.includes(m.value)">{{ m.label }}{{ taken.includes(m.value) ? ' (exists)' : '' }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Notes (optional)</mat-label>
          <input matInput formControlName="notes" maxlength="500" /></mat-form-field>
      </form>
      <p class="muted small">Creates a draft. Calculate it to generate payslips for everyone on payroll that month.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="runForm" [disabled]="saving()">Create</button>
    </mat-dialog-actions>
  `,
  styles: `.grid { display: grid; } .small { font-size: 0.82rem; }`
})
export class NewRunDialogComponent {
  readonly taken = inject<string[]>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<NewRunDialogComponent, PayrollRun>);
  private payroll = inject(PayrollService);
  private fb = inject(FormBuilder);

  /** Next month back to twelve months ago. */
  readonly months = Array.from({ length: 13 }, (_, i) => {
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() + 1 - i);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    return { value, label: d.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' }) };
  });
  saving = signal(false);
  form = this.fb.group({
    period: [this.months.find((m) => !this.taken.includes(m.value))?.value ?? null, Validators.required],
    notes: ['']
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.payroll.createRun(this.form.value.period!, this.form.value.notes || null).subscribe({
      next: (r) => this.ref.close(r),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ adjustment
@Component({
  selector: 'app-adjustment-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule,
    MatButtonModule, EmployeePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Add adjustment</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="adjForm" (ngSubmit)="save()" class="hg-form-grid">
        <hg-employee-picker class="full" formControlName="employee" label="Employee" [required]="true" />
        <mat-form-field appearance="outline"><mat-label>Type</mat-label>
          <mat-select formControlName="type">
            <mat-option value="EARNING">Earning (bonus, reimbursement…)</mat-option>
            <mat-option value="DEDUCTION">Deduction (recovery, advance…)</mat-option>
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Amount (₹)</mat-label>
          <input matInput type="number" min="1" formControlName="amount" required /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Label on payslip</mat-label>
          <input matInput formControlName="label" maxlength="80" required placeholder="e.g. Performance bonus" /></mat-form-field>
        @if (form.controls.type.value === 'EARNING') {
          <mat-checkbox formControlName="taxable">Taxable</mat-checkbox>
        }
      </form>
      <p class="muted small">The run goes back to draft; recalculate it to apply the change.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="adjForm" [disabled]="saving()">Add</button>
    </mat-dialog-actions>
  `,
  styles: `.small { font-size: 0.82rem; }`
})
export class AdjustmentDialogComponent {
  private runId = inject<number>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<AdjustmentDialogComponent, boolean>);
  private payroll = inject(PayrollService);
  private fb = inject(FormBuilder);

  saving = signal(false);
  form = this.fb.group({
    employee: [null as PickedEmployee | null, Validators.required],
    type: ['EARNING' as 'EARNING' | 'DEDUCTION'],
    amount: [null as number | null, [Validators.required, Validators.min(1)]],
    label: ['', Validators.required],
    taxable: [true]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.payroll.addAdjustment(this.runId, {
      employeeId: v.employee!.id, type: v.type!, label: v.label!.trim(), amount: Number(v.amount),
      taxable: v.type === 'EARNING' ? !!v.taxable : false
    }).subscribe({ next: () => this.ref.close(true), error: () => this.saving.set(false) });
  }
}

// ============================================================ salary component
@Component({
  selector: 'app-component-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule,
    MatButtonModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ c ? 'Edit ' + c.name : 'New earnings component' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="compForm" (ngSubmit)="save()" class="hg-form-grid">
        <mat-form-field appearance="outline"><mat-label>Code</mat-label>
          <input matInput formControlName="code" required maxlength="20" />
          @if (form.controls.code.hasError('pattern')) { <mat-error>Capital letters, digits, _</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Name on payslip</mat-label>
          <input matInput formControlName="name" required maxlength="80" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Calculation</mat-label>
          <mat-select formControlName="calcType">
            @for (t of calcTypes; track t) { <mat-option [value]="t">{{ t | humanize }}</mat-option> }
          </mat-select></mat-form-field>
        @if (form.controls.calcType.value !== 'BALANCING') {
          <mat-form-field appearance="outline"><mat-label>{{ form.controls.calcType.value === 'FIXED_MONTHLY' ? 'Rupees per month' : 'Percent' }}</mat-label>
            <input matInput type="number" min="0" step="0.01" formControlName="calcValue" required /></mat-form-field>
        }
        <mat-form-field appearance="outline"><mat-label>Display order</mat-label>
          <input matInput type="number" min="0" max="999" formControlName="sortOrder" /></mat-form-field>
        <mat-checkbox formControlName="taxable">Taxable</mat-checkbox>
        <mat-checkbox formControlName="active">Active</mat-checkbox>
      </form>
      <p class="muted small">Changes apply to runs calculated from now on; approved payslips are never altered.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="compForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `.small { font-size: 0.82rem; }`
})
export class ComponentDialogComponent {
  c = inject<SalaryComponent | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ComponentDialogComponent, boolean>);
  private payroll = inject(PayrollService);
  private fb = inject(FormBuilder);

  readonly calcTypes = CALC_TYPES;
  saving = signal(false);
  form = this.fb.group({
    code: [{ value: this.c?.code ?? '', disabled: !!this.c }, [Validators.required, Validators.pattern(/^[A-Z0-9_]{2,20}$/)]],
    name: [this.c?.name ?? '', Validators.required],
    calcType: [this.c?.calcType ?? 'FIXED_MONTHLY'],
    calcValue: [this.c?.calcValue ?? 0, Validators.min(0)],
    sortOrder: [this.c?.sortOrder ?? 50],
    taxable: [this.c?.taxable ?? true],
    active: [this.c?.active ?? true]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const req: SalaryComponentRequest = {
      code: v.code!.trim().toUpperCase(), name: v.name!.trim(), calcType: v.calcType!, calcValue: Number(v.calcValue ?? 0),
      sortOrder: Number(v.sortOrder ?? 0), taxable: !!v.taxable, active: !!v.active
    };
    this.saving.set(true);
    (this.c ? this.payroll.updateComponent(this.c.id, req) : this.payroll.createComponent(req)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}
