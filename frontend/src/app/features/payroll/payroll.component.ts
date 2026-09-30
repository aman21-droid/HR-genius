import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PayrollService } from '../../core/services/payroll.service';
import { PayrollRun, SalaryComponent } from '../../core/models/payroll.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe, InrPipe } from '../../shared/pipes/labels.pipe';
import { ComponentDialogComponent, NewRunDialogComponent } from './payroll-dialogs.component';

@Component({
  selector: 'app-payroll',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatMenuModule,
    PageHeaderComponent, StatusChipComponent, EmptyStateComponent, HumanizePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Payroll" subtitle="Monthly runs, approvals and the salary structure"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Payroll' }]">
        <button mat-flat-button color="primary" (click)="newRun()"><mat-icon>add</mat-icon> New run</button>
      </hg-page-header>

      <mat-tab-group animationDuration="0ms">
        <mat-tab label="Runs">
          <div class="pad">
            @if (latest(); as l) {
              <section class="kpis">
                <div><small>Latest run</small><strong>{{ monthLabel(l.period) }}</strong><hg-status [value]="l.status" /></div>
                <div><small>Employees</small><strong>{{ l.employeeCount }}</strong></div>
                <div><small>Net payout</small><strong>{{ l.totalNet | inr }}</strong></div>
                <div><small>Cost to company</small><strong>{{ l.totalEmployerCost | inr }}</strong></div>
              </section>
            }
            <div class="runs">
              @for (r of runs(); track r.id) {
                <a class="run" [routerLink]="['/payroll/runs', r.id]">
                  <div class="m"><strong>{{ monthLabel(r.period) }}</strong>
                    <span class="muted small">{{ r.employeeCount }} employees{{ r.adjustments ? ' · ' + r.adjustments + ' adjustment(s)' : '' }}</span></div>
                  <div class="num"><small>Gross</small>{{ r.totalGross | inr }}</div>
                  <div class="num"><small>Deductions</small>{{ r.totalDeductions | inr }}</div>
                  <div class="num"><small>Net</small><strong>{{ r.totalNet | inr }}</strong></div>
                  <hg-status [value]="r.status" />
                </a>
              } @empty {
                <hg-empty-state icon="payments" title="No payroll runs yet" message="Create a run for a month to generate payslips." />
              }
            </div>
          </div>
        </mat-tab>

        <mat-tab label="Salary structure">
          <div class="pad">
            <p class="muted">Each employee's monthly pay is derived from their annual CTC using these components. CTC also
              funds employer PF (12% of Basic, capped at a ₹15,000 wage) and employer ESI where applicable. Statutory
              deductions (PF, ESI, professional tax, TDS) are computed automatically.</p>
            <div class="tpl-head"><button mat-stroked-button (click)="editComponent(null)"><mat-icon>add</mat-icon> Add component</button></div>
            <table class="hg-table">
              <thead><tr><th>Component</th><th>Calculation</th><th>Taxable</th><th>Status</th><th></th></tr></thead>
              <tbody>
                @for (c of components(); track c.id) {
                  <tr>
                    <td><strong>{{ c.name }}</strong> <span class="muted small">{{ c.code }}</span></td>
                    <td>{{ describe(c) }}</td>
                    <td>{{ c.taxable ? 'Yes' : 'No' }}</td>
                    <td><hg-status [value]="c.active ? 'ACTIVE' : 'INACTIVE'" /></td>
                    <td class="end">
                      <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Actions for ' + c.name"><mat-icon>more_vert</mat-icon></button>
                      <mat-menu #m="matMenu">
                        <button mat-menu-item (click)="editComponent(c)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                        @if (c.code !== 'BASIC' && c.calcType !== 'BALANCING') {
                          <button mat-menu-item (click)="deleteComponent(c)"><mat-icon>delete</mat-icon><span>Delete</span></button>
                        }
                      </mat-menu>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: `
    .pad { padding: 1rem 0.25rem; }
    .small { font-size: 0.8rem; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.75rem; margin-bottom: 1rem; }
    .kpis > div { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.75rem 1rem;
      display: grid; gap: 0.2rem; justify-items: start; }
    .kpis small { font-size: 0.75rem; opacity: 0.7; } .kpis strong { font-size: 1.2rem; font-variant-numeric: tabular-nums; }
    .runs { display: grid; gap: 0.5rem; }
    .run { display: grid; grid-template-columns: minmax(180px, 1.5fr) repeat(3, minmax(110px, 1fr)) auto; gap: 1rem; align-items: center;
      padding: 0.75rem 1rem; border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; color: inherit; text-decoration: none; }
    .run:hover { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    .run .m { display: grid; }
    .num { font-variant-numeric: tabular-nums; } .num small { display: block; font-size: 0.7rem; opacity: 0.65; }
    @media (max-width: 760px) { .run { grid-template-columns: 1fr 1fr; } }
    .tpl-head { display: flex; justify-content: flex-end; margin: 0.5rem 0; }
    .hg-table { width: 100%; border-collapse: collapse; }
    .hg-table th, .hg-table td { text-align: left; padding: 0.55rem 0.75rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .hg-table th { font-size: 0.75rem; text-transform: uppercase; opacity: 0.6; }
    .end { text-align: right; }
  `
})
export class PayrollComponent {
  private payroll = inject(PayrollService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);
  private router = inject(Router);

  runs = signal<PayrollRun[]>([]);
  components = signal<SalaryComponent[]>([]);
  latest = signal<PayrollRun | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.payroll.runs().subscribe((r) => { this.runs.set(r); this.latest.set(r[0] ?? null); });
    this.payroll.components().subscribe((c) => this.components.set(c));
  }

  newRun(): void {
    this.dialog.open(NewRunDialogComponent, { data: this.runs().map((r) => r.period), width: '460px', maxWidth: '95vw' })
      .afterClosed().subscribe((r?: PayrollRun) => r && this.router.navigate(['/payroll/runs', r.id]));
  }

  editComponent(c: SalaryComponent | null): void {
    this.dialog.open(ComponentDialogComponent, { data: c, width: '620px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => { if (ok) { this.snack.open('Salary structure updated', undefined, { duration: 2500 }); this.reload(); } });
  }

  deleteComponent(c: SalaryComponent): void {
    this.confirm.ask({ title: `Delete ${c.name}?`, message: 'Future runs will no longer include it. Existing payslips are unchanged.',
      confirmText: 'Delete', danger: true })
      .subscribe((yes) => yes && this.payroll.deleteComponent(c.id).subscribe(() => this.reload()));
  }

  describe(c: SalaryComponent): string {
    switch (c.calcType) {
      case 'PERCENT_OF_CTC': return `${c.calcValue}% of monthly CTC`;
      case 'PERCENT_OF_BASIC': return `${c.calcValue}% of Basic`;
      case 'FIXED_MONTHLY': return `₹${Number(c.calcValue).toLocaleString('en-IN')} per month`;
      default: return 'Balance of monthly gross';
    }
  }

  monthLabel(period: string): string {
    const [y, m] = period.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  }
}
