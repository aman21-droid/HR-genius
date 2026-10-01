import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AnalyticsService } from '../../core/services/policy.service';
import { AnalyticsOverview } from '../../core/models/services.models';
import { HBarChartComponent, LineChartComponent } from '../analytics/charts.component';
import { DrilldownService } from '../../core/services/drilldown.service';

/** Mounted only for users with ANALYTICS_VIEW, using the existing organisation endpoint. */
@Component({
  selector: 'hg-workforce-insights',
  standalone: true,
  imports: [DecimalPipe, RouterLink, MatIconModule, MatButtonModule, HBarChartComponent, LineChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './workforce-insights.component.html',
  styleUrl: './workforce-insights.component.scss'
})
export class WorkforceInsightsComponent {
  private analytics = inject(AnalyticsService);
  readonly drill = inject(DrilldownService);
  overview = signal<AnalyticsOverview | null>(null);
  analyticsError = signal(false);
  period = signal<6 | 12>(12);
  chartTable = signal(false);
  movement = computed(() => (this.overview()?.movement ?? []).slice(-this.period()));
  movementLabels = computed(() => this.movement().map(m => m.month));
  movementValues = computed(() => this.movement().map(m => m.headcount));
  departments = computed(() => (this.overview()?.byDepartment ?? []).map(d => ({ label: d.label, value: Number(d.value) })));
  readonly shortMonth = (ym: string) => this.monthLabel(ym, 'short');

  constructor() { this.loadAnalytics(); }
  loadAnalytics(): void {
    this.analyticsError.set(false);
    this.analytics.overview().subscribe({ next: data => this.overview.set(data), error: () => this.analyticsError.set(true) });
  }
  monthLabel(ym: string, month: 'short' | 'long' = 'long'): string {
    const [y, m] = ym.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month, ...(month === 'long' ? { year: 'numeric' as const } : {}) });
  }
}
