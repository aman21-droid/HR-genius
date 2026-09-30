import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { OnboardingTask, TaskStatus } from '../../core/models/onboarding.models';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

/**
 * Checklist rows for onboarding tasks. Emits status changes; the parent decides what may be
 * edited (a task is actionable when {@code canWork} returns true for it).
 */
@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [DatePipe, RouterLink, MatCheckboxModule, MatIconModule, MatButtonModule, MatMenuModule, MatTooltipModule,
    HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="tasks">
      @for (t of tasks(); track t.id) {
        <li [class.done]="t.status !== 'PENDING'" [class.overdue]="t.overdue">
          <mat-checkbox [checked]="t.status === 'DONE'" [indeterminate]="t.status === 'SKIPPED'"
                        [disabled]="!canWork()(t)" (change)="statusChange.emit({ task: t, status: $event.checked ? 'DONE' : 'PENDING' })"
                        [attr.aria-label]="'Mark ' + t.title + ' done'" />
          <div class="body">
            <div class="title">{{ t.title }}</div>
            @if (t.description) { <div class="desc">{{ t.description }}</div> }
            <div class="meta">
              <span class="role" [class]="'role role-' + t.ownerRole">{{ t.ownerRole | humanize }}</span>
              @if (showHire()) { <a [routerLink]="['/onboarding/plans', t.planId]">{{ t.newHireName }}</a> }
              @if (t.assigneeName) { <span><mat-icon inline>person</mat-icon> {{ t.assigneeName }}</span> }
              @else { <span class="muted"><mat-icon inline>groups</mat-icon> {{ t.ownerRole | humanize }} team</span> }
              @if (t.dueDate) {
                <span [class.late]="t.overdue"><mat-icon inline>event</mat-icon> {{ t.overdue ? 'Overdue · ' : 'Due ' }}{{ t.dueDate | date: 'MMM d' }}</span>
              }
              @if (t.completedBy) {
                <span class="muted">{{ t.status === 'SKIPPED' ? 'Skipped' : 'Done' }} by {{ t.completedBy }}</span>
              }
            </div>
          </div>
          @if (canWork()(t) || canManage()) {
            <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'More for ' + t.title"><mat-icon>more_vert</mat-icon></button>
            <mat-menu #m="matMenu">
              @if (canWork()(t) && t.status === 'PENDING') {
                <button mat-menu-item (click)="statusChange.emit({ task: t, status: 'SKIPPED' })"><mat-icon>redo</mat-icon><span>Skip (not needed)</span></button>
              }
              @if (canWork()(t) && t.status !== 'PENDING') {
                <button mat-menu-item (click)="statusChange.emit({ task: t, status: 'PENDING' })"><mat-icon>undo</mat-icon><span>Reopen</span></button>
              }
              @if (canManage()) {
                <button mat-menu-item (click)="reassign.emit(t)"><mat-icon>assignment_ind</mat-icon><span>Reassign / due date</span></button>
              }
            </mat-menu>
          }
        </li>
      } @empty {
        <li class="empty muted">{{ emptyText() }}</li>
      }
    </ul>
  `,
  styles: `
    .tasks { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.25rem; }
    li { display: flex; gap: 0.5rem; align-items: flex-start; padding: 0.55rem 0.4rem; border-radius: 10px; }
    li:hover { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    li.done .title { text-decoration: line-through; opacity: 0.6; }
    .body { flex: 1; min-width: 0; padding-top: 0.55rem; }
    .title { font-weight: 600; font-size: 0.92rem; }
    .desc { font-size: 0.82rem; opacity: 0.75; margin-top: 0.1rem; }
    .meta { display: flex; flex-wrap: wrap; gap: 0.35rem 0.9rem; font-size: 0.78rem; margin-top: 0.35rem; align-items: center; }
    .meta mat-icon { font-size: 14px; height: 14px; width: 14px; vertical-align: -2px; }
    .late { color: var(--mat-sys-error, #c62828); font-weight: 600; }
    .role { padding: 0 0.45rem; border-radius: 999px; font-weight: 600; font-size: 0.7rem; background: #e5e7eb; color: #374151; }
    .role-HR { background: #ede9fe; color: #5b21b6; } .role-MANAGER { background: #dbeafe; color: #1e40af; }
    .role-EMPLOYEE { background: #dcfce7; color: #166534; } .role-IT { background: #fef3c7; color: #92400e; }
    .role-FINANCE { background: #fce7f3; color: #9d174d; }
    .empty { padding: 1rem; }
  `
})
export class TaskListComponent {
  tasks = input.required<OnboardingTask[]>();
  canWork = input<(t: OnboardingTask) => boolean>(() => false);
  canManage = input(false);
  /** Show which new hire each task belongs to (for cross-plan "my tasks" lists). */
  showHire = input(false);
  emptyText = input('Nothing here.');

  statusChange = output<{ task: OnboardingTask; status: TaskStatus }>();
  reassign = output<OnboardingTask>();
}
