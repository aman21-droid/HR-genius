import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PayrollService } from '../../core/services/payroll.service';
import { Payslip } from '../../core/models/payroll.models';
import { saveBlob } from '../../core/utils/http.utils';
import { HumanizePipe, InrPipe } from '../../shared/pipes/labels.pipe';

/** Payslip detail in a dialog (data = payslip id), with a PDF download. */
@Component({
  selector: 'app-payslip-view',
  standalone: true,
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatIconModule, HumanizePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (p(); as p) {
      <h2 mat-dialog-title>Payslip · {{ monthLabel(p.period) }}</h2>
      <mat-dialog-content>
        <div class="who">
          <div><strong>{{ p.employeeName }}</strong> <span class="muted">({{ p.employeeCode }})</span></div>
          <div class="muted small">{{ p.designation }} · {{ p.department }} · {{ p.location }}</div>
        </div>
        <dl class="facts">
          <div><dt>Paid days</dt><dd>{{ p.paidDays }} / {{ p.daysInPeriod }}</dd></div>
          <div><dt>Loss of pay</dt><dd>{{ p.lopDays }} day(s)</dd></div>
          <div><dt>PAN</dt><dd>{{ p.pan || '—' }}</dd></div>
          <div><dt>UAN</dt><dd>{{ p.uan || '—' }}</dd></div>
          <div><dt>Bank</dt><dd>{{ p.bankName || '—' }} {{ p.accountMasked || '' }}</dd></div>
          <div><dt>Tax regime</dt><dd>{{ (p.taxRegime || '') | humanize }}</dd></div>
        </dl>
        <div class="cols">
          <section>
            <h3>Earnings</h3>
            @for (l of p.earnings; track l.code) { <div class="row"><span>{{ l.name }}</span><span>{{ l.amount | inr }}</span></div> }
            <div class="row total"><span>Gross earnings</span><span>{{ p.grossEarnings | inr }}</span></div>
          </section>
          <section>
            <h3>Deductions</h3>
            @for (l of p.deductions; track l.code) { <div class="row"><span>{{ l.name }}</span><span>{{ l.amount | inr }}</span></div> }
            @empty { <div class="row muted"><span>None</span><span></span></div> }
            <div class="row total"><span>Total deductions</span><span>{{ p.totalDeductions | inr }}</span></div>
          </section>
        </div>
        <div class="net"><span>Net pay</span><strong>{{ p.netPay | inr }}</strong></div>
        @if (p.employer.length) {
          <p class="muted small">Employer contributions (not part of net pay):
            @for (l of p.employer; track l.code; let last = $last) { {{ l.name }} {{ l.amount | inr }}{{ last ? '' : ', ' }} }</p>
        }
        <p class="muted small">TDS is an estimate from your current structure.</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Close</button>
        <button mat-flat-button color="primary" (click)="pdf(p)" [disabled]="downloading()"><mat-icon>picture_as_pdf</mat-icon> Download PDF</button>
      </mat-dialog-actions>
    } @else {
      <mat-dialog-content><p class="muted">Loading…</p></mat-dialog-content>
    }
  `,
  styles: `
    .who { margin-bottom: 0.75rem; }
    .small { font-size: 0.82rem; }
    .facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.5rem 1rem; margin: 0 0 1rem; }
    .facts dt { font-size: 0.72rem; opacity: 0.65; margin: 0; } .facts dd { margin: 0; font-size: 0.88rem; }
    @media (max-width: 560px) { .facts { grid-template-columns: 1fr 1fr; } }
    .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; }
    @media (max-width: 560px) { .cols { grid-template-columns: 1fr; } }
    h3 { font-size: 0.85rem; margin: 0 0 0.35rem; text-transform: uppercase; letter-spacing: 0.04em; opacity: 0.7; }
    .row { display: flex; justify-content: space-between; gap: 1rem; font-size: 0.88rem; padding: 0.2rem 0;
      font-variant-numeric: tabular-nums; }
    .row.total { border-top: 1px solid var(--hg-border, rgba(0,0,0,0.12)); margin-top: 0.3rem; padding-top: 0.4rem; font-weight: 600; }
    .net { display: flex; justify-content: space-between; align-items: baseline; margin: 1rem 0 0.5rem; padding: 0.75rem 1rem;
      border-radius: 12px; background: var(--mat-sys-surface-container, rgba(0,0,0,0.04)); }
    .net strong { font-size: 1.4rem; font-variant-numeric: tabular-nums; }
  `
})
export class PayslipViewComponent {
  private id = inject<number>(MAT_DIALOG_DATA);
  private payroll = inject(PayrollService);

  p = signal<Payslip | null>(null);
  downloading = signal(false);

  constructor() {
    this.payroll.payslip(this.id).subscribe((p) => this.p.set(p));
  }

  pdf(p: Payslip): void {
    this.downloading.set(true);
    this.payroll.payslipPdf(p.id).subscribe({
      next: (res) => { saveBlob(res, `payslip-${p.period}.pdf`); this.downloading.set(false); },
      error: () => this.downloading.set(false)
    });
  }

  monthLabel(period: string): string {
    const [y, m] = period.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  }
}
