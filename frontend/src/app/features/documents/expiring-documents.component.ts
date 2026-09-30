import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { EmployeeService } from '../../core/services/employee.service';
import { ExpiringDocument } from '../../core/models/employee.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

/** HR view of visas, contracts and certifications that have expired or are about to. */
@Component({
  selector: 'app-expiring-documents',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonToggleModule, MatIconModule, PageHeaderComponent, EmptyStateComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Expiring documents" subtitle="Visas, contracts and certifications that need attention"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Expiring documents' }]">
        <mat-button-toggle-group [value]="days()" (change)="setDays($event.value)" aria-label="Window">
          <mat-button-toggle [value]="30">30 days</mat-button-toggle>
          <mat-button-toggle [value]="60">60 days</mat-button-toggle>
          <mat-button-toggle [value]="90">90 days</mat-button-toggle>
        </mat-button-toggle-group>
      </hg-page-header>

      <div class="summary">
        <div class="hg-card stat danger"><strong>{{ expired().length }}</strong><span>Expired</span></div>
        <div class="hg-card stat warn"><strong>{{ upcoming().length }}</strong><span>Expiring within {{ days() }} days</span></div>
      </div>

      <div class="hg-card">
        @for (d of docs(); track d.documentId) {
          <a class="row" [routerLink]="['/employees', d.employeeId]">
            <mat-icon aria-hidden="true" [class.bad]="d.daysToExpiry < 0">{{ d.daysToExpiry < 0 ? 'error' : 'schedule' }}</mat-icon>
            <div class="info"><strong>{{ d.title }}</strong>
              <small class="muted">{{ d.employeeName }} ({{ d.employeeCode }}) · {{ d.category | humanize }}</small></div>
            <span class="hg-chip" [class]="d.daysToExpiry < 0 ? 'hg-chip hg-chip-danger' : 'hg-chip hg-chip-warn'">
              {{ d.daysToExpiry < 0 ? 'Expired ' + (-d.daysToExpiry) + 'd ago' : d.daysToExpiry === 0 ? 'Today' : 'in ' + d.daysToExpiry + 'd' }}
            </span>
            <small class="muted">{{ d.expiryDate | date: 'mediumDate' }}</small>
          </a>
        } @empty {
          @if (!loading()) { <hg-empty-state icon="verified" title="All clear" message="Nothing expires in this window." /> }
        }
      </div>
    </div>
  `,
  styles: [`
    .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 16px; }
    .stat { display: flex; flex-direction: column; }
    .stat strong { font-size: 28px; }
    .stat.danger strong { color: #b91c1c; }
    .stat.warn strong { color: #b45309; }
    .row { display: flex; align-items: center; gap: 12px; padding: 12px 4px; border-bottom: 1px solid var(--hg-border);
           color: inherit; text-decoration: none; flex-wrap: wrap; }
    .row:last-child { border-bottom: none; }
    .row:hover { background: color-mix(in srgb, var(--hg-primary) 6%, transparent); }
    .info { flex: 1 1 260px; display: flex; flex-direction: column; }
    mat-icon.bad { color: #b91c1c; }
  `]
})
export class ExpiringDocumentsComponent {
  private employees = inject(EmployeeService);

  days = signal(30);
  docs = signal<ExpiringDocument[]>([]);
  loading = signal(true);
  expired = computed(() => this.docs().filter((d) => d.daysToExpiry < 0));
  upcoming = computed(() => this.docs().filter((d) => d.daysToExpiry >= 0));

  constructor() {
    this.load();
  }

  setDays(d: number): void {
    this.days.set(d);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.employees.expiringDocuments(this.days()).subscribe({
      next: (list) => { this.docs.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
