import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { AnalyticsService } from '../../core/services/policy.service';
import { AuthService } from '../../core/services/auth.service';
import { DrilldownService } from '../../core/services/drilldown.service';
import { PayrollService } from '../../core/services/payroll.service';
import { AnalyticsOverview, Slice } from '../../core/models/services.models';
import { saveBlob } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { BarDatum, ColumnChartComponent, HBarChartComponent, LineChartComponent } from './charts.component';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** HR analytics: KPI tiles up top, then one chart per question. A table view is always available. */
@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [DecimalPipe, NgTemplateOutlet, FormsModule, MatButtonModule, MatIconModule, MatSlideToggleModule, PageHeaderComponent,
    HBarChartComponent, ColumnChartComponent, LineChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Analytics" subtitle="Workforce, cost, hiring and service health"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Analytics' }]">
        <mat-slide-toggle [(ngModel)]="tableView">Table view</mat-slide-toggle>
        <button mat-stroked-button (click)="export()"><mat-icon>download</mat-icon> Headcount report</button>
      </hg-page-header>

      @if (o(); as o) {
        <div class="range-toolbar"><span>Explore your people data</span><div class="periods" aria-label="Trend period">
          <button type="button" [class.active]="period() === 6" [attr.aria-pressed]="period() === 6" (click)="period.set(6)">6 months</button>
          <button type="button" [class.active]="period() === 12" [attr.aria-pressed]="period() === 12" (click)="period.set(12)">12 months</button>
        </div></div>
        <section class="kpis">
          <div class="kpi"><small>Headcount</small><strong>{{ o.kpis.headcount }}</strong>
            <span>+{{ o.kpis.joinsLast12m }} joined · −{{ o.kpis.exitsLast12m }} left (12 mo)</span></div>
          <div class="kpi"><small>Attrition (12 mo)</small><strong>{{ o.kpis.attritionRate | number: '1.1-1' }}%</strong>
            <span>Avg tenure {{ o.kpis.averageTenureYears | number: '1.1-1' }} yrs</span></div>
          <div class="kpi"><small>Monthly payroll cost</small><strong>{{ money(o.kpis.latestMonthlyPayroll) }}</strong>
            <span>{{ o.payrollByDepartmentPeriod ? monthLabel(o.payrollByDepartmentPeriod) : 'No approved run yet' }}</span></div>
          <div class="kpi"><small>Open positions</small><strong>{{ o.kpis.openPositions }}</strong>
            <span>{{ o.kpis.openRequisitions }} requisitions{{ o.kpis.averageDaysToHire != null ? ' · ' + o.kpis.averageDaysToHire + ' days to hire' : '' }}</span></div>
          <div class="kpi"><small>Open tickets</small><strong>{{ o.kpis.openTickets }}</strong>
            <span>{{ o.kpis.overdueTickets }} overdue</span></div>
          <div class="kpi"><small>Policy compliance</small><strong>{{ o.kpis.policyCompliance != null ? (o.kpis.policyCompliance | number: '1.0-1') + '%' : '—' }}</strong>
            <span>of required acknowledgements</span></div>
        </section>

        <div class="grid">
          <section class="card wide">
            <h2>Joins and exits by month</h2>
            @if (tableView) { <ng-container *ngTemplateOutlet="table; context: { $implicit: movementRows(), cols: ['Month', 'Joined', 'Left', 'Headcount'] }" /> }
            @else {
              <hg-column-chart [labels]="movementLabels()" [series]="movementSeries()" [short]="shortMonth" [height]="170" />
            }
          </section>
          <section class="card">
            <h2>Headcount trend</h2>
            @if (tableView) { <ng-container *ngTemplateOutlet="table; context: { $implicit: headcountRows(), cols: ['Month', 'Headcount'] }" /> }
            @else { <hg-line-chart [labels]="movementLabels()" [values]="headcountValues()" name="Headcount" [short]="shortMonth" /> }
          </section>

          <section class="card"><h2>Headcount by department</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.byDepartment, drill: 'department' }" /></section>
          <section class="card"><h2>Headcount by location</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.byLocation, drill: 'location' }" /></section>
          <section class="card"><h2>Tenure</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.tenure, keepOrder: true }" /></section>
          <section class="card"><h2>Employment type</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.byEmploymentType }" /></section>

          <section class="card wide">
            <h2>Monthly cost to company</h2>
            @if (!o.payrollTrend.length) { <p class="muted">No payroll runs yet.</p> }
            @else if (tableView) { <ng-container *ngTemplateOutlet="table; context: { $implicit: payrollRows(), cols: ['Month', 'Employees', 'Gross', 'Net', 'Cost to company'] }" /> }
            @else {
              <hg-column-chart [labels]="payrollLabels()" [series]="payrollSeries()" [fmt]="lakh" [short]="shortMonth" [height]="160"
                               [clickable]="canOpenRuns" actionLabel="Open this payroll run" (selected)="openPayrollMonth($event)" />
              @if (canOpenRuns) { <p class="muted small drill-hint">Select a month to open its payroll run.</p> }
            }
          </section>
          <section class="card">
            <h2>Cost by department <span class="muted small">{{ o.payrollByDepartmentPeriod ? monthLabel(o.payrollByDepartmentPeriod) : '' }}</span></h2>
            <ng-container *ngTemplateOutlet="bars; context: { $implicit: o.payrollByDepartment, money: true, drill: 'department' }" />
          </section>

          <section class="card"><h2>Hiring funnel</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.recruitmentFunnel, keepOrder: true }" /></section>
          <section class="card"><h2>Candidate sources</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.candidateSources }" /></section>
          <section class="card">
            <h2>Rating distribution <span class="muted small">{{ o.ratingCycle ?? '' }}</span></h2>
            @if (!o.ratingDistribution.length) { <p class="muted">No completed reviews yet.</p> }
            @else if (tableView) { <ng-container *ngTemplateOutlet="table; context: { $implicit: sliceRows(o.ratingDistribution), cols: ['Rating', 'Employees'] }" /> }
            @else {
              <hg-column-chart [labels]="labelsOf(o.ratingDistribution)" [series]="[{ name: 'Employees', values: valuesOf(o.ratingDistribution) }]" [height]="150" />
            }
          </section>
          <section class="card"><h2>Leave taken this year <span class="muted small">approved days</span></h2>
            <ng-container *ngTemplateOutlet="bars; context: { $implicit: o.leaveDaysByType }" /></section>
        </div>
      } @else if (failed()) {
        <div class="hg-empty"><mat-icon>insights</mat-icon><h2>Insights couldn't load</h2><p>Please try again in a moment.</p><button mat-stroked-button (click)="load()">Try again</button></div>
      } @else {
        <div role="status" class="hg-skeleton-rows"><div class="hg-skeleton"></div><div class="hg-skeleton"></div><p class="muted">Bringing your people data together…</p></div>
      }
    </div>

    <ng-template #bars let-data let-keepOrder="keepOrder" let-money="money" let-drill="drill">
      @if (tableView) {
        <ng-container *ngTemplateOutlet="table; context: { $implicit: sliceRows(data, money), cols: ['', money ? 'Cost (INR)' : 'Count'] }" />
      } @else {
        <hg-hbar-chart [data]="asBars(data)" [fmt]="money ? lakh : plain" [clickable]="!!drill"
                       [actionLabel]="drill === 'location' ? 'Open the people at this location' : 'Open the people in this department'"
                       (selected)="openBar(drill, $event)" />
      }
    </ng-template>

    <ng-template #table let-rows let-cols="cols">
      <table class="hg-table">
        <thead><tr>@for (c of cols; track $index) { <th [class.num]="$index > 0">{{ c }}</th> }</tr></thead>
        <tbody>
          @for (r of rows; track $index) {
            <tr>@for (cell of r; track $index) { <td [class.num]="$index > 0">{{ cell }}</td> }</tr>
          } @empty { <tr><td class="muted" [attr.colspan]="cols.length">No data yet.</td></tr> }
        </tbody>
      </table>
    </ng-template>
  `,
  styles: `
    .range-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 24px 0 16px; color: var(--hg-muted); font-size: 12px; }
    .periods { display: flex; border: 1px solid var(--hg-border); border-radius: 9px; padding: 3px; background: var(--hg-surface); }
    .periods button { border: 0; padding: 8px 12px; border-radius: 6px; font-size: 11px; background: transparent; color: var(--hg-muted); cursor: pointer; }
    .periods button.active { background: var(--hg-primary); color: var(--hg-on-primary); }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 0.75rem; margin: 0.5rem 0 1rem; }
    .kpi { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 0.85rem 1rem; display: grid; gap: 0.15rem; }
    .kpi small { font-size: 0.75rem; color: var(--hg-muted); }
    .kpi strong { font-size: 1.6rem; line-height: 1.15; font-variant-numeric: tabular-nums; }
    .kpi span { font-size: 0.78rem; color: var(--hg-muted); }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .card { border: 1px solid var(--hg-border); border-radius: 18px; padding: 24px; min-width: 0; overflow-x: auto; }
    .card h2 { font-size: 0.95rem; margin: 0 0 0.9rem; font-weight: 600; }
    .small { font-size: 0.78rem; font-weight: 400; }
    .hg-table { width: 100%; border-collapse: collapse; font-size: 0.86rem; }
    .hg-table th, .hg-table td { text-align: left; padding: 0.35rem 0.5rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .hg-table th { font-size: 0.72rem; text-transform: uppercase; color: var(--hg-muted); }
    .num { text-align: right !important; font-variant-numeric: tabular-nums; }
    .drill-hint { margin: 8px 0 0; }
  `
})
export class AnalyticsComponent {
  private analytics = inject(AnalyticsService);
  private drill = inject(DrilldownService);
  private payrollApi = inject(PayrollService);
  /** Only payroll operators can open a run; for everyone else the trend stays a read-only chart. */
  readonly canOpenRuns = inject(AuthService).hasPermission('PAYROLL_RUN');
  private runIdByPeriod = new Map<string, number>();

  o = signal<AnalyticsOverview | null>(null);
  tableView = false;
  period = signal<6 | 12>(12);
  failed = signal(false);
  movement = computed(() => (this.o()?.movement ?? []).slice(-this.period()));
  payroll = computed(() => (this.o()?.payrollTrend ?? []).slice(-this.period()));

  movementLabels = computed(() => this.movement().map((m) => m.month));
  movementSeries = computed(() => {
    const m = this.movement();
    return [{ name: 'Joined', values: m.map((x) => x.joins) }, { name: 'Left', values: m.map((x) => x.exits) }];
  });
  headcountValues = computed(() => this.movement().map((m) => m.headcount));
  movementRows = computed(() => this.movement().map((m) => [this.monthLabel(m.month), m.joins, m.exits, m.headcount]));
  headcountRows = computed(() => this.movement().map((m) => [this.monthLabel(m.month), m.headcount]));
  payrollLabels = computed(() => this.payroll().map((p) => p.period));
  payrollSeries = computed(() => [{ name: 'Cost to company', values: this.payroll().map((p) => Number(p.employerCost)) }]);
  payrollRows = computed(() => this.payroll().map((p) =>
    [this.monthLabel(p.period), p.employees, this.inr(p.gross), this.inr(p.net), this.inr(p.employerCost)]));

  readonly plain = (v: number) => v.toLocaleString('en-IN');
  readonly lakh = (v: number) => (v >= 1e7 ? `₹${(v / 1e7).toFixed(2)} Cr` : v >= 1e5 ? `₹${(v / 1e5).toFixed(1)} L` : `₹${Math.round(v).toLocaleString('en-IN')}`);
  readonly shortMonth = (ym: string) => { const [y, m] = ym.split('-').map(Number); return `${MONTHS[m - 1]} ’${String(y).slice(2)}`; };

  constructor() {
    this.load();
    if (this.canOpenRuns) {
      this.payrollApi.runs().subscribe({ next: (runs) => runs.forEach((r) => this.runIdByPeriod.set(r.period, r.id)), error: () => void 0 });
    }
  }

  openBar(kind: 'department' | 'location' | null, bar: BarDatum): void {
    if (kind === 'department') { this.drill.openDepartment(bar.label); }
    else if (kind === 'location') { this.drill.openLocation(bar.label); }
  }

  openPayrollMonth(index: number): void {
    const id = this.runIdByPeriod.get(this.payroll()[index]?.period);
    if (id != null) { this.drill.openPayrollRun(id); }
  }

  load(): void {
    this.failed.set(false);
    this.analytics.overview().subscribe({ next: o => this.o.set(o), error: () => this.failed.set(true) });
  }

  asBars(data: Slice[]): BarDatum[] {
    return data.map((d) => ({ label: d.label, value: Number(d.value) }));
  }

  labelsOf(data: Slice[]): string[] {
    return data.map((d) => d.label);
  }

  valuesOf(data: Slice[]): number[] {
    return data.map((d) => Number(d.value));
  }

  sliceRows(data: Slice[], money = false): (string | number)[][] {
    return data.map((d) => [d.label, money ? this.inr(d.value) : d.value]);
  }

  money(v: number | null): string {
    return v == null ? '—' : this.lakh(Number(v));
  }

  inr(v: number): string {
    return '₹' + Math.round(Number(v)).toLocaleString('en-IN');
  }

  monthLabel(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    return `${MONTHS[m - 1]} ${y}`;
  }

  export(): void {
    this.analytics.headcountExport().subscribe((res) => saveBlob(res, 'headcount.xlsx'));
  }
}
