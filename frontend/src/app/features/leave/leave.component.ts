import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { LeaveService } from '../../core/services/leave.service';
import { ApplyLeaveRequest, LeaveBalance, LeaveRequest, LeaveType } from '../../core/models/leave.models';
import { toIsoDate } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';

// ============================================================ apply-leave dialog
@Component({
  selector: 'app-apply-leave-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatCheckboxModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Apply for leave</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="hg-form-grid" id="leaveForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full"><mat-label>Leave type</mat-label>
          <mat-select formControlName="leaveTypeId" required>
            @for (t of types; track t.id) {
              <mat-option [value]="t.id">{{ t.name }} ({{ t.code }})</mat-option>
            }
          </mat-select>
          @if (form.controls.leaveTypeId.invalid) { <mat-error>Pick a leave type</mat-error> }
        </mat-form-field>

        <mat-form-field appearance="outline"><mat-label>From</mat-label>
          <input matInput [matDatepicker]="sd" formControlName="startDate" required />
          <mat-datepicker-toggle matIconSuffix [for]="sd" /><mat-datepicker #sd />
          @if (form.controls.startDate.invalid) { <mat-error>Start date is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline"><mat-label>To</mat-label>
          <input matInput [matDatepicker]="ed" formControlName="endDate" [min]="form.controls.startDate.value" required />
          <mat-datepicker-toggle matIconSuffix [for]="ed" /><mat-datepicker #ed />
          @if (form.controls.endDate.hasError('required')) { <mat-error>End date is required</mat-error> }
          @else if (form.controls.endDate.hasError('order')) { <mat-error>End can't be before start</mat-error> }
        </mat-form-field>

        @if (selectedType()?.allowHalfDay) {
          <mat-checkbox formControlName="halfDayStart">Half day on first day</mat-checkbox>
          <mat-checkbox formControlName="halfDayEnd">Half day on last day</mat-checkbox>
        }

        <mat-form-field appearance="outline" class="full"><mat-label>Reason</mat-label>
          <textarea matInput rows="2" formControlName="reason" maxlength="500"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="leaveForm" [disabled]="saving()">Submit</button>
    </mat-dialog-actions>
  `
})
export class ApplyLeaveDialogComponent {
  types = inject<LeaveType[]>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ApplyLeaveDialogComponent, boolean>);
  private leave = inject(LeaveService);
  private fb = inject(FormBuilder);

  saving = signal(false);

  form = this.fb.group({
    leaveTypeId: [null as number | null, Validators.required],
    startDate: [null as Date | null, Validators.required],
    endDate: [null as Date | null, Validators.required],
    halfDayStart: [false],
    halfDayEnd: [false],
    reason: ['']
  }, { validators: (g) => {
    const s = g.get('startDate')!.value as Date | null;
    const e = g.get('endDate')!.value as Date | null;
    return s && e && e < s ? { order: true } : null;
  } });

  selectedType = computed(() => {
    const id = this.typeId();
    return this.types.find((t) => t.id === id) ?? null;
  });
  private typeId = signal<number | null>(null);

  constructor() {
    this.form.controls.leaveTypeId.valueChanges.subscribe((v) => this.typeId.set(v));
    // Surface the cross-field order error on the endDate control for display.
    this.form.statusChanges.subscribe(() => {
      const ctrl = this.form.controls.endDate;
      if (this.form.hasError('order')) { ctrl.setErrors({ ...(ctrl.errors ?? {}), order: true }); }
    });
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const type = this.selectedType();
    const req: ApplyLeaveRequest = {
      leaveTypeId: v.leaveTypeId!,
      startDate: toIsoDate(v.startDate)!,
      endDate: toIsoDate(v.endDate)!,
      halfDayStart: type?.allowHalfDay ? !!v.halfDayStart : false,
      halfDayEnd: type?.allowHalfDay ? !!v.halfDayEnd : false,
      reason: v.reason?.trim() || null
    };
    this.saving.set(true);
    this.leave.apply(req).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ leave screen
@Component({
  selector: 'app-leave',
  standalone: true,
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatIconModule, MatMenuModule,
    PageHeaderComponent, StatusChipComponent, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Leave" subtitle="Your balances and leave applications"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Leave' }]">
        <button mat-flat-button color="primary" (click)="apply()" [disabled]="!types().length">
          <mat-icon>add</mat-icon> Apply for leave
        </button>
      </hg-page-header>

      <h2 class="section">Balances <small class="muted">· {{ year }}</small></h2>
      @if (loadingBalances()) {
        <p class="muted">Loading…</p>
      } @else if (!balances().length) {
        <hg-empty-state icon="beach_access" title="No balances yet"
                        message="Your leave balances will appear once HR sets up your entitlements." />
      } @else {
        <div class="balances">
          @for (b of balances(); track b.leaveTypeId) {
            <article class="bal" [style.--accent]="b.color || '#607d8b'">
              <header><span class="code">{{ b.code }}</span><span class="name">{{ b.name }}</span></header>
              <div class="avail"><strong>{{ b.available | number: '1.0-1' }}</strong><span>available</span></div>
              <div class="balance-meter" role="img" [attr.aria-label]="b.name + ': ' + b.used + ' days used, ' + b.pending + ' pending, ' + b.available + ' available'">
                <span class="used" [style.flex-grow]="b.used > 0 ? b.used : 0" [title]="b.used + ' days used'"></span>
                <span class="pending" [style.flex-grow]="b.pending > 0 ? b.pending : 0" [title]="b.pending + ' days pending'"></span>
                <span class="available" [style.flex-grow]="b.available > 0 ? b.available : 0" [title]="b.available + ' days available'"></span>
              </div>
              <div class="meter-key"><span><i class="used"></i>Used</span><span><i class="pending"></i>Pending</span><span><i class="available"></i>Available</span></div>
              <dl class="break">
                <div><dt>Entitlement</dt><dd>{{ b.annualEntitlement | number: '1.0-1' }}</dd></div>
                <div><dt>Accrued</dt><dd>{{ b.accrued | number: '1.0-1' }}</dd></div>
                <div><dt>Used</dt><dd>{{ b.used | number: '1.0-1' }}</dd></div>
                <div><dt>Pending</dt><dd>{{ b.pending | number: '1.0-1' }}</dd></div>
              </dl>
            </article>
          }
        </div>
      }

      <h2 class="section">My requests</h2>
      @if (loadingRequests()) {
        <p class="muted">Loading…</p>
      } @else if (!requests().length) {
        <hg-empty-state icon="event_note" title="No leave requests"
                        message="Applications you submit will be listed here with their approval status." />
      } @else {
        <div class="req-list">
          @for (r of requests(); track r.id) {
            <article class="req-row">
              <span class="swatch" [style.background]="r.color || '#607d8b'" aria-hidden="true"></span>
              <div class="main">
                <div class="line1">
                  <strong>{{ r.leaveTypeName }}</strong>
                  <span class="dates">{{ r.startDate | date: 'MMM d' }} – {{ r.endDate | date: 'MMM d, y' }}</span>
                  <span class="days">{{ r.days | number: '1.0-1' }} day(s)</span>
                </div>
                @if (r.reason) { <p class="reason">{{ r.reason }}</p> }
              </div>
              <hg-status [value]="r.status" />
              @if (r.status === 'PENDING' || r.status === 'APPROVED') {
                <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Actions"><mat-icon>more_vert</mat-icon></button>
                <mat-menu #menu="matMenu">
                  <button mat-menu-item (click)="cancel(r)"><mat-icon>cancel</mat-icon><span>Cancel request</span></button>
                </mat-menu>
              }
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .balance-meter { height: 7px; border-radius: 6px; overflow: hidden; display: flex; background: var(--hg-bg); margin: 14px 0 8px; gap: 2px; }
    .balance-meter span { flex-basis: 0; transition: flex-grow 220ms ease; }
    .used { background: var(--hg-lavender); } .pending { background: var(--hg-warning); } .available { background: var(--hg-primary); }
    .meter-key { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; font-size: 9px; color: var(--hg-muted); }
    .meter-key i { display: inline-block; height: 5px; width: 5px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
    .section { font-size: 1.05rem; margin: 1.5rem 0 0.75rem; }
    .section:first-of-type { margin-top: 1rem; }
    .balances { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.75rem; }
    .bal { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-left: 4px solid var(--accent); border-radius: 12px; padding: 0.9rem 1rem; }
    .bal header { display: flex; align-items: baseline; gap: 0.5rem; }
    .bal .code { font-weight: 700; color: var(--hg-primary); }
    .bal .name { font-size: 0.85rem; opacity: 0.8; }
    .bal .avail { display: flex; align-items: baseline; gap: 0.4rem; margin: 0.4rem 0 0.6rem; }
    .bal .avail strong { font-size: 1.8rem; line-height: 1; }
    .bal .avail span { font-size: 0.78rem; opacity: 0.7; }
    .bal .break { display: grid; grid-template-columns: 1fr 1fr; gap: 0.2rem 0.75rem; margin: 0; }
    .bal .break div { display: flex; justify-content: space-between; font-size: 0.8rem; }
    .bal dt { color: var(--hg-muted); margin: 0; } .bal dd { margin: 0; font-weight: 600; }
    .req-list { display: grid; gap: 0.5rem; }
    .req-row { display: flex; align-items: center; gap: 0.75rem; border: 1px solid var(--hg-border, rgba(0,0,0,0.12));
      border-radius: 10px; padding: 0.6rem 0.9rem; }
    .req-row .swatch { width: 10px; height: 10px; border-radius: 50%; flex: none; }
    .req-row .main { flex: 1; min-width: 0; }
    .req-row .line1 { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: baseline; }
    .req-row .dates { opacity: 0.85; } .req-row .days { font-size: 0.8rem; opacity: 0.7; }
    .req-row .reason { margin: 0.2rem 0 0; font-size: 0.85rem; opacity: 0.75; }
  `
})
export class LeaveComponent {
  private leave = inject(LeaveService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  readonly year = new Date().getFullYear();

  balances = signal<LeaveBalance[]>([]);
  requests = signal<LeaveRequest[]>([]);
  types = signal<LeaveType[]>([]);
  loadingBalances = signal(true);
  loadingRequests = signal(true);

  constructor() {
    this.leave.types(true).subscribe((t) => this.types.set(t));
    this.loadBalances();
    this.loadRequests();
  }

  loadBalances(): void {
    this.loadingBalances.set(true);
    this.leave.myBalances(this.year).subscribe({
      next: (b) => { this.balances.set(b); this.loadingBalances.set(false); },
      error: () => this.loadingBalances.set(false)
    });
  }

  loadRequests(): void {
    this.loadingRequests.set(true);
    this.leave.myRequests().subscribe({
      next: (r) => { this.requests.set(r); this.loadingRequests.set(false); },
      error: () => this.loadingRequests.set(false)
    });
  }

  apply(): void {
    this.dialog.open(ApplyLeaveDialogComponent, { data: this.types(), width: '560px', maxWidth: '95vw' })
      .afterClosed().subscribe((ok) => {
        if (ok) {
          this.snack.open('Leave request submitted', undefined, { duration: 2500 });
          this.reload();
        }
      });
  }

  cancel(r: LeaveRequest): void {
    this.confirm.ask({
      title: 'Cancel this leave request?',
      message: `${r.leaveTypeName}, ${r.days} day(s) from ${r.startDate}. This can't be undone.`,
      confirmText: 'Cancel request', danger: true
    }).subscribe((yes) => yes && this.leave.cancel(r.id).subscribe(() => {
      this.snack.open('Leave request cancelled', undefined, { duration: 2500 });
      this.reload();
    }));
  }

  private reload(): void {
    forkJoin({ b: this.leave.myBalances(this.year), r: this.leave.myRequests() }).subscribe(({ b, r }) => {
      this.balances.set(b);
      this.requests.set(r);
    });
  }
}
