import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service';
import { OrgService } from '../../core/services/org.service';
import { ExpiringDocument } from '../../core/models/employee.models';

interface StatCard {
  label: string;
  value: string;
  icon: string;
  link?: string;
}

/**
 * Home page. Shows only real numbers; widgets for leave, approvals, payroll etc. arrive with
 * their modules rather than being faked here.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  private auth = inject(AuthService);
  private employees = inject(EmployeeService);
  private org = inject(OrgService);

  user = this.auth.user;
  readonly isHr = this.auth.hasAnyRole(['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER']);
  readonly isManager = this.auth.hasRole('MANAGER');
  readonly canAddEmployee = this.auth.hasPermission('EMPLOYEE_WRITE');

  headcount = signal<number | null>(null);
  teamSize = signal<number | null>(null);
  expiring = signal<ExpiringDocument[]>([]);
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
      { label: 'Departments', value: this.fmt(this.lookups()?.departments.length), icon: 'account_tree', link: '/org-chart' },
      { label: 'Locations', value: this.fmt(this.lookups()?.locations.length), icon: 'place' }
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
    if (this.isManager) {
      this.employees.list({ teamOnly: true }, { size: 1 }).subscribe((p) => this.teamSize.set(p.totalElements));
    }
    if (this.isHr) {
      this.employees.expiringDocuments(30).subscribe((d) => this.expiring.set(d));
    }
  }

  private fmt(n: number | null | undefined): string {
    return n == null ? '…' : String(n);
  }
}
