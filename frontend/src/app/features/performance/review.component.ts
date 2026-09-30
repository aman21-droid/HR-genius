import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PerformanceService } from '../../core/services/performance.service';
import { GOAL_STATUSES, Goal, GoalStatus, Review } from '../../core/models/performance.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { GoalDialogComponent } from './performance-dialogs.component';

interface Draft {
  overall: number | null;
  comments: string;
  goals: Record<number, { rating: number | null; comment: string }>;
}

/**
 * One review. The same page serves the employee (goals, progress, self-assessment, acknowledge),
 * the reviewer (manager assessment) and HR (read-only); the server says what the viewer may do.
 */
@Component({
  selector: 'app-review',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatSliderModule, MatMenuModule, PageHeaderComponent, StatusChipComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      @if (r(); as r) {
        <hg-page-header [title]="r.viewerRole === 'SELF' ? 'My review · ' + r.cycleName : r.employeeName + ' · ' + r.cycleName"
                        [subtitle]="(r.designation ?? '') + ' · reviewer ' + r.reviewerName"
                        [breadcrumbs]="[{ label: 'Performance', link: '/performance' }, { label: r.cycleName }]">
          <hg-status [value]="r.status" />
        </hg-page-header>

        <p class="banner" role="status">{{ guidance() }}</p>

        <!-- ================= goals ================= -->
        <section class="card">
          <header>
            <h2>Goals <span class="muted small">· weights {{ r.totalWeight }}%</span>
              @if (r.totalWeight !== 100 && r.goals.length) { <span class="warn small"> (must total 100%)</span> }</h2>
            @if (r.canEditGoals) { <button mat-stroked-button (click)="editGoal(null)"><mat-icon>add</mat-icon> Add goal</button> }
          </header>
          @for (g of r.goals; track g.id) {
            <article class="goal">
              <div class="g-top">
                <div class="grow">
                  <strong>{{ g.title }}</strong> <span class="weight">{{ g.weight }}%</span>
                  @if (g.description) { <div class="muted small">{{ g.description }}</div> }
                  <div class="muted small">@if (g.targetDate) { Target {{ g.targetDate | date: 'MMM d, y' }} · } <hg-status [value]="g.status" /></div>
                </div>
                @if (r.canEditGoals) {
                  <button mat-icon-button [matMenuTriggerFor]="gm" [attr.aria-label]="'Actions for ' + g.title"><mat-icon>more_vert</mat-icon></button>
                  <mat-menu #gm="matMenu">
                    <button mat-menu-item (click)="editGoal(g)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                    <button mat-menu-item (click)="deleteGoal(g)"><mat-icon>delete</mat-icon><span>Remove</span></button>
                  </mat-menu>
                }
              </div>
              <div class="progress">
                <div class="track" [attr.aria-label]="'Progress ' + g.progress + '%'"><i [style.width.%]="g.progress"></i></div>
                <span class="small">{{ g.progress }}%</span>
                @if (r.canUpdateProgress) {
                  <button mat-button (click)="toggleProgress(g)">{{ editing() === g.id ? 'Done' : 'Update' }}</button>
                }
              </div>
              @if (editing() === g.id) {
                <div class="prog-edit">
                  <mat-slider min="0" max="100" step="5" discrete class="grow"><input matSliderThumb [(ngModel)]="progressValue" /></mat-slider>
                  <mat-form-field appearance="outline" class="st"><mat-label>Status</mat-label>
                    <mat-select [(ngModel)]="progressStatus">
                      @for (s of goalStatuses; track s) { <mat-option [value]="s">{{ s | humanize }}</mat-option> }
                    </mat-select></mat-form-field>
                  <button mat-flat-button color="primary" (click)="saveProgress(g)">Save</button>
                </div>
              }

              <!-- ratings shown once visible to this viewer -->
              <div class="ratings">
                @if (g.selfRating != null && !selfForm()) {
                  <div><small>Self</small> <span class="stars">{{ stars(g.selfRating) }}</span> <span class="muted small">{{ g.selfComment }}</span></div>
                }
                @if (g.managerRating != null && !managerForm()) {
                  <div><small>Manager</small> <span class="stars">{{ stars(g.managerRating) }}</span> <span class="muted small">{{ g.managerComment }}</span></div>
                }
                @if (selfForm() || managerForm()) {
                  <div class="rate">
                    <small>{{ selfForm() ? 'Your rating' : 'Your rating as reviewer' }}</small>
                    <span class="picker" role="radiogroup" [attr.aria-label]="'Rating for ' + g.title">
                      @for (n of five; track n) {
                        <button type="button" role="radio" [attr.aria-checked]="draft.goals[g.id].rating === n"
                                [class.on]="(draft.goals[g.id].rating ?? 0) >= n" (click)="setGoalRating(g.id, n)"
                                [attr.aria-label]="n + ' of 5'">★</button>
                      }
                    </span>
                    <input class="cmt" [(ngModel)]="draft.goals[g.id].comment" placeholder="Comment (optional)" maxlength="1000" />
                    @if (managerForm() && g.selfRating != null) { <span class="muted small">Self: {{ stars(g.selfRating) }}</span> }
                  </div>
                }
              </div>
            </article>
          } @empty {
            <p class="muted">{{ r.canEditGoals ? 'Add 3–5 goals for this period. Weights must add up to 100%.' : 'No goals were set.' }}</p>
          }
        </section>

        <!-- ================= assessments ================= -->
        @if (selfForm() || managerForm()) {
          <section class="card">
            <h2>{{ selfForm() ? 'Self-assessment' : 'Manager assessment' }}</h2>
            <div class="rate big">
              <small>Overall rating</small>
              <span class="picker" role="radiogroup" aria-label="Overall rating">
                @for (n of five; track n) {
                  <button type="button" role="radio" [attr.aria-checked]="draft.overall === n" [class.on]="(draft.overall ?? 0) >= n"
                          (click)="draft.overall = n" [attr.aria-label]="n + ' of 5'">★</button>
                }
              </span>
              <span class="muted small">{{ ratingLabel(draft.overall) }}</span>
            </div>
            <mat-form-field appearance="outline" class="full">
              <mat-label>{{ selfForm() ? 'Summary of your period' : 'Feedback for ' + r.employeeName }}</mat-label>
              <textarea matInput rows="5" [(ngModel)]="draft.comments" maxlength="4000"></textarea>
            </mat-form-field>
            <div class="acts">
              <button mat-stroked-button (click)="save(false)" [disabled]="busy()">Save draft</button>
              <button mat-flat-button color="primary" (click)="save(true)" [disabled]="busy()">Submit</button>
            </div>
          </section>
        }

        @if (r.selfRating != null && !selfForm()) {
          <section class="card">
            <h2>Self-assessment <span class="stars">{{ stars(r.selfRating) }}</span></h2>
            <p class="pre">{{ r.selfComments || '—' }}</p>
            <p class="muted small">Submitted {{ r.selfSubmittedAt | date: 'MMM d, y' }}</p>
          </section>
        }
        @if (r.managerRating != null && !managerForm()) {
          <section class="card">
            <h2>Manager assessment <span class="stars">{{ stars(r.managerRating) }}</span>
              @if (r.finalScore != null) { <span class="score">Final score {{ r.finalScore | number: '1.2-2' }} / 5</span> }</h2>
            <p class="pre">{{ r.managerComments || '—' }}</p>
            <p class="muted small">By {{ r.reviewerName }}, {{ r.managerSubmittedAt | date: 'MMM d, y' }}</p>
          </section>
        }

        @if (r.canAcknowledge) {
          <section class="card">
            <h2>Acknowledge your review</h2>
            <p class="muted small">Acknowledging confirms you have read it; add a comment if you'd like it on record.</p>
            <mat-form-field appearance="outline" class="full"><mat-label>Comment (optional)</mat-label>
              <textarea matInput rows="2" [(ngModel)]="ackComment" maxlength="1000"></textarea></mat-form-field>
            <div class="acts"><button mat-flat-button color="primary" (click)="acknowledge()" [disabled]="busy()">Acknowledge</button></div>
          </section>
        }
        @if (r.acknowledgedAt) {
          <p class="muted small">Acknowledged {{ r.acknowledgedAt | date: 'MMM d, y' }}{{ r.ackComment ? ': “' + r.ackComment + '”' : '' }}</p>
        }
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .banner { padding: 0.6rem 0.9rem; border-radius: 10px; background: var(--mat-sys-surface-container, rgba(0,0,0,0.04)); margin: 0 0 1rem; font-size: 0.9rem; }
    .card { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 1rem 1.15rem; margin-bottom: 1rem; }
    .card > header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .card h2 { font-size: 1rem; margin: 0 0 0.5rem; display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    .small { font-size: 0.8rem; } .warn { color: #b45309; }
    .goal { padding: 0.8rem 0; border-top: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .goal:first-of-type { border-top: none; }
    .g-top { display: flex; gap: 0.5rem; align-items: flex-start; }
    .grow { flex: 1; min-width: 0; }
    .weight { font-size: 0.75rem; padding: 0 0.4rem; border-radius: 999px; background: var(--mat-sys-surface-container, #eef2ff); }
    .progress { display: flex; align-items: center; gap: 0.6rem; margin-top: 0.4rem; }
    .track { flex: 1; max-width: 360px; height: 8px; border-radius: 4px; background: var(--hg-border, rgba(0,0,0,0.1)); overflow: hidden; }
    .track i { display: block; height: 100%; background: var(--mat-sys-primary, #1565c0); }
    .prog-edit { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
    .prog-edit .st { width: 180px; }
    .ratings { display: grid; gap: 0.3rem; margin-top: 0.5rem; font-size: 0.86rem; }
    .ratings small { display: inline-block; min-width: 64px; opacity: 0.7; }
    .stars { color: #f59e0b; letter-spacing: 1px; }
    .rate { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
    .rate.big .picker button { font-size: 1.6rem; }
    .picker button { border: none; background: none; cursor: pointer; font-size: 1.25rem; color: #d1d5db; padding: 0 1px; line-height: 1; }
    .picker button.on { color: #f59e0b; }
    .picker button:focus-visible { outline: 2px solid var(--mat-sys-primary, #1565c0); border-radius: 4px; }
    .cmt { flex: 1; min-width: 180px; border: 1px solid var(--hg-border, rgba(0,0,0,0.2)); border-radius: 8px; padding: 0.35rem 0.6rem;
      font: inherit; background: transparent; color: inherit; }
    .full { width: 100%; margin-top: 0.75rem; }
    .acts { display: flex; justify-content: flex-end; gap: 0.5rem; }
    .pre { white-space: pre-line; margin: 0.25rem 0; }
    .score { margin-left: auto; font-size: 0.9rem; font-weight: 600; }
  `
})
export class ReviewComponent {
  id = input.required<string>();

  private performance = inject(PerformanceService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  readonly five = [1, 2, 3, 4, 5];
  readonly goalStatuses = GOAL_STATUSES;
  r = signal<Review | null>(null);
  busy = signal(false);
  editing = signal<number | null>(null);
  progressValue = 0;
  progressStatus: GoalStatus = 'ON_TRACK';
  ackComment = '';
  draft: Draft = { overall: null, comments: '', goals: {} };

  selfForm = computed(() => !!this.r()?.canSelfReview);
  managerForm = computed(() => !!this.r()?.canManagerReview);

  guidance = computed(() => {
    const r = this.r();
    if (!r) { return ''; }
    if (r.cycleStatus === 'CLOSED') { return `The ${r.cycleName} cycle is closed.`; }
    switch (r.viewerRole) {
      case 'SELF':
        switch (r.status) {
          case 'NOT_STARTED': return `Set your goals, keep progress up to date, then rate yourself and submit${r.selfReviewDue ? ' by ' + new Date(r.selfReviewDue).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : ''}.`;
          case 'SELF_SUBMITTED': return `Submitted. ${r.reviewerName} will now complete your review.`;
          case 'MANAGER_SUBMITTED': return 'Your review is complete. Read it and acknowledge below.';
          default: return 'Review complete and acknowledged.';
        }
      case 'MANAGER':
        switch (r.status) {
          case 'NOT_STARTED': return `${r.employeeName} hasn't submitted their self-assessment yet. You can help shape their goals meanwhile.`;
          case 'SELF_SUBMITTED': return `Rate each goal and give an overall rating${r.managerReviewDue ? ' by ' + new Date(r.managerReviewDue).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : ''}. ${r.employeeName} sees it only after you submit.`;
          case 'MANAGER_SUBMITTED': return `Submitted. Waiting for ${r.employeeName} to acknowledge.`;
          default: return 'Review complete and acknowledged.';
        }
      default:
        return 'HR view: read-only.';
    }
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.performance.review(Number(this.id())).subscribe((r) => this.apply(r));
  }

  private apply(r: Review): void {
    this.r.set(r);
    const self = r.canSelfReview;
    this.draft = {
      overall: self ? r.selfRating : r.managerRating,
      comments: (self ? r.selfComments : r.managerComments) ?? '',
      goals: Object.fromEntries(r.goals.map((g) => [g.id, {
        rating: self ? g.selfRating : g.managerRating,
        comment: (self ? g.selfComment : g.managerComment) ?? ''
      }]))
    };
  }

  setGoalRating(goalId: number, n: number): void {
    this.draft.goals[goalId] = { ...(this.draft.goals[goalId] ?? { comment: '' }), rating: n };
  }

  editGoal(g: Goal | null): void {
    const r = this.r()!;
    const used = r.goals.filter((x) => x.id !== g?.id).reduce((s, x) => s + x.weight, 0);
    this.dialog.open(GoalDialogComponent, { data: { reviewId: r.id, goal: g, remainingWeight: Math.max(0, 100 - used) },
      width: '600px', maxWidth: '95vw' }).afterClosed().subscribe((ok) => ok && this.reload());
  }

  deleteGoal(g: Goal): void {
    this.confirm.ask({ title: `Remove "${g.title}"?`, message: 'Its weight becomes unallocated.', confirmText: 'Remove', danger: true })
      .subscribe((yes) => yes && this.performance.deleteGoal(this.r()!.id, g.id).subscribe((r) => this.apply(r)));
  }

  toggleProgress(g: Goal): void {
    if (this.editing() === g.id) { this.editing.set(null); return; }
    this.progressValue = g.progress;
    this.progressStatus = g.status === 'NOT_STARTED' ? 'ON_TRACK' : g.status;
    this.editing.set(g.id);
  }

  saveProgress(g: Goal): void {
    this.performance.progress(this.r()!.id, g.id, this.progressValue, this.progressStatus).subscribe((r) => {
      this.apply(r);
      this.editing.set(null);
      this.snack.open('Progress updated', undefined, { duration: 2000 });
    });
  }

  save(submit: boolean): void {
    const r = this.r()!;
    const body = {
      overallRating: this.draft.overall, comments: this.draft.comments || null, submit,
      goals: r.goals.map((g) => ({ goalId: g.id, rating: this.draft.goals[g.id].rating ?? null, comment: this.draft.goals[g.id]?.comment || null }))
    };
    const go = () => {
      this.busy.set(true);
      (this.selfForm() ? this.performance.selfAssessment(r.id, body) : this.performance.managerAssessment(r.id, body)).subscribe({
        next: (res) => { this.busy.set(false); this.apply(res); this.snack.open(submit ? 'Submitted' : 'Draft saved', undefined, { duration: 2500 }); },
        error: () => this.busy.set(false)
      });
    };
    if (!submit) { go(); return; }
    this.confirm.ask({ title: 'Submit your assessment?', message: 'You cannot change it after submitting.', confirmText: 'Submit' })
      .subscribe((yes) => yes && go());
  }

  acknowledge(): void {
    this.busy.set(true);
    this.performance.acknowledge(this.r()!.id, this.ackComment.trim() || null).subscribe({
      next: (res) => { this.busy.set(false); this.apply(res); this.snack.open('Review acknowledged', undefined, { duration: 2500 }); },
      error: () => this.busy.set(false)
    });
  }

  stars(n: number | null): string {
    return n == null ? '' : '★'.repeat(n) + '☆'.repeat(5 - n);
  }

  ratingLabel(n: number | null): string {
    return ['', 'Needs improvement', 'Partially meets', 'Meets expectations', 'Exceeds expectations', 'Outstanding'][n ?? 0];
  }
}
