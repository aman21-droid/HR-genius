import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { OnboardingService } from '../../core/services/onboarding.service';
import {
  OnboardingPlan, OnboardingTask, OnboardingTemplate, PlanSummary, TaskStatus
} from '../../core/models/onboarding.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { TaskListComponent } from './task-list.component';
import { ReassignTaskDialogComponent, StartPlanDialogComponent, TemplateDialogComponent } from './onboarding-dialogs.component';

/**
 * Onboarding hub. Everyone sees the tasks assigned to them and (if they are a new joiner) their own
 * checklist; HR with ONBOARDING_MANAGE also gets the new-hire tracker and the template library.
 */
@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [DatePipe, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatMenuModule, MatProgressBarModule,
    PageHeaderComponent, StatusChipComponent, EmptyStateComponent, HumanizePipe, TaskListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Onboarding" subtitle="Joining checklists for new hires"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Onboarding' }]">
        @if (canManage) {
          <button mat-flat-button color="primary" (click)="startPlan()"><mat-icon>rocket_launch</mat-icon> Start onboarding</button>
        }
      </hg-page-header>

      <mat-tab-group [selectedIndex]="0" animationDuration="0ms">
        <mat-tab>
          <ng-template mat-tab-label>My tasks @if (myTasks().length) { <span class="badge">{{ myTasks().length }}</span> }</ng-template>
          <div class="pad">
            <app-task-list [tasks]="myTasks()" [canWork]="canWorkMine" [showHire]="true"
                           emptyText="No onboarding tasks are waiting on you." (statusChange)="setStatus($event)" />
          </div>
        </mat-tab>

        @if (myPlan(); as p) {
          <mat-tab label="My onboarding">
            <div class="pad">
              <div class="welcome">
                <h2>Welcome, {{ p.employeeName.split(' ')[0] }}!</h2>
                <p class="muted">Your first day: {{ p.startDate | date: 'EEEE, MMM d, y' }}{{ p.managerName ? ' · Manager: ' + p.managerName : '' }}</p>
                <mat-progress-bar mode="determinate" [value]="progress(p)" />
                <p class="muted small">{{ doneCount(p) }} of {{ p.tasks.length }} steps complete</p>
              </div>
              <app-task-list [tasks]="p.tasks" [canWork]="canWorkMine" (statusChange)="setStatus($event)" />
            </div>
          </mat-tab>
        }

        @if (canManage) {
          <mat-tab label="New hires">
            <div class="pad">
              @for (p of plans(); track p.id) {
                <a class="plan" [routerLink]="['/onboarding/plans', p.id]">
                  <div class="who">
                    <strong>{{ p.employeeName }}</strong>
                    <span class="muted small">{{ p.employeeCode }} · {{ p.designation }} · {{ p.department }}</span>
                  </div>
                  <div class="when small">Joins {{ p.startDate | date: 'MMM d, y' }}</div>
                  <div class="prog">
                    <mat-progress-bar mode="determinate" [value]="100 * p.doneTasks / (p.totalTasks || 1)" />
                    <span class="small">{{ p.doneTasks }}/{{ p.totalTasks }}
                      @if (p.overdueTasks) { · <span class="late">{{ p.overdueTasks }} overdue</span> }</span>
                  </div>
                  <hg-status [value]="p.status" />
                </a>
              } @empty {
                <hg-empty-state icon="how_to_reg" title="No onboarding plans yet"
                                message="Plans appear here when a hire is converted from Recruitment, or when you start one." />
              }
            </div>
          </mat-tab>

          <mat-tab label="Templates">
            <div class="pad">
              <div class="tpl-head"><button mat-stroked-button (click)="editTemplate(null)"><mat-icon>add</mat-icon> New template</button></div>
              @for (t of templates(); track t.id) {
                <article class="tpl">
                  <div class="tpl-top">
                    <div>
                      <strong>{{ t.name }}</strong>
                      @if (t.defaultTemplate) { <span class="def">Default</span> }
                      @if (!t.active) { <span class="muted small"> · inactive</span> }
                      @if (t.description) { <div class="muted small">{{ t.description }}</div> }
                    </div>
                    <button mat-icon-button [matMenuTriggerFor]="tm" [attr.aria-label]="'Actions for ' + t.name"><mat-icon>more_vert</mat-icon></button>
                    <mat-menu #tm="matMenu">
                      <button mat-menu-item (click)="editTemplate(t)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                      @if (!t.defaultTemplate) {
                        <button mat-menu-item (click)="deleteTemplate(t)"><mat-icon>delete</mat-icon><span>Delete</span></button>
                      }
                    </mat-menu>
                  </div>
                  <ol class="steps">
                    @for (s of t.tasks; track s.id) {
                      <li><span class="role">{{ s.ownerRole | humanize }}</span> {{ s.title }}
                        <span class="muted small">· {{ offsetLabel(s.dueOffsetDays) }}</span></li>
                    }
                  </ol>
                </article>
              }
            </div>
          </mat-tab>
        }
      </mat-tab-group>
    </div>
  `,
  styles: `
    .pad { padding: 1rem 0.25rem; }
    .badge { margin-left: 0.4rem; min-width: 1.25rem; padding: 0 0.35rem; border-radius: 999px; font-size: 0.72rem; line-height: 1.25rem;
      background: var(--mat-sys-primary, #1565c0); color: #fff; text-align: center; }
    .small { font-size: 0.8rem; }
    .welcome { margin-bottom: 1rem; max-width: 520px; }
    .welcome h2 { margin: 0 0 0.25rem; font-size: 1.2rem; }
    .plan { display: grid; grid-template-columns: minmax(200px, 2fr) 1fr minmax(160px, 1.5fr) auto; gap: 1rem; align-items: center;
      padding: 0.75rem 1rem; border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; margin-bottom: 0.5rem;
      color: inherit; text-decoration: none; }
    .plan:hover { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    .plan .who { display: flex; flex-direction: column; }
    .prog { display: grid; gap: 0.25rem; }
    .late { color: var(--mat-sys-error, #c62828); font-weight: 600; }
    @media (max-width: 760px) { .plan { grid-template-columns: 1fr; } }
    .tpl-head { display: flex; justify-content: flex-end; margin-bottom: 0.75rem; }
    .tpl { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.75rem 1rem; margin-bottom: 0.75rem; }
    .tpl-top { display: flex; justify-content: space-between; align-items: flex-start; }
    .def { margin-left: 0.5rem; font-size: 0.7rem; padding: 0 0.45rem; border-radius: 999px; background: #dcfce7; color: #166534; }
    .steps { margin: 0.5rem 0 0; padding-left: 1.25rem; display: grid; gap: 0.2rem; font-size: 0.86rem; }
    .role { font-size: 0.7rem; font-weight: 600; padding: 0 0.4rem; border-radius: 999px; background: #e5e7eb; color: #374151; }
  `
})
export class OnboardingComponent {
  private onboarding = inject(OnboardingService);
  private auth = inject(AuthService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);
  private router = inject(Router);

  readonly canManage = this.auth.hasPermission('ONBOARDING_MANAGE');
  private readonly me = this.auth.user()?.employeeId ?? null;

  myTasks = signal<OnboardingTask[]>([]);
  myPlan = signal<OnboardingPlan | null>(null);
  plans = signal<PlanSummary[]>([]);
  templates = signal<OnboardingTemplate[]>([]);

  /** A task is workable from these lists if it is assigned to me (HR may work anything). */
  canWorkMine = (t: OnboardingTask): boolean => this.canManage || (this.me != null && t.assigneeId === this.me);

  constructor() {
    this.reload();
  }

  reload(): void {
    if (this.me != null) {
      this.onboarding.myTasks().subscribe((t) => this.myTasks.set(t));
      this.onboarding.myPlan().subscribe((res) => this.myPlan.set(res.body ?? null));
    }
    if (this.canManage) {
      this.onboarding.plans().subscribe((p) => this.plans.set(p));
      this.onboarding.templates().subscribe((t) => this.templates.set(t));
    }
  }

  setStatus(e: { task: OnboardingTask; status: TaskStatus }): void {
    this.onboarding.setTaskStatus(e.task.id, e.status).subscribe(() => {
      this.snack.open(e.status === 'DONE' ? 'Nice, task done' : e.status === 'SKIPPED' ? 'Task skipped' : 'Task reopened',
        undefined, { duration: 2000 });
      this.reload();
    });
  }

  startPlan(): void {
    this.dialog.open(StartPlanDialogComponent, { width: '520px', maxWidth: '95vw' }).afterClosed()
      .subscribe((p?: OnboardingPlan) => p && this.router.navigate(['/onboarding/plans', p.id]));
  }

  editTemplate(t: OnboardingTemplate | null): void {
    this.dialog.open(TemplateDialogComponent, { data: t, width: '820px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => {
        if (ok) { this.snack.open('Template saved', undefined, { duration: 2500 }); this.reload(); }
      });
  }

  deleteTemplate(t: OnboardingTemplate): void {
    this.confirm.ask({ title: `Delete "${t.name}"?`, message: 'Existing onboarding plans are not affected.', confirmText: 'Delete', danger: true })
      .subscribe((yes) => yes && this.onboarding.deleteTemplate(t.id).subscribe(() => {
        this.snack.open('Template deleted', undefined, { duration: 2500 });
        this.reload();
      }));
  }

  reassign(t: OnboardingTask): void {
    this.dialog.open(ReassignTaskDialogComponent, { data: t, width: '480px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => ok && this.reload());
  }

  doneCount(p: OnboardingPlan): number {
    return p.tasks.filter((t) => t.status !== 'PENDING').length;
  }

  progress(p: OnboardingPlan): number {
    return p.tasks.length ? (100 * this.doneCount(p)) / p.tasks.length : 0;
  }

  offsetLabel(days: number): string {
    if (days === 0) { return 'on day one'; }
    return days < 0 ? `${-days} day${days === -1 ? '' : 's'} before joining` : `day ${days + 1}`;
  }
}
