import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged, filter, of, switchMap } from 'rxjs';
import { OrgService } from '../../core/services/org.service';
import { EmployeeService } from '../../core/services/employee.service';
import { ASSET_CATEGORIES, Asset, AssetRequest } from '../../core/models/org.models';
import { AssetAssignment, EmployeeLookup } from '../../core/models/employee.models';
import { fromIsoDate, toIsoDate } from '../../core/utils/http.utils';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

// ============================================================ create / edit
@Component({
  selector: 'app-asset-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatButtonModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ asset ? 'Edit asset' : 'Add asset' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="hg-form-grid" id="assetForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>Asset tag</mat-label><input matInput formControlName="assetTag" required />
          @if (form.controls.assetTag.hasError('required')) { <mat-error>Asset tag is required</mat-error> }
          @else if (form.controls.assetTag.hasError('pattern')) { <mat-error>Letters, digits or '-'</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Name / model</mat-label><input matInput formControlName="name" required />
          @if (form.controls.name.invalid) { <mat-error>Name is required</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Category</mat-label>
          <mat-select formControlName="category" required>
            @for (c of categories; track c) { <mat-option [value]="c">{{ c | humanize }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Serial number</mat-label><input matInput formControlName="serialNumber" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Purchase date</mat-label>
          <input matInput [matDatepicker]="pd" formControlName="purchaseDate" [max]="today" />
          <mat-datepicker-toggle matIconSuffix [for]="pd" /><mat-datepicker #pd /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Purchase cost</mat-label>
          <input matInput type="number" min="0" formControlName="purchaseCost" /></mat-form-field>
        @if (asset && asset.status !== 'ASSIGNED') {
          <mat-form-field appearance="outline"><mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="AVAILABLE">Available</mat-option>
              <mat-option value="IN_REPAIR">In repair</mat-option>
              <mat-option value="RETIRED">Retired</mat-option>
            </mat-select></mat-form-field>
        }
        <mat-form-field appearance="outline" class="full"><mat-label>Notes</mat-label><input matInput formControlName="notes" /></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="assetForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `
})
export class AssetFormDialogComponent {
  asset = inject<Asset | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<AssetFormDialogComponent, boolean>);
  private org = inject(OrgService);
  private fb = inject(FormBuilder);

  readonly categories = ASSET_CATEGORIES;
  readonly today = new Date();
  saving = signal(false);

  form = this.fb.group({
    assetTag: [this.asset?.assetTag ?? '', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{2,40}$/)]],
    name: [this.asset?.name ?? '', [Validators.required, Validators.maxLength(160)]],
    category: [this.asset?.category ?? 'LAPTOP', Validators.required],
    serialNumber: [this.asset?.serialNumber ?? ''],
    purchaseDate: [fromIsoDate(this.asset?.purchaseDate)],
    purchaseCost: [this.asset?.purchaseCost ?? null as number | null, Validators.min(0)],
    status: [this.asset?.status ?? 'AVAILABLE'],
    notes: [this.asset?.notes ?? '']
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: AssetRequest = {
      assetTag: v.assetTag!.trim().toUpperCase(), name: v.name!.trim(), category: v.category as AssetRequest['category'],
      serialNumber: v.serialNumber || null, purchaseDate: toIsoDate(v.purchaseDate), purchaseCost: v.purchaseCost ?? null,
      notes: v.notes || null,
      // Assigned status is controlled by assign/return, never by this form.
      status: this.asset?.status === 'ASSIGNED' ? null : (v.status as AssetRequest['status'])
    };
    this.saving.set(true);
    (this.asset ? this.org.updateAsset(this.asset.id, req) : this.org.createAsset(req)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ assign
@Component({
  selector: 'app-asset-assign-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatDatepickerModule,
    MatAutocompleteModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Assign {{ asset.assetTag }}</h2>
    <mat-dialog-content>
      <p class="muted">{{ asset.name }}</p>
      <form [formGroup]="form" class="hg-form-grid" id="assignForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full"><mat-label>Employee</mat-label>
          <input matInput formControlName="employee" [matAutocomplete]="auto" placeholder="Type 2+ letters" cdkFocusInitial />
          <mat-autocomplete #auto [displayWith]="display">
            @for (e of options(); track e.id) {
              <mat-option [value]="e">{{ e.fullName }} <small class="muted">· {{ e.employeeCode }}</small></mat-option>
            }
          </mat-autocomplete>
          @if (form.controls.employee.hasError('pick')) { <mat-error>Pick a person from the list</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Assigned on</mat-label>
          <input matInput [matDatepicker]="ad" formControlName="assignedOn" [max]="today" />
          <mat-datepicker-toggle matIconSuffix [for]="ad" /><mat-datepicker #ad /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Notes</mat-label><input matInput formControlName="notes" /></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="assignForm" [disabled]="saving()">Assign</button>
    </mat-dialog-actions>
  `
})
export class AssetAssignDialogComponent {
  asset = inject<Asset>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<AssetAssignDialogComponent, boolean>);
  private org = inject(OrgService);
  private employees = inject(EmployeeService);
  private fb = inject(FormBuilder);

  readonly today = new Date();
  options = signal<EmployeeLookup[]>([]);
  saving = signal(false);

  form = this.fb.group({
    employee: [null as EmployeeLookup | string | null, Validators.required],
    assignedOn: [new Date() as Date | null],
    notes: ['']
  });

  constructor() {
    this.form.controls.employee.valueChanges.pipe(
      filter((v): v is string => typeof v === 'string'), debounceTime(250), distinctUntilChanged(),
      switchMap((q) => (q.trim().length >= 2 ? this.employees.lookup(q, 8) : of([]))), takeUntilDestroyed()
    ).subscribe((list) => this.options.set(list));
  }

  display = (e: EmployeeLookup | string | null): string => (e && typeof e !== 'string' ? `${e.fullName} (${e.employeeCode})` : (e ?? ''));

  save(): void {
    const emp = this.form.value.employee;
    if (!emp || typeof emp === 'string') {
      this.form.controls.employee.setErrors({ pick: true });
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.org.assignAsset(this.asset.id, emp.id, toIsoDate(this.form.value.assignedOn), this.form.value.notes || null)
      .subscribe({ next: () => this.ref.close(true), error: () => this.saving.set(false) });
  }
}

// ============================================================ return
@Component({
  selector: 'app-asset-return-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Return {{ asset.assetTag }}</h2>
    <mat-dialog-content>
      <p class="muted">{{ asset.name }} · held by {{ asset.currentEmployeeName }}</p>
      <form [formGroup]="form" class="hg-form-grid" id="returnForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>Condition</mat-label>
          <mat-select formControlName="condition" required>
            <mat-option value="GOOD">Good</mat-option>
            <mat-option value="FAIR">Fair (normal wear)</mat-option>
            <mat-option value="DAMAGED">Damaged (sends to repair)</mat-option>
            <mat-option value="LOST">Lost</mat-option>
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Returned on</mat-label>
          <input matInput [matDatepicker]="rd" formControlName="returnedOn" [max]="today" />
          <mat-datepicker-toggle matIconSuffix [for]="rd" /><mat-datepicker #rd /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Notes</mat-label><input matInput formControlName="notes" /></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="returnForm" [disabled]="saving()">Record return</button>
    </mat-dialog-actions>
  `
})
export class AssetReturnDialogComponent {
  asset = inject<Asset>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<AssetReturnDialogComponent, boolean>);
  private org = inject(OrgService);
  private fb = inject(FormBuilder);

  readonly today = new Date();
  saving = signal(false);
  form = this.fb.group({ condition: ['GOOD', Validators.required], returnedOn: [new Date() as Date | null], notes: [''] });

  save(): void {
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.org.returnAsset(this.asset.id, v.condition!, toIsoDate(v.returnedOn), v.notes || null)
      .subscribe({ next: () => this.ref.close(true), error: () => this.saving.set(false) });
  }
}

// ============================================================ history
@Component({
  selector: 'app-asset-history-dialog',
  standalone: true,
  imports: [DatePipe, MatDialogModule, MatButtonModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ asset.assetTag }} custody history</h2>
    <mat-dialog-content>
      @for (h of history(); track h.id) {
        <div class="row">
          <strong>{{ h.employeeName ?? 'Former employee' }} <small class="muted">{{ h.employeeCode }}</small></strong>
          <span class="muted">{{ h.assignedOn | date: 'mediumDate' }} – {{ h.returnedOn ? (h.returnedOn | date: 'mediumDate') : 'present' }}</span>
          @if (h.returnCondition) { <span class="muted">Returned: {{ h.returnCondition | humanize }}</span> }
        </div>
      } @empty { <p class="muted">Never assigned.</p> }
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-button mat-dialog-close>Close</button></mat-dialog-actions>
  `,
  styles: [`.row { display: flex; flex-direction: column; padding: 8px 0; border-bottom: 1px solid var(--hg-border); }`]
})
export class AssetHistoryDialogComponent implements OnInit {
  asset = inject<Asset>(MAT_DIALOG_DATA);
  private org = inject(OrgService);
  history = signal<AssetAssignment[]>([]);

  ngOnInit(): void {
    this.org.assetHistory(this.asset.id).subscribe((h) => this.history.set(h));
  }
}
