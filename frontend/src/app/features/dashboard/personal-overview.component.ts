import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MeSummary, TODO_ICONS } from '../../core/services/me.service';
import { ExpiringDocument } from '../../core/models/employee.models';

@Component({
  selector: 'hg-personal-overview',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './personal-overview.component.html',
  styleUrl: './personal-overview.component.scss'
})
export class PersonalOverviewComponent {
  readonly icons = TODO_ICONS;
  summary = input<MeSummary | null>(null);
  summaryError = input(false);
  hasProfile = input(false);
  isHr = input(false);
  canAnalytics = input(false);
  expiring = input<ExpiringDocument[]>([]);
  documentError = input(false);
  documentLoading = input(false);
}
