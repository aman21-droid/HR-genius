import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { humanize } from '../pipes/labels.pipe';

/** Coloured pill for statuses; colour is derived from the value, text stays readable (WCAG AA). */
@Component({
  selector: 'hg-status',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="hg-chip" [class]="'hg-chip hg-chip-' + tone()">{{ label() }}</span>`
})
export class StatusChipComponent {
  value = input.required<string | null | undefined>();

  label = computed(() => humanize(this.value()));

  tone = computed(() => {
    switch (this.value()) {
      case 'ACTIVE':
      case 'AVAILABLE':
      case 'VERIFIED':
      case 'APPROVED':
      case 'PRESENT':
      case 'REGULARIZED':
      case 'OPEN':
      case 'ACCEPTED':
      case 'HIRED':
      case 'COMPLETED':
      case 'DONE':
      case 'PAID':
      case 'ACKNOWLEDGED':
      case 'ON_TRACK':
      case 'RESOLVED':
      case 'PUBLISHED':
        return 'success';
      case 'PROBATION':
      case 'ASSIGNED':
      case 'ON_LEAVE':
      case 'HOLIDAY':
      case 'SENT':
      case 'SCHEDULED':
      case 'SCREENING':
      case 'INTERVIEW':
      case 'IN_PROGRESS':
      case 'CALCULATED':
      case 'SELF_SUBMITTED':
      case 'MANAGER_SUBMITTED':
        return 'info';
      case 'ON_NOTICE':
      case 'IN_REPAIR':
      case 'PENDING':
      case 'HALF_DAY':
      case 'PENDING_APPROVAL':
      case 'ON_HOLD':
      case 'OFFER':
      case 'AT_RISK':
        return 'warn';
      case 'EXITED':
      case 'RETIRED':
      case 'EXPIRED':
      case 'REJECTED':
      case 'ABSENT':
      case 'DECLINED':
      case 'NO_SHOW':
      case 'OFF_TRACK':
        return 'danger';
      default:
        // CANCELLED, WEEKEND, NOT_MARKED, DRAFT, CLOSED, WITHDRAWN, APPLIED, SKIPPED and anything unmapped
        return 'neutral';
    }
  });
}
