import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service';
import { OrgService } from '../../core/services/org.service';
import { MeService, MeSummary, TODO_ICONS } from '../../core/services/me.service';
import { ExpiringDocument } from '../../core/models/employee.models';
import { InrPipe } from '../../shared/pipes/labels.pipe';

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
  imports: [RouterLink, DatePipe, DecimalPipe, MatIconModule, MatButtonModule, InrPipe],
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
  readonly icons = TODO_ICONS;
  readonly isHr = this.auth.hasAnyRole(['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER']);
  readonly isManager = this.auth.hasRole('MANAGER');
  readonly canAddEmployee = this.auth.hasPermission('EMPLOYEE_WRITE');
  readonly canAnalytics = this.auth.hasPermission('ANALYTICS_VIEW');

  headcount = signal<number | null>(null);
  teamSize = signal<number | null>(null);
  expiring = signal<ExpiringDocument[]>([]);
  summary = signal<MeSummary | null>(null);
  lookups = toSignal(this.org.lookups());

  greeting = computed(() => {
    const h = new Date().getHours();
    const part = h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
    const name = this.user()?.fullName?.split(' ')[0] ?? 'there';
    return `${part}, ${name}`;
  });

  stats = computed<StatCard[]>(() => {
    const cards: StatCard[] = [
      { label: 'People', value: this.fmt(this.headcount()), icon: 'groups', link: '/employees' },
      { label: 'Departments', value: this.fmt(this.lookups()?.departments.length), icon: 'account_tree', link: '/org-chart' }
    ];
    if (this.isManager) {
      cards.push({ label: 'In my team', value: this.fmt(this.teamSize()), icon: 'diversity_3', link: '/employees' });
    }
    if (this.isHr) {
      cards.push({ label: 'Documents needing attention', value: String(this.expiring().length), icon: 'event_busy',
        link: '/documents/expiring' });
    }
    return cards;
  });

  constructor() {
    this.employees.list({}, { size: 1 }).subscribe((p) => this.headcount.set(p.totalElements));
    if (this.user()?.employeeId != null) {
      this.me.summary().subscribe({ next: (s) => this.summary.set(s), error: () => undefined });
    }
    if (this.isManager) {
      this.employees.list({ teamOnly: true }, { size: 1 }).subscribe((p) => this.teamSize.set(p.totalElements));
    }
    if (this.isHr) {
      this.employees.expiringDocuments(30).subscribe((d) => this.expiring.set(d));
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
