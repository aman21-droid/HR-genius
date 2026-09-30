import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { PayrollService } from '../../core/services/payroll.service';
import { Adjustment, PayrollRun, PayslipSummary } from '../../core/models/payroll.models';
import { saveBlob } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe, InrPipe } from '../../shared/pipes/labels.pipe';
import { ReasonDialogComponent, ReasonDialogData } from '../recruitment/recruitment-dialogs.component';
import { AdjustmentDialogComponent } from './payroll-dialogs.component';
import { PayslipViewComponent } from './payslip-view.component';

@Component({
  selector: 'app-run-detail',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatTooltipModule, PageHeaderComponent, StatusChipComponent, HumanizePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      @if (run(); as r) {
        <hg-page-header [title]="'Payroll · ' + monthLabel(r.period)"
                        [subtitle]="r.periodStart + ' to ' + r.periodEnd + (r.notes ? ' · ' + r.notes : '')"
                        [breadcrumbs]="[{ label: 'Payroll', link: '/payroll' }, { label: monthLabel(r.period) }]">
          <hg-status [value]="r.status" />
          @if (r.status === 'DRAFT' || r.status === 'CALCULATED') {
            <button mat-stroked-button (click)="calculate()" [disabled]="busy()"><mat-icon>calculate</mat-icon>
              {{ r.status === 'DRAFT' ? 'Calculate' : 'Recalculate' }}</button>
          }
          @if (r.status === 'CALCULATED') {
            <button mat-flat-button color="primary" (click)="submit()" [disabled]="busy()"><mat-icon>send</mat-icon> Submit for approval</button>
          }
          @if (r.status === 'APPROVED') {
            <button mat-stroked-button (click)="bankFile()"><mat-icon>account_balance</mat-icon> Bank file</button>
            <button mat-flat-button color="primary" (click)="markPaid()"><mat-icon>paid</mat-icon> Mark as paid</button>
          }
          @if (r.status === 'PAID') {
            <button mat-stroked-button (click)="bankFile()"><mat-icon>account_balance</mat-icon> Bank file</button>
          }
          @if (r.employeeCount) {
            <button mat-stroked-button (click)="register()"><mat-icon>table_view</mat-icon> Register</button>
          }
          @if (r.status === 'DRAFT' || r.status === 'CALCULATED') {
            <button mat-button color="warn" (click)="remove()">Delete</button>
          }
        </hg-page-header>

        @switch (r.status) {
          @case ('DRAFT') { <p class="banner">Draft: {{ r.calculatedAt ? 'inputs changed since the last calculation; recalculate to update payslips.' : 'calculate to generate payslips.' }}</p> }
          @case ('PENDING_APPROVAL') { <p class="banner">Waiting for HR approval in the Approvals inbox. Payslips are released to employees once approved.</p> }
          @case ('APPROVED') { <p class="banner ok">Approved {{ r.approvedAt | date: 'MMM d, HH:mm' }}. Employees can see their payslips. Download the bank file, transfer salaries, then mark as paid.</p> }
          @case ('PAID') { <p class="banner ok">Paid {{ r.paidAt | date: 'MMM d, y' }} · reference {{ r.paymentReference }}</p> }
        }

        <section class="kpis">
          <div><small>Employees</small><strong>{{ r.employeeCount }}</strong></div>
          <div><small>Gross</small><strong>{{ r.totalGross | inr }}</strong></div>
          <div><small>Deductions</small><strong>{{ r.totalDeductions | inr }}</strong></div>
          <div><small>Net payout</small><strong>{{ r.totalNet | inr }}</strong></div>
          <div><small>Cost to company</small><strong>{{ r.totalEmployerCost | inr }}</strong></div>
        </section>

        <mat-tab-group animationDuration="0ms">
          <mat-tab [label]="'Payslips (' + slips().length + ')'">
            <div class="pad">
              @if (missingBank()) {
                <p class="warn"><mat-icon inline>warning</mat-icon> {{ missingBank() }} employee(s) have no bank details; their rows in the bank file are marked MISSING.</p>
              }
              <mat-form-field appearance="outline" class="search">
                <mat-icon matPrefix>search</mat-icon>
                <input matInput placeholder="Filter by name, code or department" [ngModel]="q()" (ngModelChange)="q.set($event)" />
              </mat-form-field>
              <div class="table-wrap">
                <table class="hg-table">
                  <thead><tr><th>Employee</th><th>Department</th><th class="num">Paid days</th><th class="num">LOP</th>
                    <th class="num">Gross</th><th class="num">Deductions</th><th class="num">Net</th><th></th></tr></thead>
                  <tbody>
                    @for (s of filtered(); track s.id) {
                      <tr (click)="open(s)" class="click">
                        <td><strong>{{ s.employeeName }}</strong> <span class="muted small">{{ s.employeeCode }}</span></td>
                        <td>{{ s.department }}</td>
                        <td class="num">{{ s.paidDays | number: '1.0-1' }}</td>
                        <td class="num">{{ s.lopDays ? (s.lopDays | number: '1.0-1') : '' }}</td>
                        <td class="num">{{ s.grossEarnings | inr }}</td>
                        <td class="num">{{ s.totalDeductions | inr }}</td>
                        <td class="num"><strong>{{ s.netPay | inr }}</strong></td>
                        <td>@if (s.bankDetailsMissing) { <mat-icon class="warnicon" matTooltip="No bank details on file">warning</mat-icon> }</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="8" class="muted">{{ r.status === 'DRAFT' && !r.calculatedAt ? 'Calculate the run to generate payslips.' : 'No payslips match.' }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          </mat-tab>
          <mat-tab [label]="'Adjustments (' + adjustments().length + ')'">
            <div class="pad">
              @if (editable()) {
                <div class="tpl-head"><button mat-stroked-button (click)="addAdjustment()"><mat-icon>add</mat-icon> Add adjustment</button></div>
              }
              @for (a of adjustments(); track a.id) {
                <div class="adj">
                  <mat-icon [class.earn]="a.type === 'EARNING'">{{ a.type === 'EARNING' ? 'add_circle' : 'remove_circle' }}</mat-icon>
                  <div class="grow"><strong>{{ a.label }}</strong> <span class="muted small">· {{ a.employeeName }} ({{ a.employeeCode }}){{ a.type === 'EARNING' && !a.taxable ? ' · non-taxable' : '' }}</span></div>
                  <strong class="amt">{{ a.type === 'EARNING' ? '+' : '−' }}{{ a.amount | inr }}</strong>
                  @if (editable()) {
                    <button mat-icon-button (click)="removeAdjustment(a)" [attr.aria-label]="'Remove ' + a.label"><mat-icon>delete</mat-icon></button>
                  }
                </div>
              } @empty {
                <p class="muted">No one-off bonuses or deductions this month.</p>
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .pad { padding: 1rem 0.25rem; }
    .small { font-size: 0.8rem; }
    .banner { padding: 0.6rem 0.9rem; border-radius: 10px; background: var(--mat-sys-surface-container, rgba(0,0,0,0.04)); margin: 0 0 1rem; font-size: 0.9rem; }
    .banner.ok { background: #dcfce7; color: #166534; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.75rem; margin-bottom: 0.5rem; }
    .kpis > div { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.7rem 1rem; }
    .kpis small { display: block; font-size: 0.75rem; opacity: 0.7; } .kpis strong { font-size: 1.15rem; font-variant-numeric: tabular-nums; }
    .search { width: 100%; max-width: 420px; }
    .warn { color: #92400e; background: #fef3c7; padding: 0.5rem 0.75rem; border-radius: 8px; font-size: 0.86rem; }
    .warnicon { color: #b45309; font-size: 18px; height: 18px; width: 18px; }
    .table-wrap { overflow-x: auto; }
    .hg-table { width: 100%; border-collapse: collapse; }
    .hg-table th, .hg-table td { text-align: left; padding: 0.5rem 0.7rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); white-space: nowrap; }
    .hg-table th { font-size: 0.72rem; text-transform: uppercase; opacity: 0.6; }
    .hg-table .num { text-align: right; font-variant-numeric: tabular-nums; }
    tr.click { cursor: pointer; } tr.click:hover { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    .tpl-head { display: flex; justify-content: flex-end; margin-bottom: 0.5rem; }
    .adj { display: flex; align-items: center; gap: 0.75rem; padding: 0.5rem 0; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .adj .grow { flex: 1; } .adj mat-icon { color: #b91c1c; } .adj mat-icon.earn { color: #15803d; }
    .adj .amt { font-variant-numeric: tabular-nums; }
  `
})
export class RunDetailComponent {
  id = input.required<string>();

  private payroll = inject(PayrollService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);
  private router = inject(Router);

  run = signal<PayrollRun | null>(null);
  slips = signal<PayslipSummary[]>([]);
  adjustments = signal<Adjustment[]>([]);
  busy = signal(false);
  q = signal('');

  editable = computed(() => ['DRAFT', 'CALCULATED'].includes(this.run()?.status ?? ''));
  missingBank = computed(() => this.slips().filter((s) => s.bankDetailsMissing).length);
  filtered = computed(() => {
    const q = this.q().trim().toLowerCase();
    return q ? this.slips().filter((s) => `${s.employeeName} ${s.employeeCode} ${s.department ?? ''}`.toLowerCase().includes(q)) : this.slips();
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    const id = Number(this.id());
    this.payroll.run(id).subscribe((r) => this.run.set(r));
    this.payroll.runPayslips(id).subscribe((s) => this.slips.set(s));
    this.payroll.adjustments(id).subscribe((a) => this.adjustments.set(a));
  }

  calculate(): void {
    this.act(this.payroll.calculate(this.run()!.id), 'Payslips calculated');
  }

  submit(): void {
    const r = this.run()!;
    this.confirm.ask({
      title: `Submit ${this.monthLabel(r.period)} for approval?`,
      message: `${r.employeeCount} payslips, net payout ₹${Math.round(r.totalNet).toLocaleString('en-IN')}. The run is frozen while HR reviews it.`,
      confirmText: 'Submit'
    }).subscribe((yes) => yes && this.act(this.payroll.submit(r.id), 'Sent to HR for approval'));
  }

  markPaid(): void {
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '460px', maxWidth: '95vw',
      data: { title: 'Mark salaries as paid', message: 'Enter the bank batch / UTR reference for the transfer.',
        label: 'Payment reference', confirmText: 'Mark as paid', required: true }
    }).afterClosed().subscribe((ref) => ref && this.act(this.payroll.markPaid(this.run()!.id, ref), 'Run marked as paid'));
  }

  remove(): void {
    const r = this.run()!;
    this.confirm.ask({ title: `Delete the ${this.monthLabel(r.period)} run?`, message: 'Its payslips and adjustments are removed.',
      confirmText: 'Delete', danger: true })
      .subscribe((yes) => yes && this.payroll.deleteRun(r.id).subscribe(() => {
        this.snack.open('Run deleted', undefined, { duration: 2500 });
        this.router.navigate(['/payroll']);
      }));
  }

  register(): void {
    this.payroll.register(this.run()!.id).subscribe((res) => saveBlob(res, 'payroll-register.xlsx'));
  }

  bankFile(): void {
    this.confirm.ask({ title: 'Download the bank transfer file?', message: 'It contains full account numbers. The download is recorded in the audit trail.',
      confirmText: 'Download' })
      .subscribe((yes) => yes && this.payroll.bankFile(this.run()!.id).subscribe((res) => saveBlob(res, 'bank-transfer.xlsx')));
  }

  open(s: PayslipSummary): void {
    this.dialog.open(PayslipViewComponent, { data: s.id, width: '720px', maxWidth: '95vw' });
  }

  addAdjustment(): void {
    this.dialog.open(AdjustmentDialogComponent, { data: this.run()!.id, width: '560px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => { if (ok) { this.snack.open('Adjustment added. Recalculate to apply it.', undefined, { duration: 3000 }); this.reload(); } });
  }

  removeAdjustment(a: Adjustment): void {
    this.act(this.payroll.removeAdjustment(this.run()!.id, a.id), 'Adjustment removed. Recalculate to apply it.');
  }

  monthLabel(period: string): string {
    const [y, m] = period.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  }

  private act(call: Observable<unknown>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => { this.busy.set(false); this.snack.open(message, undefined, { duration: 2500 }); this.reload(); },
      error: () => this.busy.set(false)
    });
  }
}
