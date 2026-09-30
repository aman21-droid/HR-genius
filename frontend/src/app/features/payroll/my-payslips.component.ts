import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { PayrollService } from '../../core/services/payroll.service';
import { PayrollRun, SalaryStructure } from '../../core/models/payroll.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe, InrPipe } from '../../shared/pipes/labels.pipe';
import { PayslipViewComponent } from './payslip-view.component';

/** Employee self-service: released payslips and the salary structure behind them. */
@Component({
  selector: 'app-my-payslips',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, PageHeaderComponent, EmptyStateComponent, HumanizePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="My payslips" subtitle="Monthly payslips and your salary structure"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'My payslips' }]" />

      @if (structure(); as s) {
        <section class="structure">
          <div class="head">
            <div><small>Annual CTC</small><strong>{{ s.annualCtc | inr }}</strong></div>
            <div><small>Monthly gross</small><strong>{{ s.monthlyGross | inr }}</strong></div>
            <div><small>Est. monthly take-home</small><strong class="net">{{ s.monthlyNet | inr }}</strong></div>
            <div><small>Tax regime</small><strong>{{ s.taxRegime | humanize }}</strong></div>
          </div>
          <details>
            <summary>See the breakdown</summary>
            <div class="cols">
              <div>
                <h3>Earnings</h3>
                @for (l of s.earnings; track l.code) { <div class="row"><span>{{ l.name }}</span><span>{{ l.amount | inr }}</span></div> }
              </div>
              <div>
                <h3>Deductions</h3>
                @for (l of s.deductions; track l.code) { <div class="row"><span>{{ l.name }}</span><span>{{ l.amount | inr }}</span></div> }
                <h3 class="mt">Employer contributions</h3>
                @for (l of s.employer; track l.code) { <div class="row"><span>{{ l.name }}</span><span>{{ l.amount | inr }}</span></div> }
              </div>
            </div>
          </details>
        </section>
      }

      <h2 class="section">Payslips</h2>
      @if (loading()) {
        <p class="muted">Loading…</p>
      } @else {
        <div class="slips">
          @for (s of slips(); track s.id) {
            <button class="slip" type="button" (click)="open(s)">
              <mat-icon>receipt_long</mat-icon>
              <span class="month">{{ monthLabel(s.period) }}</span>
              <span class="muted small">Gross {{ s.totalGross | inr }}</span>
              <strong>{{ s.totalNet | inr }}</strong>
              <span class="muted small">{{ s.status === 'PAID' ? 'Paid' : 'Released' }}</span>
            </button>
          } @empty {
            <hg-empty-state icon="receipt_long" title="No payslips yet"
                            message="Your payslip appears here once payroll for the month is approved." />
          }
        </div>
      }
    </div>
  `,
  styles: `
    .structure { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 1rem 1.25rem; margin: 1rem 0; }
    .head { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; }
    .head small { display: block; font-size: 0.75rem; opacity: 0.7; }
    .head strong { font-size: 1.15rem; font-variant-numeric: tabular-nums; }
    .head .net { color: var(--mat-sys-primary, #1565c0); }
    details { margin-top: 0.75rem; } summary { cursor: pointer; font-size: 0.88rem; }
    .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-top: 0.75rem; }
    @media (max-width: 600px) { .cols { grid-template-columns: 1fr; } }
    h3 { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.04em; opacity: 0.7; margin: 0 0 0.3rem; }
    h3.mt { margin-top: 0.9rem; }
    .row { display: flex; justify-content: space-between; font-size: 0.88rem; padding: 0.15rem 0; font-variant-numeric: tabular-nums; }
    .section { font-size: 1rem; margin: 1.5rem 0 0.6rem; }
    .small { font-size: 0.8rem; }
    .slips { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.75rem; }
    .slip { display: grid; gap: 0.2rem; text-align: left; padding: 1rem; border-radius: 12px; cursor: pointer; font: inherit; color: inherit;
      border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); background: transparent; }
    .slip:hover, .slip:focus-visible { border-color: var(--mat-sys-primary, #1565c0); }
    .slip .month { font-weight: 600; }
    .slip strong { font-size: 1.2rem; font-variant-numeric: tabular-nums; }
    .slip mat-icon { opacity: 0.6; }
  `
})
export class MyPayslipsComponent {
  private payroll = inject(PayrollService);
  private dialog = inject(MatDialog);

  slips = signal<PayrollRun[]>([]);
  structure = signal<SalaryStructure | null>(null);
  loading = signal(true);

  constructor() {
    this.payroll.myPayslips().subscribe({
      next: (s) => { this.slips.set(s); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
    // No CTC on record returns a 409 toast; the structure card is simply omitted then.
    this.payroll.myStructure().subscribe({ next: (s) => this.structure.set(s), error: () => undefined });
  }

  open(s: PayrollRun): void {
    this.dialog.open(PayslipViewComponent, { data: s.id, width: '720px', maxWidth: '95vw' });
  }

  monthLabel(period: string): string {
    const [y, m] = period.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  }
}
