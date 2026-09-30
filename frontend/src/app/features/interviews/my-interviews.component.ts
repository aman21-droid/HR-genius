import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { MyInterview, RECOMMENDATIONS, Recommendation } from '../../core/models/recruitment.models';
import { saveBlob } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

// ============================================================ scorecard dialog
@Component({
  selector: 'app-scorecard-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatButtonToggleModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Scorecard · {{ i.candidateName }}</h2>
    <mat-dialog-content>
      <p class="muted">{{ i.roundName }} for {{ i.requisitionTitle }} ({{ i.reqCode }})</p>
      <form [formGroup]="form" id="scoreForm" (ngSubmit)="save()" class="score">
        <label id="ratingLbl">Overall rating</label>
        <div class="stars" role="radiogroup" aria-labelledby="ratingLbl">
          @for (n of five; track n) {
            <button type="button" mat-icon-button role="radio" [attr.aria-checked]="form.controls.rating.value === n"
                    [attr.aria-label]="n + ' out of 5'" (click)="form.controls.rating.setValue(n)">
              <mat-icon [class.on]="n <= (form.controls.rating.value ?? 0)">star</mat-icon>
            </button>
          }
        </div>
        <label id="recLbl">Recommendation</label>
        <mat-button-toggle-group formControlName="recommendation" aria-labelledby="recLbl" class="rec">
          @for (r of recommendations; track r) {
            <mat-button-toggle [value]="r" [class]="'rec-' + r">{{ r | humanize }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
        @if (form.touched && form.invalid) { <p class="err">Pick a rating and a recommendation</p> }
        <mat-form-field appearance="outline" class="full"><mat-label>Strengths</mat-label>
          <textarea matInput rows="3" formControlName="strengths" maxlength="2000"></textarea></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Concerns</mat-label>
          <textarea matInput rows="3" formControlName="concerns" maxlength="2000"></textarea></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="scoreForm" [disabled]="saving()">
        {{ i.submitted ? 'Update scorecard' : 'Submit scorecard' }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .score { display: grid; gap: 0.4rem; }
    .score label { font-size: 0.8rem; font-weight: 600; opacity: 0.75; margin-top: 0.4rem; }
    .stars mat-icon { opacity: 0.25; } .stars mat-icon.on { opacity: 1; color: #f59e0b; }
    .rec { flex-wrap: wrap; }
    .full { width: 100%; }
    .err { color: var(--mat-sys-error, #c62828); font-size: 0.8rem; margin: 0; }
  `
})
export class ScorecardDialogComponent {
  i = inject<MyInterview>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ScorecardDialogComponent, boolean>);
  private recruitment = inject(RecruitmentService);
  private fb = inject(FormBuilder);

  readonly five = [1, 2, 3, 4, 5];
  readonly recommendations = RECOMMENDATIONS;
  saving = signal(false);

  form = this.fb.group({
    rating: [this.i.rating, Validators.required],
    recommendation: [this.i.recommendation as Recommendation | null, Validators.required],
    strengths: [this.i.strengths ?? ''],
    concerns: [this.i.concerns ?? '']
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.recruitment.submitFeedback(this.i.interviewId, {
      rating: v.rating!, recommendation: v.recommendation!, strengths: v.strengths || null, concerns: v.concerns || null
    }).subscribe({ next: () => this.ref.close(true), error: () => this.saving.set(false) });
  }
}

// ============================================================ my interviews
@Component({
  selector: 'app-my-interviews',
  standalone: true,
  imports: [DatePipe, DecimalPipe, NgTemplateOutlet, MatButtonModule, MatIconModule, PageHeaderComponent,
    StatusChipComponent, EmptyStateComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="My interviews" subtitle="Rounds you are on the panel for, and your scorecards"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'My interviews' }]" />

      @if (loading()) {
        <p class="muted">Loading…</p>
      } @else if (!items().length) {
        <hg-empty-state icon="record_voice_over" title="No interviews yet"
                        message="When a recruiter adds you to an interview panel, it shows up here." />
      } @else {
        @if (owed().length) {
          <h2 class="section">Scorecards owed <span class="count">{{ owed().length }}</span></h2>
          <div class="list">
            @for (i of owed(); track i.feedbackId) { <ng-container *ngTemplateOutlet="row; context: { $implicit: i }" /> }
          </div>
        }
        @if (upcoming().length) {
          <h2 class="section">Upcoming</h2>
          <div class="list">
            @for (i of upcoming(); track i.feedbackId) { <ng-container *ngTemplateOutlet="row; context: { $implicit: i }" /> }
          </div>
        }
        @if (done().length) {
          <h2 class="section">Done</h2>
          <div class="list">
            @for (i of done(); track i.feedbackId) { <ng-container *ngTemplateOutlet="row; context: { $implicit: i }" /> }
          </div>
        }
      }
    </div>

    <ng-template #row let-i>
      <article class="item">
        <div class="when">
          <strong>{{ i.scheduledAt | date: 'MMM d' }}</strong>
          <span>{{ i.scheduledAt | date: 'HH:mm' }}</span>
        </div>
        <div class="main">
          <div class="l1"><strong>{{ i.candidateName }}</strong> <span class="muted">· {{ i.roundName }}</span></div>
          <div class="l2 muted">{{ i.requisitionTitle }} ({{ i.reqCode }})
            · {{ i.currentTitle || '—' }}{{ i.currentCompany ? ' at ' + i.currentCompany : '' }}
            @if (i.totalExperience != null) { · {{ i.totalExperience | number: '1.0-1' }} yrs }</div>
          <div class="l2 muted">{{ i.mode | humanize }} · {{ i.durationMinutes }} min
            @if (i.locationOrLink) {
              · @if (isLink(i.locationOrLink)) { <a [href]="i.locationOrLink" target="_blank" rel="noopener noreferrer">Join link</a> }
                @else { {{ i.locationOrLink }} }
            }</div>
          @if (i.submitted) {
            <div class="mine">You rated {{ i.rating }}/5 · <hg-status [value]="i.recommendation" /></div>
          }
        </div>
        <div class="acts">
          <hg-status [value]="i.interviewStatus" />
          @if (i.hasResume) {
            <button mat-button (click)="resume(i)"><mat-icon>description</mat-icon> Resume</button>
          }
          @if (i.interviewStatus !== 'CANCELLED' && i.interviewStatus !== 'NO_SHOW') {
            @if (i.submitted) {
              <button mat-stroked-button (click)="score(i)"><mat-icon>edit_note</mat-icon> Edit scorecard</button>
            } @else {
              <button mat-flat-button color="primary" (click)="score(i)"><mat-icon>rate_review</mat-icon> Give feedback</button>
            }
          }
        </div>
      </article>
    </ng-template>
  `,
  styles: `
    .section { font-size: 1rem; margin: 1.5rem 0 0.6rem; display: flex; align-items: center; gap: 0.5rem; }
    .section .count { font-size: 0.75rem; background: var(--mat-sys-error, #c62828); color: #fff; border-radius: 999px; padding: 0 0.5rem; }
    .list { display: grid; gap: 0.5rem; }
    .item { display: flex; gap: 1rem; align-items: center; border: 1px solid var(--hg-border, rgba(0,0,0,0.12));
      border-radius: 12px; padding: 0.75rem 1rem; flex-wrap: wrap; }
    .when { display: flex; flex-direction: column; align-items: center; min-width: 56px; font-variant-numeric: tabular-nums; }
    .when span { font-size: 0.8rem; opacity: 0.7; }
    .main { flex: 1; min-width: 220px; }
    .l2 { font-size: 0.82rem; margin-top: 0.15rem; }
    .mine { font-size: 0.82rem; margin-top: 0.35rem; display: flex; gap: 0.4rem; align-items: center; }
    .acts { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
  `
})
export class MyInterviewsComponent {
  private recruitment = inject(RecruitmentService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  items = signal<MyInterview[]>([]);
  loading = signal(true);

  private now = () => Date.now();
  owed = computed(() => this.items()
    .filter((i) => !i.submitted && i.interviewStatus !== 'CANCELLED' && i.interviewStatus !== 'NO_SHOW'
      && new Date(i.scheduledAt).getTime() <= this.now())
    .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)));
  upcoming = computed(() => this.items()
    .filter((i) => !i.submitted && i.interviewStatus === 'SCHEDULED' && new Date(i.scheduledAt).getTime() > this.now())
    .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)));
  done = computed(() => this.items()
    .filter((i) => i.submitted || i.interviewStatus === 'CANCELLED' || i.interviewStatus === 'NO_SHOW'));

  constructor() {
    this.reload();
  }

  reload(): void {
    this.recruitment.myInterviews().subscribe({
      next: (list) => { this.items.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  isLink(v: string): boolean {
    return /^https?:\/\//i.test(v);
  }

  resume(i: MyInterview): void {
    this.recruitment.downloadResume(i.candidateId).subscribe((res) => saveBlob(res, 'resume'));
  }

  score(i: MyInterview): void {
    this.dialog.open(ScorecardDialogComponent, { data: i, width: '560px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => {
        if (ok) { this.snack.open('Scorecard saved. Thanks!', undefined, { duration: 2500 }); this.reload(); }
      });
  }
}
