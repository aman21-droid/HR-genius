import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EmployeeService } from '../../core/services/employee.service';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeDetail, EmployeeSummary } from '../../core/models/employee.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe, InitialsPipe } from '../../shared/pipes/labels.pipe';
import { ProfileDocumentsComponent } from './tabs/profile-documents.component';
import { ProfileAssetsComponent } from './tabs/profile-assets.component';
import { ProfileTimelineComponent } from './tabs/profile-timeline.component';
import { ProfileContactsComponent } from './tabs/profile-contacts.component';
import { ProfileStatutoryComponent } from './tabs/profile-statutory.component';

/**
 * Employee profile. Route /employees/:id, or /profile for the signed-in user (no id).
 * Tabs appear only when the caller is allowed to see them, so no tab ever 403s.
 */
@Component({
  selector: 'app-employee-profile',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatMenuModule,
    MatTooltipModule, PageHeaderComponent, StatusChipComponent, HumanizePipe, InitialsPipe,
    ProfileDocumentsComponent, ProfileAssetsComponent, ProfileTimelineComponent, ProfileContactsComponent,
    ProfileStatutoryComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-profile.component.html'
})
export class EmployeeProfileComponent {
  id = input<string>();

  private employees = inject(EmployeeService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  employee = signal<EmployeeDetail | null>(null);
  reports = signal<EmployeeSummary[]>([]);
  notFound = signal(false);

  canDelete = this.auth.hasPermission('EMPLOYEE_WRITE') && this.auth.hasAnyRole(['SUPER_ADMIN', 'HR_ADMIN']);
  isSelf = computed(() => this.employee()?.id === this.auth.user()?.employeeId);

  constructor() {
    // Reload whenever the route id changes (e.g. clicking a direct report).
    effect(() => {
      const id = this.id();
      this.load(id ? +id : null);
    }, { allowSignalWrites: true });
  }

  private load(id: number | null): void {
    this.employee.set(null);
    this.reports.set([]);
    this.notFound.set(false);
    const source = id ? this.employees.get(id) : this.employees.me();
    source.subscribe({
      next: (e) => {
        this.employee.set(e);
        if (e.directReports > 0) {
          this.employees.list({ managerId: e.id }, { size: 50, sort: 'firstName,asc' })
            .subscribe((page) => this.reports.set(page.content));
        }
      },
      error: () => this.notFound.set(true)
    });
  }

  delete(): void {
    const e = this.employee();
    if (!e) return;
    this.confirm.ask({
      title: `Delete ${e.fullName}?`,
      message: 'The record is soft-deleted and their login is disabled. This is for records created by mistake. '
        + 'Use offboarding for people who are leaving.',
      confirmText: 'Delete',
      danger: true
    }).subscribe((ok) => {
      if (!ok) return;
      this.employees.delete(e.id).subscribe(() => {
        this.snack.open(`${e.fullName} deleted`, 'OK', { duration: 3000 });
        this.router.navigate(['/employees']);
      });
    });
  }

  tenure(e: EmployeeDetail): string {
    const start = new Date(e.dateOfJoining);
    const end = e.exitDate ? new Date(e.exitDate) : new Date();
    let months = (end.getFullYear() - start.getFullYear()) * 12 + end.getMonth() - start.getMonth();
    if (end.getDate() < start.getDate()) months--;
    if (months < 1) return 'New joiner';
    const y = Math.floor(months / 12);
    const m = months % 12;
    return [y ? `${y} yr${y > 1 ? 's' : ''}` : '', m ? `${m} mo` : ''].filter(Boolean).join(' ');
  }
}
