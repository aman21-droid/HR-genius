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
        return 'success';
      case 'PROBATION':
      case 'ASSIGNED':
      case 'ON_LEAVE':
      case 'HOLIDAY':
        return 'info';
      case 'ON_NOTICE':
      case 'IN_REPAIR':
      case 'PENDING':
      case 'HALF_DAY':
        return 'warn';
      case 'EXITED':
      case 'RETIRED':
      case 'EXPIRED':
      case 'REJECTED':
      case 'ABSENT':
        return 'danger';
      default:
        // CANCELLED, WEEKEND, NOT_MARKED and anything unmapped
        return 'neutral';
    }
  });
}
