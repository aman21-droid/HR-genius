import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { PerformanceService } from '../../core/services/performance.service';
import { CycleSummary, FeedbackNote, ReviewCycle, ReviewSummary } from '../../core/models/performance.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe, InitialsPipe } from '../../shared/pipes/labels.pipe';
import { CycleDialogComponent, FeedbackDialogComponent } from './performance-dialogs.component';

/** Performance hub: my reviews, my team's reviews, kudos & feedback, and (HR) review cycles. */
@Component({
  selector: 'app-performance',
  standalone: true,
  imports: [DatePipe, DecimalPipe, NgTemplateOutlet, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, PageHeaderComponent,
    StatusChipComponent, EmptyStateComponent, HumanizePipe, InitialsPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Performance" subtitle="Goals, reviews and feedback"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Performance' }]">
        <button mat-stroked-button (click)="giveFeedback()"><mat-icon>volunteer_activism</mat-icon> Give feedback</button>
        @if (isAdmin) { <button mat-flat-button color="primary" (click)="newCycle()"><mat-icon>add</mat-icon> New cycle</button> }
      </hg-page-header>

      <mat-tab-group animationDuration="0ms">
        <mat-tab label="My reviews">
          <div class="pad">
            <ng-container *ngTemplateOutlet="list; context: { $implicit: mine(), empty: 'You are not part of any review cycle yet.' }" />
          </div>
        </mat-tab>
        @if (team().length) {
          <mat-tab>
            <ng-template mat-tab-label>My team @if (teamPending()) { <span class="badge">{{ teamPending() }}</span> }</ng-template>
            <div class="pad"><ng-container *ngTemplateOutlet="list; context: { $implicit: team(), empty: '', showPerson: true }" /></div>
          </mat-tab>
        }
        <mat-tab label="Kudos & feedback">
          <div class="pad feedback">
            <section>
              <h2>Kudos wall</h2>
              @for (f of wall(); track f.id) { <ng-container *ngTemplateOutlet="note; context: { $implicit: f, showFrom: true }" /> }
              @empty { <p class="muted">No kudos yet. Be the first!</p> }
            </section>
            <section>
              <h2>Feedback for you</h2>
              @for (f of received(); track f.id) { <ng-container *ngTemplateOutlet="note; context: { $implicit: f, showFrom: true }" /> }
              @empty { <p class="muted">Nothing yet.</p> }
              @if (teamFeedback().length) {
                <h2 class="mt">About your team (private)</h2>
                @for (f of teamFeedback(); track f.id) { <ng-container *ngTemplateOutlet="note; context: { $implicit: f, showFrom: true, showTo: true }" /> }
              }
            </section>
          </div>
        </mat-tab>
        @if (isAdmin) {
          <mat-tab label="Review cycles">
            <div class="pad">
              @for (c of cycles(); track c.id) {
                <article class="cycle" [class.open]="openCycle() === c.id">
                  <div class="cyc-top">
                    <div>
                      <strong>{{ c.name }}</strong> <hg-status [value]="c.status" />
                      <div class="muted small">{{ c.startDate | date: 'MMM d, y' }} – {{ c.endDate | date: 'MMM d, y' }}
                        @if (c.selfReviewDue) { · self due {{ c.selfReviewDue | date: 'MMM d' }} }
                        @if (c.managerReviewDue) { · manager due {{ c.managerReviewDue | date: 'MMM d' }} }</div>
                    </div>
                    <div class="prog small">{{ c.completedCount }}/{{ c.reviewCount }} complete</div>
                    <div class="acts">
                      @if (c.status === 'DRAFT') { <button mat-flat-button color="primary" (click)="launch(c)">Launch</button> }
                      @if (c.status === 'ACTIVE') { <button mat-stroked-button (click)="close(c)">Close</button> }
                      @if (c.status !== 'DRAFT') {
                        <button mat-button (click)="toggle(c)">{{ openCycle() === c.id ? 'Hide' : 'Details' }}</button>
                      }
                    </div>
                  </div>
                  @if (openCycle() === c.id && summary(); as s) {
                    <div class="details">
                      <div class="dist" aria-label="Manager rating distribution">
                        @for (n of [1, 2, 3, 4, 5]; track n) {
                          <div class="bar-col">
                            <div class="bar" [style.height.%]="barHeight(s, n)" [attr.title]="s.ratingDistribution[n] + ' rated ' + n"></div>
                            <span>{{ n }}★</span><small>{{ s.ratingDistribution[n] }}</small>
                          </div>
                        }
                      </div>
                      <div class="stats">
                        @for (st of statuses; track st) { <div><small>{{ st | humanize }}</small><strong>{{ s.byStatus[st] }}</strong></div> }
                        <div><small>Average score</small><strong>{{ s.averageScore != null ? (s.averageScore | number: '1.2-2') : '—' }}</strong></div>
                      </div>
                      <table class="hg-table">
                        <thead><tr><th>Employee</th><th>Reviewer</th><th>Status</th><th class="num">Rating</th><th class="num">Score</th></tr></thead>
                        <tbody>
                          @for (r of cycleReviews(); track r.id) {
                            <tr>
                              <td><a [routerLink]="['/performance/reviews', r.id]">{{ r.employeeName }}</a> <span class="muted small">{{ r.employeeCode }}</span></td>
                              <td>{{ r.reviewerName }}</td>
                              <td><hg-status [value]="r.status" /></td>
                              <td class="num">{{ r.managerRating ?? '—' }}</td>
                              <td class="num">{{ r.finalScore != null ? (r.finalScore | number: '1.2-2') : '—' }}</td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    </div>
                  }
                </article>
              } @empty {
                <hg-empty-state icon="event_repeat" title="No review cycles" message="Create a cycle to start a review season." />
              }
            </div>
          </mat-tab>
        }
      </mat-tab-group>
    </div>

    <ng-template #list let-items let-empty="empty" let-showPerson="showPerson">
      <div class="reviews">
        @for (r of items; track r.id) {
          <a class="review" [routerLink]="['/performance/reviews', r.id]">
            @if (showPerson) { <span class="avatar">{{ r.employeeName | initials }}</span> }
            <div class="grow">
              <strong>{{ showPerson ? r.employeeName : r.cycleName }}</strong>
              <div class="muted small">{{ showPerson ? r.cycleName + ' · ' + (r.designation ?? '') : 'Reviewer: ' + r.reviewerName }} · {{ r.goalCount }} goal(s)</div>
            </div>
            @if (r.finalScore != null) { <span class="score">{{ r.finalScore | number: '1.1-2' }}</span> }
            <hg-status [value]="r.status" />
          </a>
        } @empty {
          @if (empty) { <hg-empty-state icon="flag" title="No reviews" [message]="empty" /> }
        }
      </div>
    </ng-template>

    <ng-template #note let-f let-showFrom="showFrom" let-showTo="showTo">
      <article class="note" [class.constructive]="f.kind === 'CONSTRUCTIVE'">
        <div class="note-top">
          <span class="avatar sm">{{ f.fromName | initials }}</span>
          <span><strong>{{ f.fromName }}</strong> → <strong>{{ f.toName }}</strong></span>
          <span class="muted small when">{{ f.createdAt | date: 'MMM d' }}{{ f.visibility === 'PRIVATE' ? ' · private' : '' }}</span>
        </div>
        <p>{{ f.kind === 'PRAISE' ? '🎉 ' : '💡 ' }}{{ f.message }}</p>
      </article>
    </ng-template>
  `,
  styles: `
    .pad { padding: 1rem 0.25rem; }
    .small { font-size: 0.8rem; }
    .badge { margin-left: 0.4rem; min-width: 1.25rem; padding: 0 0.35rem; border-radius: 999px; font-size: 0.72rem; line-height: 1.25rem;
      background: var(--hg-primary); color: var(--hg-on-primary); text-align: center; }
    .reviews { display: grid; gap: 0.5rem; }
    .review { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 1rem; border-radius: 12px; color: inherit; text-decoration: none;
      border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); }
    .review:hover { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    .grow { flex: 1; min-width: 0; }
    .score { font-weight: 700; font-size: 1.05rem; font-variant-numeric: tabular-nums; }
    .avatar { display: inline-grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; font-size: 0.8rem; font-weight: 600;
      background: var(--mat-sys-primary-container, #dbeafe); color: var(--mat-sys-on-primary-container, #1e3a8a); flex: none; }
    .avatar.sm { width: 26px; height: 26px; font-size: 0.7rem; }
    .feedback { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; align-items: start; }
    @media (max-width: 860px) { .feedback { grid-template-columns: 1fr; } }
    .feedback h2 { font-size: 1rem; margin: 0 0 0.6rem; } .feedback h2.mt { margin-top: 1.25rem; }
    .note { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.7rem 0.9rem; margin-bottom: 0.5rem; }
    .note.constructive { border-left: 3px solid #f59e0b; }
    .note-top { display: flex; align-items: center; gap: 0.5rem; font-size: 0.86rem; flex-wrap: wrap; }
    .note .when { margin-left: auto; }
    .note p { margin: 0.4rem 0 0; font-size: 0.9rem; }
    .cycle { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.75rem 1rem; margin-bottom: 0.6rem; }
    .cyc-top { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .cyc-top > div:first-child { flex: 1; min-width: 220px; }
    .acts { display: flex; gap: 0.4rem; }
    .details { margin-top: 1rem; display: grid; gap: 1rem; }
    .dist { display: flex; gap: 1rem; align-items: flex-end; height: 140px; padding: 0 0.5rem; }
    .bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; gap: 0.2rem; font-size: 0.8rem; }
    .bar { width: 100%; max-width: 56px; background: var(--mat-sys-primary, #1565c0); border-radius: 6px 6px 0 0; min-height: 2px; }
    .stats { display: flex; flex-wrap: wrap; gap: 1.25rem; }
    .stats small { display: block; font-size: 0.72rem; opacity: 0.7; }
    .hg-table { width: 100%; border-collapse: collapse; }
    .hg-table th, .hg-table td { text-align: left; padding: 0.45rem 0.6rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .hg-table th { font-size: 0.72rem; text-transform: uppercase; opacity: 0.6; }
    .num { text-align: right !important; font-variant-numeric: tabular-nums; }
  `
})
export class PerformanceComponent {
  private performance = inject(PerformanceService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  readonly isAdmin = inject(AuthService).hasPermission('PERFORMANCE_ADMIN');
  readonly statuses = ['NOT_STARTED', 'SELF_SUBMITTED', 'MANAGER_SUBMITTED', 'ACKNOWLEDGED'] as const;

  mine = signal<ReviewSummary[]>([]);
  team = signal<ReviewSummary[]>([]);
  teamPending = signal(0);
  wall = signal<FeedbackNote[]>([]);
  received = signal<FeedbackNote[]>([]);
  teamFeedback = signal<FeedbackNote[]>([]);
  cycles = signal<ReviewCycle[]>([]);
  openCycle = signal<number | null>(null);
  summary = signal<CycleSummary | null>(null);
  cycleReviews = signal<ReviewSummary[]>([]);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.performance.myReviews().subscribe((r) => this.mine.set(r));
    this.performance.teamReviews().subscribe((r) => {
      this.team.set(r);
      this.teamPending.set(r.filter((x) => x.status === 'SELF_SUBMITTED' && x.cycleStatus === 'ACTIVE').length);
    });
    this.loadFeedback();
    if (this.isAdmin) {
      this.performance.cycles().subscribe((c) => this.cycles.set(c));
    }
  }

  loadFeedback(): void {
    this.performance.wall().subscribe((f) => this.wall.set(f));
    this.performance.received().subscribe((f) => this.received.set(f));
    this.performance.aboutTeam().subscribe((f) => this.teamFeedback.set(f));
  }

  giveFeedback(): void {
    this.dialog.open(FeedbackDialogComponent, { width: '560px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => { if (ok) { this.snack.open('Feedback sent', undefined, { duration: 2500 }); this.loadFeedback(); } });
  }

  newCycle(): void {
    this.dialog.open(CycleDialogComponent, { width: '620px', maxWidth: '95vw' }).afterClosed()
      .subscribe((c?: ReviewCycle) => { if (c) { this.snack.open(`${c.name} created as a draft`, undefined, { duration: 2500 }); this.reload(); } });
  }

  launch(c: ReviewCycle): void {
    this.confirm.ask({ title: `Launch ${c.name}?`, message: 'A review opens for every current employee with a manager. Employees can then set goals.',
      confirmText: 'Launch' })
      .subscribe((yes) => yes && this.performance.launch(c.id).subscribe((res) => {
        this.snack.open(`${res.reviewCount} reviews opened`, undefined, { duration: 3000 });
        this.reload();
      }));
  }

  close(c: ReviewCycle): void {
    this.confirm.ask({ title: `Close ${c.name}?`, message: 'Unfinished reviews can no longer be submitted.', confirmText: 'Close cycle', danger: true })
      .subscribe((yes) => yes && this.performance.close(c.id).subscribe(() => this.reload()));
  }

  toggle(c: ReviewCycle): void {
    if (this.openCycle() === c.id) { this.openCycle.set(null); return; }
    this.openCycle.set(c.id);
    this.summary.set(null);
    this.performance.summary(c.id).subscribe((s) => this.summary.set(s));
    this.performance.cycleReviews(c.id).subscribe((r) => this.cycleReviews.set(r));
  }

  barHeight(s: CycleSummary, n: number): number {
    const max = Math.max(1, ...Object.values(s.ratingDistribution));
    return (100 * (s.ratingDistribution[n] ?? 0)) / max;
  }
}
