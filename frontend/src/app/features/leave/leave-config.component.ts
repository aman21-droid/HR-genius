import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LeaveService } from '../../core/services/leave.service';
import { ACCRUAL_METHODS, LeaveType, LeaveTypeRequest } from '../../core/models/leave.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

// ============================================================ leave-type dialog
@Component({
  selector: 'app-leave-type-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MatButtonModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ type ? 'Edit leave type' : 'New leave type' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="hg-form-grid" id="ltForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>Code</mat-label>
          <input matInput formControlName="code" required [readonly]="!!type" maxlength="20" />
          @if (form.controls.code.hasError('required')) { <mat-error>Code is required</mat-error> }
          @else if (form.controls.code.hasError('pattern')) { <mat-error>Letters, digits, '-' or '_'</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Name</mat-label>
          <input matInput formControlName="name" required maxlength="80" />
          @if (form.controls.name.invalid) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Description</mat-label>
          <input matInput formControlName="description" maxlength="300" /></mat-form-field>

        <mat-form-field appearance="outline"><mat-label>Colour</mat-label>
          <input matInput type="color" formControlName="color" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Annual entitlement</mat-label>
          <input matInput type="number" min="0" max="366" step="0.5" formControlName="annualEntitlement" required />
          @if (form.controls.annualEntitlement.invalid) { <mat-error>0–366</mat-error> }
        </mat-form-field>

        <mat-form-field appearance="outline"><mat-label>Accrual method</mat-label>
          <mat-select formControlName="accrualMethod" required>
            @for (m of methods; track m) { <mat-option [value]="m">{{ m | humanize }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Accrual rate</mat-label>
          <input matInput type="number" min="0" max="366" step="0.25" formControlName="accrualRate" required />
        </mat-form-field>

        <mat-form-field appearance="outline"><mat-label>Carry-forward cap</mat-label>
          <input matInput type="number" min="0" max="366" step="0.5" formControlName="carryForwardCap" required />
        </mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Max balance</mat-label>
          <input matInput type="number" min="0" max="366" step="0.5" formControlName="maxBalance" placeholder="No cap" />
        </mat-form-field>

        <div class="toggles full">
          <mat-checkbox formControlName="paid">Paid</mat-checkbox>
          <mat-checkbox formControlName="allowHalfDay">Allow half day</mat-checkbox>
          <mat-checkbox formControlName="encashable">Encashable</mat-checkbox>
          <mat-checkbox formControlName="requiresApproval">Requires approval</mat-checkbox>
          <mat-checkbox formControlName="active">Active</mat-checkbox>
        </div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="ltForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `.toggles { display: flex; flex-wrap: wrap; gap: 0.75rem 1.5rem; }`
})
export class LeaveTypeDialogComponent {
  type = inject<LeaveType | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<LeaveTypeDialogComponent, boolean>);
  private leave = inject(LeaveService);
  private fb = inject(FormBuilder);

  readonly methods = ACCRUAL_METHODS;
  saving = signal(false);

  form = this.fb.group({
    code: [this.type?.code ?? '', [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]{1,20}$/)]],
    name: [this.type?.name ?? '', [Validators.required, Validators.maxLength(80)]],
    description: [this.type?.description ?? ''],
    color: [this.type?.color ?? '#607d8b'],
    annualEntitlement: [this.type?.annualEntitlement ?? 0, [Validators.required, Validators.min(0), Validators.max(366)]],
    accrualMethod: [this.type?.accrualMethod ?? 'ANNUAL', Validators.required],
    accrualRate: [this.type?.accrualRate ?? 0, [Validators.required, Validators.min(0), Validators.max(366)]],
    carryForwardCap: [this.type?.carryForwardCap ?? 0, [Validators.required, Validators.min(0), Validators.max(366)]],
    maxBalance: [this.type?.maxBalance ?? null as number | null, [Validators.min(0), Validators.max(366)]],
    paid: [this.type?.paid ?? true],
    allowHalfDay: [this.type?.allowHalfDay ?? true],
    encashable: [this.type?.encashable ?? false],
    requiresApproval: [this.type?.requiresApproval ?? true],
    active: [this.type?.active ?? true]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const req: LeaveTypeRequest = {
      code: v.code!.trim().toUpperCase(),
      name: v.name!.trim(),
      description: v.description?.trim() || null,
      color: v.color || null,
      paid: !!v.paid,
      annualEntitlement: Number(v.annualEntitlement),
      accrualMethod: v.accrualMethod as LeaveTypeRequest['accrualMethod'],
      accrualRate: Number(v.accrualRate),
      carryForwardCap: Number(v.carryForwardCap),
      maxBalance: v.maxBalance === null || v.maxBalance === undefined ? null : Number(v.maxBalance),
      allowHalfDay: !!v.allowHalfDay,
      encashable: !!v.encashable,
      requiresApproval: !!v.requiresApproval,
      active: !!v.active
    };
    this.saving.set(true);
    (this.type ? this.leave.updateType(this.type.id, req) : this.leave.createType(req)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ config screen
@Component({
  selector: 'app-leave-config',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatIconModule, MatMenuModule, MatSlideToggleModule,
    PageHeaderComponent, StatusChipComponent, EmptyStateComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Leave configuration" subtitle="Leave types, accrual rules and the accrual job"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Leave config' }]">
        <button mat-stroked-button (click)="runAccrual()" [disabled]="accruing()">
          <mat-icon>autorenew</mat-icon> Run accrual
        </button>
        <button mat-flat-button color="primary" (click)="edit(null)"><mat-icon>add</mat-icon> New type</button>
      </hg-page-header>

      @if (loading()) {
        <p class="muted">Loading…</p>
      } @else if (!types().length) {
        <hg-empty-state icon="beach_access" title="No leave types"
                        message="Create your first leave type to start tracking balances." />
      } @else {
        <div class="table-wrap">
          <table class="hg-table">
            <thead>
              <tr>
                <th>Type</th><th class="num">Entitlement</th><th>Accrual</th>
                <th class="num">Carry-fwd</th><th>Flags</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (t of types(); track t.id) {
                <tr>
                  <td>
                    <span class="swatch" [style.background]="t.color || '#607d8b'"></span>
                    <span class="tname"><strong>{{ t.code }}</strong><small>{{ t.name }}</small></span>
                  </td>
                  <td class="num">{{ t.annualEntitlement | number: '1.0-1' }}</td>
                  <td>{{ t.accrualMethod | humanize }}@if (t.accrualMethod !== 'NONE') { · {{ t.accrualRate | number: '1.0-2' }} }</td>
                  <td class="num">{{ t.carryForwardCap | number: '1.0-1' }}</td>
                  <td class="flags">
                    @if (t.paid) { <span class="flag">Paid</span> }
                    @if (t.allowHalfDay) { <span class="flag">½-day</span> }
                    @if (t.encashable) { <span class="flag">Encash</span> }
                    @if (!t.requiresApproval) { <span class="flag">Auto</span> }
                  </td>
                  <td><hg-status [value]="t.active ? 'ACTIVE' : 'EXPIRED'" /></td>
                  <td class="end">
                    <button mat-icon-button [matMenuTriggerFor]="menu" [attr.aria-label]="'Actions for ' + t.code"><mat-icon>more_vert</mat-icon></button>
                    <mat-menu #menu="matMenu">
                      <button mat-menu-item (click)="edit(t)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                      <button mat-menu-item (click)="remove(t)"><mat-icon>delete</mat-icon><span>Delete</span></button>
                    </mat-menu>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: `
    .table-wrap { overflow-x: auto; }
    .hg-table { width: 100%; border-collapse: collapse; }
    .hg-table th, .hg-table td { text-align: left; padding: 0.6rem 0.75rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .hg-table th { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.03em; opacity: 0.6; }
    .hg-table .num { text-align: right; font-variant-numeric: tabular-nums; }
    .hg-table .end { text-align: right; }
    .swatch { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 0.5rem; vertical-align: middle; }
    .tname { display: inline-flex; flex-direction: column; vertical-align: middle; }
    .tname small { opacity: 0.7; }
    .flags { display: flex; flex-wrap: wrap; gap: 0.3rem; }
    .flag { font-size: 0.72rem; padding: 0.1rem 0.45rem; border-radius: 999px; background: var(--hg-border, rgba(0,0,0,0.08)); }
  `
})
export class LeaveConfigComponent {
  private leave = inject(LeaveService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  types = signal<LeaveType[]>([]);
  loading = signal(true);
  accruing = signal(false);

  constructor() { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.leave.types(false).subscribe({
      next: (t) => { this.types.set(t); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  edit(type: LeaveType | null): void {
    this.dialog.open(LeaveTypeDialogComponent, { data: type, width: '680px', maxWidth: '95vw' })
      .afterClosed().subscribe((ok) => {
        if (ok) { this.snack.open('Leave type saved', undefined, { duration: 2500 }); this.reload(); }
      });
  }

  remove(type: LeaveType): void {
    this.confirm.ask({
      title: `Delete ${type.code}?`,
      message: 'Existing balances and history are kept; the type is removed from new applications.',
      confirmText: 'Delete', danger: true
    }).subscribe((yes) => yes && this.leave.deleteType(type.id).subscribe(() => {
      this.snack.open(`${type.code} deleted`, undefined, { duration: 2500 });
      this.reload();
    }));
  }

  runAccrual(): void {
    this.confirm.ask({
      title: 'Run the accrual job now?',
      message: 'Credits this period’s accrual to every active employee. Safe to run more than once — it is idempotent per period.',
      confirmText: 'Run now'
    }).subscribe((yes) => {
      if (!yes) { return; }
      this.accruing.set(true);
      this.leave.runAccrual().subscribe({
        next: (res) => {
          const credited = (res?.['credited'] ?? res?.['updated'] ?? 0) as number;
          this.snack.open(`Accrual complete · ${credited} balance(s) credited`, undefined, { duration: 3500 });
          this.accruing.set(false);
        },
        error: () => this.accruing.set(false)
      });
    });
  }
}
