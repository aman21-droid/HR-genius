import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service';
import { OrgService } from '../../core/services/org.service';
import { MeService, MeSummary, RoleDashboard } from '../../core/services/me.service';
import { RoleFocusComponent } from './role-focus.component';
import { ExpiringDocument } from '../../core/models/employee.models';
import { InrPipe } from '../../shared/pipes/labels.pipe';
import { WorkforceInsightsComponent } from './workforce-insights.component';
import { PersonalOverviewComponent } from './personal-overview.component';
import { PeopleMomentsComponent } from './people-moments.component';

interface StatCard {
  label: string;
  value: string;
  icon: string;
  link?: string;
}

/** Home page: what needs my attention, my own highlights, and (for HR/managers) a few org numbers. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe, MatIconModule, MatButtonModule, InrPipe, WorkforceInsightsComponent, PeopleMomentsComponent, PersonalOverviewComponent, RoleFocusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  private auth = inject(AuthService);
  private employees = inject(EmployeeService);
  private org = inject(OrgService);
  private me = inject(MeService);

  user = this.auth.user;
  readonly isHr = this.auth.hasAnyRole(['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER']);
  readonly isManager = this.auth.hasRole('MANAGER');
  readonly canAddEmployee = this.auth.hasPermission('EMPLOYEE_WRITE');
  readonly canAnalytics = this.auth.hasPermission('ANALYTICS_VIEW');

  headcount = signal<number | null>(null);
  teamSize = signal<number | null>(null);
  expiring = signal<ExpiringDocument[]>([]);
  summary = signal<MeSummary | null>(null);
  roleDashboard = signal<RoleDashboard | null>(null);
  summaryError = signal(false);
  documentError = signal(false);
  documentLoading = signal(true);
  headcountError = signal(false);
  teamError = signal(false);
  lookupError = signal(false);
  readonly today = new Date();
  readonly hasProfile = this.user()?.employeeId != null;
  lookups = toSignal(this.org.lookups().pipe(catchError(() => { this.lookupError.set(true); return of(undefined); })));
  overviewError = computed(() => this.headcountError() || this.teamError() || this.lookupError());

  greeting = computed(() => {
    const h = new Date().getHours();
    const part = h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
    const name = this.user()?.fullName?.split(' ')[0] ?? 'there';
    return `${part}, ${name}`;
  });

  stats = computed<StatCard[]>(() => {
    // Organisation-wide numbers only for people who run the organisation; everyone else gets their own.
    if (!this.isHr && !this.canAnalytics && !this.isManager) {
      const s = this.summary();
      const leave = s?.leaveBalances.reduce((t, b) => t + Number(b.available), 0);
      return [
        { label: 'Leave days available', value: this.fmt(leave), icon: 'beach_access', link: '/leave' },
        { label: 'Things to do', value: this.fmt(s?.todos.length), icon: 'task_alt' },
        { label: 'Open help requests', value: this.fmt(s?.openTickets), icon: 'support_agent', link: '/helpdesk' }
      ];
    }
    const cards: StatCard[] = [
      { label: 'People', value: this.headcountError() ? '—' : this.fmt(this.headcount()), icon: 'groups', link: '/employees' },
      { label: 'Departments', value: this.lookupError() ? '—' : this.fmt(this.lookups()?.departments.length), icon: 'account_tree', link: '/org-chart' },
      { label: 'Locations', value: this.lookupError() ? '—' : this.fmt(this.lookups()?.locations.length), icon: 'place' }
    ];
    if (this.isManager) {
      cards.push({ label: 'In my team', value: this.teamError() ? '—' : this.fmt(this.teamSize()), icon: 'diversity_3', link: '/employees' });
    }
    if (this.isHr) {
      cards.push({ label: 'Documents needing attention', value: this.documentError() ? '—' : this.documentLoading() ? '…' : String(this.expiring().length), icon: 'event_busy',
        link: '/documents/expiring' });
    }
    return cards;
  });

  constructor() {
    this.employees.list({}, { size: 1 }).subscribe({ next: p => this.headcount.set(p.totalElements), error: () => this.headcountError.set(true) });
    if (this.user()?.employeeId != null) {
      this.me.summary().subscribe({ next: (s) => this.summary.set(s), error: () => this.summaryError.set(true) });
      this.me.dashboard().subscribe({ next: (d) => this.roleDashboard.set(d), error: () => void 0 });
    }
    if (this.isManager) {
      this.employees.list({ teamOnly: true }, { size: 1 }).subscribe({ next: p => this.teamSize.set(p.totalElements), error: () => this.teamError.set(true) });
    }
    if (this.isHr) {
      this.employees.expiringDocuments(30).subscribe({
        next: d => { this.expiring.set(d); this.documentLoading.set(false); },
        error: () => { this.documentError.set(true); this.documentLoading.set(false); }
      });
    }
  }

  monthLabel(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  }

  private fmt(n: number | null | undefined): string {
    return n == null ? '…' : String(n);
  }
}
