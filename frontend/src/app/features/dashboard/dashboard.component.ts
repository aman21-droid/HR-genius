import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';

interface StatCard {
  label: string;
  value: string;
  icon: string;
  hint: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  private auth = inject(AuthService);
  user = this.auth.user;

  greeting = computed(() => {
    const h = new Date().getHours();
    const part = h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
    const name = this.user()?.fullName?.split(' ')[0] ?? 'there';
    return `${part}, ${name}`;
  });

  // Placeholder KPIs — Phase 8 wires these to the analytics API.
  stats: StatCard[] = [
    { label: 'Headcount', value: '50', icon: 'groups', hint: 'across 6 departments' },
    { label: 'On leave today', value: '3', icon: 'beach_access', hint: 'team availability 94%' },
    { label: 'Open positions', value: '3', icon: 'work', hint: '12 candidates in pipeline' },
    { label: 'Pending approvals', value: '5', icon: 'approval', hint: 'awaiting your action' }
  ];
}
