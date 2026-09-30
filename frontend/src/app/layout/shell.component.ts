import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
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

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles?: string[]; // if set, item shows only for these roles
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule, MatTooltipModule, MatBadgeModule
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent {
  private auth = inject(AuthService);
  theme = inject(ThemeService);

  collapsed = signal(false);
  user = this.auth.user;

  // Full nav map. Later phases wire up the real routes; disabled items are visible
  // but not yet routable so the information architecture is clear from day one.
  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Employees', icon: 'people', route: '/employees', roles: ['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'MANAGER'] },
    { label: 'Recruitment', icon: 'work', route: '/recruitment', roles: ['SUPER_ADMIN', 'HR_ADMIN', 'RECRUITER'] },
    { label: 'Attendance', icon: 'schedule', route: '/attendance' },
    { label: 'Leave', icon: 'beach_access', route: '/leave' },
    { label: 'Payroll', icon: 'payments', route: '/payroll', roles: ['SUPER_ADMIN', 'PAYROLL_ADMIN'] },
    { label: 'Performance', icon: 'trending_up', route: '/performance' },
    { label: 'Analytics', icon: 'insights', route: '/analytics', roles: ['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER', 'PAYROLL_ADMIN'] },
    { label: 'Helpdesk', icon: 'support_agent', route: '/helpdesk' }
  ];

  get visibleNav(): NavItem[] {
    return this.navItems.filter((i) => !i.roles || this.auth.hasAnyRole(i.roles));
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
