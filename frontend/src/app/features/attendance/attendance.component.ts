import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AttendanceService } from '../../core/services/attendance.service';
import { CalendarDay, Regularization, RegularizationRequestBody } from '../../core/models/attendance.models';
import { toIsoDate } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe, humanize } from '../../shared/pipes/labels.pipe';

/** Combine a yyyy-MM-dd date with an HH:mm time into an ISO instant, or null if time missing. */
function atTime(dateIso: string, time: string | null | undefined): string | null {
  if (!time) { return null; }
  const [y, m, d] = dateIso.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  return new Date(y, m - 1, d, hh, mm).toISOString();
}

// ============================================================ regularize dialog
@Component({
  selector: 'app-regularize-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Request regularization</h2>
    <mat-dialog-content>
      <p class="muted">Correct a past day where your punches are missing or wrong. It goes to your manager for approval.</p>
      <form [formGroup]="form" class="hg-form-grid" id="regForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>Date</mat-label>
          <input matInput [matDatepicker]="wd" formControlName="workDate" [max]="today" required />
          <mat-datepicker-toggle matIconSuffix [for]="wd" /><mat-datepicker #wd />
          @if (form.controls.workDate.invalid) { <mat-error>Pick the day to fix</mat-error> }
        </mat-form-field>
        <span></span>
        <mat-form-field appearance="outline"><mat-label>Check-in</mat-label>
          <input matInput type="time" formControlName="checkIn" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Check-out</mat-label>
          <input matInput type="time" formControlName="checkOut" /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Reason</mat-label>
          <textarea matInput rows="2" formControlName="reason" maxlength="500" required></textarea>
          @if (form.controls.reason.invalid) { <mat-error>A reason is required</mat-error> }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="regForm" [disabled]="saving()">Submit</button>
    </mat-dialog-actions>
  `
})
export class RegularizeDialogComponent {
  private ref = inject(MatDialogRef<RegularizeDialogComponent, boolean>);
  private attendance = inject(AttendanceService);
  private fb = inject(FormBuilder);

  readonly today = new Date();
  saving = signal(false);

  form = this.fb.group({
    workDate: [null as Date | null, Validators.required],
    checkIn: [''],
    checkOut: [''],
    reason: ['', [Validators.required, Validators.maxLength(500)]]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const dateIso = toIsoDate(v.workDate)!;
    const body: RegularizationRequestBody = {
      workDate: dateIso,
      requestedCheckIn: atTime(dateIso, v.checkIn),
      requestedCheckOut: atTime(dateIso, v.checkOut),
      reason: v.reason!.trim()
    };
    this.saving.set(true);
    this.attendance.regularize(body).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ attendance screen
@Component({
  selector: 'app-attendance',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTooltipModule,
    PageHeaderComponent, StatusChipComponent, EmptyStateComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Attendance" subtitle="Punch in and out, and review your month"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Attendance' }]">
        <button mat-stroked-button (click)="regularize()"><mat-icon>edit_calendar</mat-icon> Regularize</button>
      </hg-page-header>

      <!-- Today's punch card -->
      <section class="punch">
        <div class="now">
          <span class="label">Today · {{ now | date: 'EEE, MMM d' }}</span>
          @if (today(); as t) {
            <div class="times">
              <div><small>In</small><strong>{{ t.checkIn ? (t.checkIn | date: 'HH:mm') : '—' }}</strong></div>
              <div><small>Out</small><strong>{{ t.checkOut ? (t.checkOut | date: 'HH:mm') : '—' }}</strong></div>
              <div><small>Worked</small><strong>{{ hoursOf(t.workedMinutes) }}</strong></div>
            </div>
            <hg-status [value]="t.status" />
          } @else {
            <span class="muted">Not marked yet</span>
          }
        </div>
        <div class="actions">
          @if (!today()?.checkIn) {
            <button mat-flat-button color="primary" (click)="checkIn()" [disabled]="busy()">
              <mat-icon>login</mat-icon> Check in
            </button>
          } @else if (!today()?.checkOut) {
            <button mat-flat-button color="primary" (click)="checkOut()" [disabled]="busy()">
              <mat-icon>logout</mat-icon> Check out
            </button>
          } @else {
            <span class="done"><mat-icon>task_alt</mat-icon> Day complete</span>
          }
        </div>
      </section>

      <!-- Month calendar -->
      <section class="month">
        <header>
          <button mat-icon-button (click)="shiftMonth(-1)" aria-label="Previous month"><mat-icon>chevron_left</mat-icon></button>
          <h2>{{ monthStart() | date: 'MMMM y' }}</h2>
          <button mat-icon-button (click)="shiftMonth(1)" [disabled]="isCurrentMonth()" aria-label="Next month"><mat-icon>chevron_right</mat-icon></button>
        </header>
        @if (loadingCal()) {
          <p class="muted">Loading…</p>
        } @else {
          <div class="grid">
            @for (d of weekdays; track d) { <span class="dow">{{ d }}</span> }
            @for (blank of leadingBlanks(); track $index) { <span class="cell blank"></span> }
            @for (c of cells(); track c.date) {
              <span class="cell" [class.today]="c.date === todayIso"
                    [style.--tone]="toneColor(c.status)"
                    [matTooltip]="tip(c)">
                <span class="dnum">{{ c.date | date: 'd' }}</span>
                @if (c.checkIn) { <span class="ci">{{ c.checkIn | date: 'HH:mm' }}</span> }
              </span>
            }
          </div>
          <div class="legend">
            @for (s of legend; track s.status) {
              <span><i [style.background]="s.color"></i>{{ s.status | humanize }}</span>
            }
          </div>
        }
      </section>

      <!-- Regularization requests -->
      <section class="regs">
        <h2>Regularization requests</h2>
        @if (loadingRegs()) {
          <p class="muted">Loading…</p>
        } @else if (!regs().length) {
          <hg-empty-state icon="history_toggle_off" title="No regularizations"
                          message="If a day's punches are missing, use Regularize to request a correction." />
        } @else {
          <div class="reg-list">
            @for (r of regs(); track r.id) {
              <article class="reg-row">
                <div class="main">
                  <div class="l1">
                    <strong>{{ r.workDate | date: 'EEE, MMM d, y' }}</strong>
                    <span class="t">{{ r.requestedCheckIn ? (r.requestedCheckIn | date: 'HH:mm') : '—' }}
                      → {{ r.requestedCheckOut ? (r.requestedCheckOut | date: 'HH:mm') : '—' }}</span>
                  </div>
                  <p class="reason">{{ r.reason }}</p>
                </div>
                <hg-status [value]="r.status" />
              </article>
            }
          </div>
        }
      </section>
    </div>
  `,
  styles: `
    h2 { font-size: 1.05rem; }
    .punch { display: flex; flex-wrap: wrap; gap: 1rem; justify-content: space-between; align-items: center;
      border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 1rem 1.25rem; margin: 1rem 0 1.5rem; }
    .punch .label { display: block; font-size: 0.85rem; opacity: 0.7; margin-bottom: 0.4rem; }
    .punch .times { display: flex; gap: 1.5rem; margin-bottom: 0.5rem; }
    .punch .times small { display: block; font-size: 0.72rem; opacity: 0.65; }
    .punch .times strong { font-size: 1.15rem; font-variant-numeric: tabular-nums; }
    .punch .done { display: inline-flex; align-items: center; gap: 0.4rem; opacity: 0.7; }
    .month header { display: flex; align-items: center; gap: 0.5rem; }
    .month header h2 { flex: 1; text-align: center; margin: 0; }
    .grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; margin-top: 0.5rem; }
    .dow { text-align: center; font-size: 0.72rem; opacity: 0.6; padding: 0.25rem 0; }
    .cell { position: relative; min-height: 54px; border-radius: 8px; padding: 4px 6px;
      background: color-mix(in srgb, var(--tone, transparent) 16%, transparent);
      border: 1px solid color-mix(in srgb, var(--tone, rgba(0,0,0,0.1)) 40%, transparent); }
    .cell.blank { background: none; border: none; }
    .cell.today { outline: 2px solid var(--mat-sys-primary, #1565c0); outline-offset: -1px; }
    .cell .dnum { font-size: 0.8rem; font-weight: 600; }
    .cell .ci { position: absolute; bottom: 4px; left: 6px; font-size: 0.68rem; opacity: 0.8; font-variant-numeric: tabular-nums; }
    .legend { display: flex; flex-wrap: wrap; gap: 0.75rem 1rem; margin-top: 0.75rem; font-size: 0.78rem; opacity: 0.8; }
    .legend span { display: inline-flex; align-items: center; gap: 0.35rem; }
    .legend i { width: 11px; height: 11px; border-radius: 3px; display: inline-block; }
    .regs { margin-top: 1.5rem; }
    .reg-list { display: grid; gap: 0.5rem; }
    .reg-row { display: flex; align-items: center; gap: 0.75rem; border: 1px solid var(--hg-border, rgba(0,0,0,0.12));
      border-radius: 10px; padding: 0.6rem 0.9rem; }
    .reg-row .main { flex: 1; min-width: 0; }
    .reg-row .l1 { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: baseline; }
    .reg-row .t { font-size: 0.85rem; opacity: 0.75; font-variant-numeric: tabular-nums; }
    .reg-row .reason { margin: 0.2rem 0 0; font-size: 0.85rem; opacity: 0.75; }
  `
})
export class AttendanceComponent {
  private attendance = inject(AttendanceService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  readonly now = new Date();
  readonly todayIso = toIsoDate(this.now)!;
  readonly weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  readonly legend = [
    { status: 'PRESENT', color: '#2e7d32' },
    { status: 'HALF_DAY', color: '#f9a825' },
    { status: 'ON_LEAVE', color: '#1565c0' },
    { status: 'HOLIDAY', color: '#6a1b9a' },
    { status: 'WEEKEND', color: '#9e9e9e' },
    { status: 'ABSENT', color: '#c62828' }
  ];

  monthStart = signal(new Date(this.now.getFullYear(), this.now.getMonth(), 1));
  cells = signal<CalendarDay[]>([]);
  regs = signal<Regularization[]>([]);
  loadingCal = signal(true);
  loadingRegs = signal(true);
  busy = signal(false);

  today = computed(() => this.cells().find((c) => c.date === this.todayIso) ?? null);
  leadingBlanks = computed(() => Array.from({ length: this.monthStart().getDay() }));

  constructor() {
    this.loadCalendar();
    this.loadRegs();
  }

  isCurrentMonth(): boolean {
    const m = this.monthStart();
    return m.getFullYear() === this.now.getFullYear() && m.getMonth() === this.now.getMonth();
  }

  shiftMonth(delta: number): void {
    const m = this.monthStart();
    this.monthStart.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
    this.loadCalendar();
  }

  loadCalendar(): void {
    this.loadingCal.set(true);
    const start = this.monthStart();
    const end = new Date(start.getFullYear(), start.getMonth() + 1, 0);
    this.attendance.calendar(toIsoDate(start)!, toIsoDate(end)!).subscribe({
      next: (days) => { this.cells.set(days); this.loadingCal.set(false); },
      error: () => this.loadingCal.set(false)
    });
  }

  loadRegs(): void {
    this.loadingRegs.set(true);
    this.attendance.myRegularizations().subscribe({
      next: (r) => { this.regs.set(r); this.loadingRegs.set(false); },
      error: () => this.loadingRegs.set(false)
    });
  }

  checkIn(): void {
    this.busy.set(true);
    this.attendance.checkIn().subscribe({
      next: () => { this.snack.open('Checked in', undefined, { duration: 2000 }); this.busy.set(false); this.loadCalendar(); },
      error: () => this.busy.set(false)
    });
  }

  checkOut(): void {
    this.busy.set(true);
    this.attendance.checkOut().subscribe({
      next: () => { this.snack.open('Checked out', undefined, { duration: 2000 }); this.busy.set(false); this.loadCalendar(); },
      error: () => this.busy.set(false)
    });
  }

  regularize(): void {
    this.dialog.open(RegularizeDialogComponent, { width: '560px', maxWidth: '95vw' })
      .afterClosed().subscribe((ok) => {
        if (ok) { this.snack.open('Regularization submitted', undefined, { duration: 2500 }); this.loadRegs(); }
      });
  }

  hoursOf(minutes: number | null | undefined): string {
    if (!minutes) { return '—'; }
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return `${h}h ${String(m).padStart(2, '0')}m`;
  }

  toneColor(status: string): string {
    return this.legend.find((l) => l.status === status)?.color ?? 'transparent';
  }

  tip(c: CalendarDay): string {
    const label = c.label || humanize(c.status);
    if (c.checkIn) {
      const out = c.checkOut ? new Date(c.checkOut).toTimeString().slice(0, 5) : '…';
      return `${label} · ${new Date(c.checkIn).toTimeString().slice(0, 5)}–${out}`;
    }
    return label;
  }
}
