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
        return 'success';
      case 'PROBATION':
      case 'ASSIGNED':
        return 'info';
      case 'ON_NOTICE':
      case 'IN_REPAIR':
      case 'PENDING':
        return 'warn';
      case 'EXITED':
      case 'RETIRED':
      case 'EXPIRED':
        return 'danger';
      default:
        return 'neutral';
    }
  });
}
