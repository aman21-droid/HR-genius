import { Component, inject, signal } from '@angular/core';
import { CommonModule, NgTemplateOutlet } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { AuthService } from '../core/services/auth.service';
import { ThemeService } from '../core/services/theme.service';
import { ApprovalService } from '../core/services/approval.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles?: string[];        // if set, item shows only for these roles
  permission?: string;     // if set, item shows only with this permission
  soon?: boolean;          // module arrives in a later phase: shown, not clickable
  section?: 'main' | 'admin';
  badge?: 'approvals';     // if set, item shows a live count badge
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule, NgTemplateOutlet, RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule, MatTooltipModule, MatBadgeModule
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent {
  private auth = inject(AuthService);
  private approvals = inject(ApprovalService);
  theme = inject(ThemeService);

  collapsed = signal(false);
  user = this.auth.user;
  approvalCount = this.approvals.pendingCount;

  constructor() {
    // Seed the Approvals badge once the shell (i.e. an authenticated session) mounts.
    this.approvals.refreshCount();
  }

  // Full nav map. Modules from later phases are listed (marked "Soon") so the information
  // architecture is visible from day one, but they are not clickable yet.
  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Employees', icon: 'people', route: '/employees' },
    { label: 'Org chart', icon: 'account_tree', route: '/org-chart' },
    { label: 'Attendance', icon: 'schedule', route: '/attendance' },
    { label: 'Leave', icon: 'beach_access', route: '/leave' },
    { label: 'Approvals', icon: 'fact_check', route: '/approvals', badge: 'approvals' },
    { label: 'Recruitment', icon: 'work', route: '/recruitment', permission: 'RECRUITMENT_MANAGE' },
    { label: 'My interviews', icon: 'record_voice_over', route: '/interviews' },
    { label: 'Onboarding', icon: 'how_to_reg', route: '/onboarding' },
    { label: 'My payslips', icon: 'receipt_long', route: '/payslips' },
    { label: 'Payroll', icon: 'payments', route: '/payroll', permission: 'PAYROLL_RUN' },
    { label: 'Performance', icon: 'trending_up', route: '/performance' },
    { label: 'Analytics', icon: 'insights', route: '/analytics', roles: ['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'PAYROLL_ADMIN'], soon: true },
    { label: 'Helpdesk', icon: 'support_agent', route: '/helpdesk', soon: true },
    // ---- admin ----
    { label: 'Org setup', icon: 'corporate_fare', route: '/org/setup', permission: 'ORG_MANAGE', section: 'admin' },
    { label: 'Leave config', icon: 'beach_access', route: '/leave/config', permission: 'LEAVE_CONFIG', section: 'admin' },
    { label: 'Assets', icon: 'devices_other', route: '/assets', permission: 'ASSET_MANAGE', section: 'admin' },
    { label: 'Expiring documents', icon: 'event_busy', route: '/documents/expiring', roles: ['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER'], section: 'admin' },
    { label: 'Audit trail', icon: 'history', route: '/admin/audit-log', permission: 'AUDIT_VIEW', section: 'admin' }
  ];

  private allowed(i: NavItem): boolean {
    return (!i.roles || this.auth.hasAnyRole(i.roles)) && (!i.permission || this.auth.hasPermission(i.permission));
  }

  get mainNav(): NavItem[] {
    return this.navItems.filter((i) => i.section !== 'admin' && this.allowed(i));
  }

  get adminNav(): NavItem[] {
    return this.navItems.filter((i) => i.section === 'admin' && this.allowed(i));
  }

  get hasEmployeeProfile(): boolean {
    return this.user()?.employeeId != null;
  }

  get initials(): string {
    const name = this.user()?.fullName ?? this.user()?.email ?? '';
    return name.split(/[\s@.]+/).filter(Boolean).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('');
  }

  get primaryRole(): string {
    return this.user()?.roles?.[0] ?? '';
  }

  toggleSidebar(): void {
    this.collapsed.set(!this.collapsed());
  }

  logout(): void {
    this.auth.logout();
  }
}
