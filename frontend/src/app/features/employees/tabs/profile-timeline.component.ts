import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { EmployeeService } from '../../../core/services/employee.service';
import { TimelineEvent } from '../../../core/models/employee.models';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';

const ICONS: Record<string, string> = {
  JOINED: 'login', CONFIRMED: 'verified', PROMOTED: 'trending_up', DESIGNATION_CHANGED: 'badge',
  TRANSFERRED: 'swap_horiz', MANAGER_CHANGED: 'supervisor_account', SALARY_REVISED: 'payments',
  STATUS_CHANGED: 'flag', EXITED: 'logout', REHIRED: 'replay'
};

@Component({
  selector: 'app-profile-timeline',
  standalone: true,
  imports: [DatePipe, MatIconModule, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hg-card">
      <h3 class="hg-section-title">Career timeline</h3>
      @if (events().length) {
        <ol class="timeline">
          @for (ev of events(); track ev.id) {
            <li [class]="'ev-' + ev.eventType.toLowerCase()">
              <span class="dot" aria-hidden="true"><mat-icon>{{ icon(ev.eventType) }}</mat-icon></span>
              <div class="body">
                <time>{{ ev.eventDate | date: 'mediumDate' }}</time>
                <strong>{{ ev.title }}</strong>
                @if (ev.description) { <p class="muted">{{ ev.description }}</p> }
              </div>
            </li>
          }
        </ol>
      } @else if (!loading()) {
        <hg-empty-state icon="timeline" title="No events yet" />
      }
    </section>
  `,
  styles: [`
    .timeline { list-style: none; margin: 0; padding: 0 0 0 8px; border-left: 2px solid var(--hg-border); }
    .timeline li { position: relative; padding: 0 0 20px 28px; }
    .dot { position: absolute; left: -17px; top: 0; width: 32px; height: 32px; border-radius: 50%;
           display: flex; align-items: center; justify-content: center; background: var(--hg-surface);
           border: 2px solid var(--hg-border); color: var(--hg-primary); }
    .dot mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .body { display: flex; flex-direction: column; padding-top: 4px; }
    time { font-size: 12px; color: var(--hg-muted); }
    .body p { margin: 2px 0 0; }
    .ev-promoted .dot { color: #16a34a; border-color: #16a34a; }
    .ev-exited .dot { color: #b91c1c; border-color: #b91c1c; }
  `]
})
export class ProfileTimelineComponent implements OnInit {
  employeeId = input.required<number>();
  private employees = inject(EmployeeService);

  events = signal<TimelineEvent[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.employees.timeline(this.employeeId()).subscribe({
      next: (list) => { this.events.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  icon(type: string): string {
    return ICONS[type] ?? 'circle';
  }
}
