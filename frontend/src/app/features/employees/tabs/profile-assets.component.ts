import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { EmployeeService } from '../../../core/services/employee.service';
import { AssetAssignment } from '../../../core/models/employee.models';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';
import { HumanizePipe } from '../../../shared/pipes/labels.pipe';

const ICONS: Record<string, string> = {
  LAPTOP: 'laptop', MONITOR: 'desktop_windows', PHONE: 'smartphone', ID_CARD: 'badge',
  ACCESS_CARD: 'contactless', HEADSET: 'headset_mic', OTHER: 'inventory_2'
};

@Component({
  selector: 'app-profile-assets',
  standalone: true,
  imports: [DatePipe, MatIconModule, EmptyStateComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hg-card">
      <h3 class="hg-section-title">Currently assigned ({{ current().length }})</h3>
      @for (a of current(); track a.id) {
        <div class="asset">
          <mat-icon aria-hidden="true">{{ icon(a.category) }}</mat-icon>
          <div class="info"><strong>{{ a.assetName }}</strong>
            <small class="muted">{{ a.assetTag }} · {{ a.category | humanize }}{{ a.serialNumber ? ' · S/N ' + a.serialNumber : '' }}</small></div>
          <small class="muted">since {{ a.assignedOn | date: 'mediumDate' }}</small>
        </div>
      } @empty {
        @if (!loading()) { <hg-empty-state icon="devices_other" title="No assets assigned" /> }
      }
    </section>
    @if (past().length) {
      <section class="hg-card">
        <h3 class="hg-section-title">History</h3>
        @for (a of past(); track a.id) {
          <div class="asset past">
            <mat-icon aria-hidden="true">{{ icon(a.category) }}</mat-icon>
            <div class="info"><strong>{{ a.assetName }}</strong><small class="muted">{{ a.assetTag }}</small></div>
            <small class="muted">{{ a.assignedOn | date: 'mediumDate' }} – {{ a.returnedOn | date: 'mediumDate' }}
              {{ a.returnCondition ? '(' + (a.returnCondition | humanize) + ')' : '' }}</small>
          </div>
        }
      </section>
    }
  `,
  styles: [`
    .asset { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--hg-border); flex-wrap: wrap; }
    .asset:last-child { border-bottom: none; }
    .asset.past { opacity: .75; }
    .info { flex: 1 1 220px; display: flex; flex-direction: column; }
  `]
})
export class ProfileAssetsComponent implements OnInit {
  employeeId = input.required<number>();
  private employees = inject(EmployeeService);

  items = signal<AssetAssignment[]>([]);
  loading = signal(true);
  current = computed(() => this.items().filter((a) => !a.returnedOn));
  past = computed(() => this.items().filter((a) => !!a.returnedOn));

  ngOnInit(): void {
    this.employees.assets(this.employeeId()).subscribe({
      next: (list) => { this.items.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  icon(category: string): string {
    return ICONS[category] ?? 'inventory_2';
  }
}
