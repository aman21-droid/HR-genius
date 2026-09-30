import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { AnalyticsService } from '../../core/services/policy.service';
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

          <section class="card"><h2>Headcount by department</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.byDepartment }" /></section>
          <section class="card"><h2>Headcount by location</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.byLocation }" /></section>
          <section class="card"><h2>Tenure</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.tenure, keepOrder: true }" /></section>
          <section class="card"><h2>Employment type</h2><ng-container *ngTemplateOutlet="bars; context: { $implicit: o.byEmploymentType }" /></section>

          <section class="card wide">
            <h2>Monthly cost to company</h2>
            @if (!o.payrollTrend.length) { <p class="muted">No payroll runs yet.</p> }
            @else if (tableView) { <ng-container *ngTemplateOutlet="table; context: { $implicit: payrollRows(), cols: ['Month', 'Employees', 'Gross', 'Net', 'Cost to company'] }" /> }
            @else {
              <hg-column-chart [labels]="payrollLabels()" [series]="payrollSeries()" [fmt]="lakh" [short]="shortMonth" [height]="160" />
            }
          </section>
          <section class="card">
            <h2>Cost by department <span class="muted small">{{ o.payrollByDepartmentPeriod ? monthLabel(o.payrollByDepartmentPeriod) : '' }}</span></h2>
            <ng-container *ngTemplateOutlet="bars; context: { $implicit: o.payrollByDepartment, money: true }" />
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
      } @else {
        <p class="muted">Crunching the numbers…</p>
      }
    </div>

    <ng-template #bars let-data let-keepOrder="keepOrder" let-money="money">
      @if (tableView) {
        <ng-container *ngTemplateOutlet="table; context: { $implicit: sliceRows(data, money), cols: ['', money ? 'Cost (INR)' : 'Count'] }" />
      } @else {
        <hg-hbar-chart [data]="asBars(data)" [fmt]="money ? lakh : plain" />
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
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 0.75rem; margin: 0.5rem 0 1rem; }
    .kpi { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 0.85rem 1rem; display: grid; gap: 0.15rem; }
    .kpi small { font-size: 0.75rem; opacity: 0.7; }
    .kpi strong { font-size: 1.6rem; line-height: 1.15; font-variant-numeric: tabular-nums; }
    .kpi span { font-size: 0.78rem; opacity: 0.7; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .card { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 1rem 1.15rem; min-width: 0; }
    .card h2 { font-size: 0.95rem; margin: 0 0 0.9rem; font-weight: 600; }
    .small { font-size: 0.78rem; font-weight: 400; }
    .hg-table { width: 100%; border-collapse: collapse; font-size: 0.86rem; }
    .hg-table th, .hg-table td { text-align: left; padding: 0.35rem 0.5rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .hg-table th { font-size: 0.72rem; text-transform: uppercase; opacity: 0.6; }
    .num { text-align: right !important; font-variant-numeric: tabular-nums; }
  `
})
export class AnalyticsComponent {
  private analytics = inject(AnalyticsService);

  o = signal<AnalyticsOverview | null>(null);
  tableView = false;

  movementLabels = computed(() => (this.o()?.movement ?? []).map((m) => m.month));
  movementSeries = computed(() => {
    const m = this.o()?.movement ?? [];
    return [{ name: 'Joined', values: m.map((x) => x.joins) }, { name: 'Left', values: m.map((x) => x.exits) }];
  });
  headcountValues = computed(() => (this.o()?.movement ?? []).map((m) => m.headcount));
  movementRows = computed(() => (this.o()?.movement ?? []).map((m) => [this.monthLabel(m.month), m.joins, m.exits, m.headcount]));
  headcountRows = computed(() => (this.o()?.movement ?? []).map((m) => [this.monthLabel(m.month), m.headcount]));
  payrollLabels = computed(() => (this.o()?.payrollTrend ?? []).map((p) => p.period));
  payrollSeries = computed(() => [{ name: 'Cost to company', values: (this.o()?.payrollTrend ?? []).map((p) => Number(p.employerCost)) }]);
  payrollRows = computed(() => (this.o()?.payrollTrend ?? []).map((p) =>
    [this.monthLabel(p.period), p.employees, this.inr(p.gross), this.inr(p.net), this.inr(p.employerCost)]));

  readonly plain = (v: number) => v.toLocaleString('en-IN');
  readonly lakh = (v: number) => (v >= 1e7 ? `₹${(v / 1e7).toFixed(2)} Cr` : v >= 1e5 ? `₹${(v / 1e5).toFixed(1)} L` : `₹${Math.round(v).toLocaleString('en-IN')}`);
  readonly shortMonth = (ym: string) => { const [y, m] = ym.split('-').map(Number); return `${MONTHS[m - 1]} ’${String(y).slice(2)}`; };

  constructor() {
    this.analytics.overview().subscribe((o) => this.o.set(o));
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
