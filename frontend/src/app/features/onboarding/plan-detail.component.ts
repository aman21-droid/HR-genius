import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { OnboardingService } from '../../core/services/onboarding.service';
import { OWNER_ROLES, OnboardingPlan, OnboardingTask, OwnerRole, TaskStatus } from '../../core/models/onboarding.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { TaskListComponent } from './task-list.component';
import { ReassignTaskDialogComponent } from './onboarding-dialogs.component';

/** One new hire's checklist, grouped by the team that owns each step. */
@Component({
  selector: 'app-plan-detail',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, PageHeaderComponent,
    StatusChipComponent, HumanizePipe, TaskListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      @if (plan(); as p) {
        <hg-page-header [title]="'Onboarding · ' + p.employeeName"
                        [subtitle]="p.employeeCode + ' · ' + (p.designation ?? '') + ' · ' + (p.department ?? '')"
                        [breadcrumbs]="[{ label: 'Onboarding', link: '/onboarding' }, { label: p.employeeName }]">
          <hg-status [value]="p.status" />
          <a mat-stroked-button [routerLink]="['/employees', p.employeeId]"><mat-icon>badge</mat-icon> Profile</a>
          @if (p.applicationId && canRecruit) {
            <a mat-stroked-button [routerLink]="['/recruitment/applications', p.applicationId]"><mat-icon>work_history</mat-icon> Hiring record</a>
          }
        </hg-page-header>

        <section class="summary">
          <div><small>Joining</small><strong>{{ p.startDate | date: 'EEE, MMM d, y' }}</strong></div>
          <div><small>Manager</small><strong>{{ p.managerName || '—' }}</strong></div>
          <div class="grow">
            <small>{{ done() }} of {{ p.tasks.length }} complete{{ overdue() ? ' · ' + overdue() + ' overdue' : '' }}</small>
            <mat-progress-bar aria-label="Onboarding completion" mode="determinate" [value]="p.tasks.length ? 100 * done() / p.tasks.length : 0" />
          </div>
        </section>

        @for (g of groups(); track g.role) {
          <section class="group">
            <h2>{{ g.role | humanize }} <span class="muted">{{ g.done }}/{{ g.tasks.length }}</span></h2>
            <app-task-list [tasks]="g.tasks" [canWork]="canWork" [canManage]="canManage"
                           (statusChange)="setStatus($event)" (reassign)="reassign($event)" />
          </section>
        }
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .summary { display: flex; flex-wrap: wrap; gap: 1.5rem; align-items: flex-end; padding: 1rem 1.25rem; margin-bottom: 1rem;
      border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; }
    .summary small { display: block; font-size: 0.75rem; opacity: 0.7; margin-bottom: 0.2rem; }
    .summary .grow { flex: 1; min-width: 220px; }
    .group { margin-bottom: 1rem; }
    .group h2 { font-size: 0.95rem; margin: 0.5rem 0 0.25rem; }
  `
})
export class PlanDetailComponent {
  id = input.required<string>();

  private onboarding = inject(OnboardingService);
  private auth = inject(AuthService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  readonly canManage = this.auth.hasPermission('ONBOARDING_MANAGE');
  readonly canRecruit = this.auth.hasPermission('RECRUITMENT_MANAGE');
  private readonly me = this.auth.user()?.employeeId ?? null;

  plan = signal<OnboardingPlan | null>(null);
  done = computed(() => (this.plan()?.tasks ?? []).filter((t) => t.status !== 'PENDING').length);
  overdue = computed(() => (this.plan()?.tasks ?? []).filter((t) => t.overdue).length);
  groups = computed(() => {
    const tasks = this.plan()?.tasks ?? [];
    return OWNER_ROLES
      .map((role: OwnerRole) => {
        const list = tasks.filter((t) => t.ownerRole === role);
        return { role, tasks: list, done: list.filter((t) => t.status !== 'PENDING').length };
      })
      .filter((g) => g.tasks.length);
  });

  canWork = (t: OnboardingTask): boolean => this.canManage || (this.me != null && t.assigneeId === this.me);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.onboarding.plan(Number(this.id())).subscribe((p) => this.plan.set(p));
  }

  setStatus(e: { task: OnboardingTask; status: TaskStatus }): void {
    this.onboarding.setTaskStatus(e.task.id, e.status).subscribe(() => {
      this.snack.open(e.status === 'DONE' ? 'Task done' : e.status === 'SKIPPED' ? 'Task skipped' : 'Task reopened',
        undefined, { duration: 2000 });
      this.reload();
    });
  }

  reassign(t: OnboardingTask): void {
    this.dialog.open(ReassignTaskDialogComponent, { data: t, width: '480px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => {
        if (ok) { this.snack.open('Task reassigned', undefined, { duration: 2000 }); this.reload(); }
      });
  }
}
