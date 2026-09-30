import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, switchMap } from 'rxjs';
import { RecruitmentService } from '../../core/services/recruitment.service';
import {
  ApplicationCard, ApplicationDetail, ApplicationStage, Candidate, PIPELINE_STAGES, Pipeline
} from '../../core/models/recruitment.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import {
  AddToPipelineDialogComponent, CandidateDialogComponent, ReasonDialogComponent, ReasonDialogData
} from './recruitment-dialogs.component';

interface Column {
  stage: ApplicationStage;
  cards: ApplicationCard[];
}

/**
 * Kanban board for one requisition. Drag a card to move the candidate; dropping on "Rejected"
 * asks for a reason, and "Hired" only accepts candidates through "Convert to employee".
 */
@Component({
  selector: 'app-pipeline',
  standalone: true,
  imports: [DatePipe, DecimalPipe, LowerCasePipe, RouterLink, CdkDropListGroup, CdkDropList, CdkDrag, MatButtonModule, MatIconModule,
    MatTooltipModule, PageHeaderComponent, StatusChipComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page wide">
      @if (data(); as d) {
        <hg-page-header [title]="d.requisition.title"
                        [subtitle]="d.requisition.reqCode + ' · ' + d.requisition.departmentName + ' · ' + d.requisition.locationName + ' · hiring manager ' + d.requisition.hiringManagerName"
                        [breadcrumbs]="[{ label: 'Recruitment', link: '/recruitment' }, { label: d.requisition.reqCode }]">
          <hg-status [value]="d.requisition.status" />
          @if (d.requisition.status === 'OPEN' && d.requisition.publishOnCareers) {
            <a mat-stroked-button [routerLink]="['/careers', d.requisition.reqCode]" target="_blank" rel="noopener">
              <mat-icon>public</mat-icon> Careers page</a>
          }
          @if (d.requisition.status === 'OPEN') {
            <button mat-stroked-button (click)="newCandidate()"><mat-icon>person_add</mat-icon> New candidate</button>
            <button mat-flat-button color="primary" (click)="addExisting()"><mat-icon>group_add</mat-icon> Add from pool</button>
          }
        </hg-page-header>

        @if (d.requisition.status !== 'OPEN') {
          <p class="banner" role="status">
            <mat-icon>info</mat-icon>
            @switch (d.requisition.status) {
              @case ('DRAFT') { This requisition is a draft. Submit it for approval from the Recruitment list to start hiring. }
              @case ('PENDING_APPROVAL') { Waiting for approval. It opens for applications once approved. }
              @case ('ON_HOLD') { On hold: not accepting new applications. You can still move existing candidates. }
              @default { This requisition is {{ d.requisition.status | humanize | lowercase }}. }
            }
          </p>
        }

        <div class="summary">
          <span><strong>{{ d.requisition.filled }}</strong> of {{ d.requisition.openings }} filled</span>
          <span><strong>{{ activeCount() }}</strong> in process</span>
          @if (d.requisition.targetDate) { <span>Target {{ d.requisition.targetDate | date: 'MMM d, y' }}</span> }
        </div>

        <div class="board" cdkDropListGroup>
          @for (col of columns(); track col.stage) {
            <section class="col" [class.terminal]="col.stage === 'HIRED' || col.stage === 'REJECTED'">
              <header>
                <span>{{ col.stage | humanize }}</span>
                <span class="count">{{ col.cards.length }}</span>
              </header>
              <div class="drop" cdkDropList [cdkDropListData]="col" (cdkDropListDropped)="drop($event)"
                   [attr.aria-label]="(col.stage | humanize) + ' column'">
                @for (c of col.cards; track c.id) {
                  <article class="card" cdkDrag [cdkDragData]="c" [cdkDragDisabled]="c.stage === 'HIRED'"
                           (click)="openApplication(c)" (keydown.enter)="openApplication(c)" tabindex="0"
                           [attr.aria-label]="c.candidateName + ', ' + (c.stage | humanize)">
                    <div class="name">{{ c.candidateName }}</div>
                    <div class="sub">{{ c.currentTitle || '—' }}{{ c.currentCompany ? ' · ' + c.currentCompany : '' }}</div>
                    <div class="meta">
                      @if (c.totalExperience != null) { <span>{{ c.totalExperience | number: '1.0-1' }} yrs</span> }
                      @if (c.averageRating != null) {
                        <span matTooltip="Average interview rating"><mat-icon inline>star</mat-icon>{{ c.averageRating | number: '1.1-1' }}</span>
                      }
                      @if (c.feedbackPending) {
                        <span class="owed" matTooltip="Scorecards still owed"><mat-icon inline>rate_review</mat-icon>{{ c.feedbackPending }}</span>
                      }
                      @if (c.hasResume) { <mat-icon inline matTooltip="Resume on file">description</mat-icon> }
                      <span class="src">{{ c.source | humanize }}</span>
                    </div>
                    <div class="age">{{ c.stageChangedAt | date: 'MMM d' }}</div>
                  </article>
                } @empty {
                  <p class="empty">{{ col.stage === 'HIRED' ? 'Hire via an accepted offer' : 'Drop here' }}</p>
                }
              </div>
            </section>
          }
        </div>
      } @else if (loading()) {
        <p class="muted">Loading pipeline…</p>
      }
    </div>
  `,
  styles: `
    .wide { max-width: none; }
    .banner { display: flex; gap: 0.5rem; align-items: center; padding: 0.6rem 0.9rem; border-radius: 10px;
      background: var(--mat-sys-surface-container, rgba(0,0,0,0.04)); margin: 0 0 1rem; font-size: 0.9rem; }
    .summary { display: flex; gap: 1.5rem; flex-wrap: wrap; font-size: 0.88rem; margin-bottom: 0.75rem; opacity: 0.85; }
    .board { display: grid; grid-auto-flow: column; grid-auto-columns: minmax(230px, 1fr); gap: 0.75rem;
      overflow-x: auto; padding-bottom: 0.5rem; }
    .col { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); border-radius: 12px; padding: 0.5rem;
      display: flex; flex-direction: column; min-height: 320px; }
    .col header { display: flex; justify-content: space-between; align-items: center; padding: 0.35rem 0.5rem 0.6rem;
      font-weight: 600; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; }
    .col .count { font-weight: 500; opacity: 0.6; }
    .drop { flex: 1; display: flex; flex-direction: column; gap: 0.5rem; min-height: 60px; }
    .card { background: var(--mat-sys-surface, #fff); border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 10px;
      padding: 0.6rem 0.7rem; cursor: grab; position: relative; }
    .card:focus-visible { outline: 2px solid var(--mat-sys-primary, #1565c0); }
    .card .name { font-weight: 600; font-size: 0.92rem; }
    .card .sub { font-size: 0.78rem; opacity: 0.75; margin: 0.1rem 0 0.4rem; }
    .card .meta { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; font-size: 0.75rem; opacity: 0.85; }
    .card .meta mat-icon { font-size: 14px; height: 14px; width: 14px; vertical-align: -2px; }
    .card .owed { color: var(--mat-sys-tertiary, #b26a00); }
    .card .src { margin-left: auto; opacity: 0.7; }
    .card .age { position: absolute; top: 0.55rem; right: 0.6rem; font-size: 0.7rem; opacity: 0.55; }
    .empty { text-align: center; font-size: 0.8rem; opacity: 0.5; padding: 1rem 0; margin: 0;
      border: 1px dashed var(--hg-border, rgba(0,0,0,0.2)); border-radius: 10px; }
    .cdk-drag-preview { box-shadow: 0 8px 24px rgba(0,0,0,0.2); border-radius: 10px; }
    .cdk-drag-placeholder { opacity: 0.35; }
    .cdk-drop-list-dragging .card:not(.cdk-drag-placeholder) { transition: transform 200ms ease; }
  `
})
export class PipelineComponent {
  /** Route param bound via withComponentInputBinding. */
  id = input.required<string>();

  private recruitment = inject(RecruitmentService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  data = signal<Pipeline | null>(null);
  loading = signal(true);

  private readonly boardStages: ApplicationStage[] = [...PIPELINE_STAGES, 'REJECTED'];

  columns = computed<Column[]>(() => {
    const cards = this.data()?.applications ?? [];
    return this.boardStages.map((stage) => ({
      stage,
      // WITHDRAWN candidates sit with the rejected ones at the end of the board.
      cards: cards.filter((c) => c.stage === stage || (stage === 'REJECTED' && c.stage === 'WITHDRAWN'))
    }));
  });

  activeCount = computed(() =>
    (this.data()?.applications ?? []).filter((c) => !['HIRED', 'REJECTED', 'WITHDRAWN'].includes(c.stage)).length);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.recruitment.pipeline(Number(this.id())).subscribe({
      next: (p) => { this.data.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  drop(ev: CdkDragDrop<Column>): void {
    const card = ev.item.data as ApplicationCard;
    const target = ev.container.data.stage;
    if (ev.previousContainer === ev.container || card.stage === target) {
      return;
    }
    if (target === 'HIRED') {
      this.snack.open('Open the candidate and use "Convert to employee" on an accepted offer.', 'OK', { duration: 4000 });
      return;
    }
    const reason$: Observable<string | null | undefined> = target === 'REJECTED'
      ? this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
        width: '460px', maxWidth: '95vw',
        data: { title: `Reject ${card.candidateName}?`, label: 'Reason (kept on the record)', confirmText: 'Reject',
          required: true, danger: true }
      }).afterClosed()
      : of(null);

    reason$.subscribe((reason) => {
      if (target === 'REJECTED' && !reason) {
        return;   // dialog cancelled
      }
      // Optimistic move; reload from the server either way to pick up derived fields.
      this.patchCard(card.id, target);
      this.recruitment.moveStage(card.id, target, reason ?? null).subscribe({
        next: () => {
          this.snack.open(`${card.candidateName} moved to ${target.toLowerCase()}`, undefined, { duration: 2000 });
          this.reload();
        },
        error: () => this.reload()
      });
    });
  }

  openApplication(c: ApplicationCard): void {
    this.router.navigate(['/recruitment/applications', c.id]);
  }

  addExisting(): void {
    const req = this.data()?.requisition;
    if (!req) { return; }
    this.dialog.open(AddToPipelineDialogComponent, { data: req, width: '520px', maxWidth: '95vw' }).afterClosed()
      .subscribe((app?: ApplicationDetail) => {
        if (app) {
          this.snack.open(`${app.candidate.fullName} added`, undefined, { duration: 2500 });
          this.reload();
        }
      });
  }

  newCandidate(): void {
    const req = this.data()?.requisition;
    if (!req) { return; }
    this.dialog.open(CandidateDialogComponent, { data: null, width: '720px', maxWidth: '95vw' }).afterClosed()
      .pipe(switchMap((c?: Candidate) => (c ? this.recruitment.createApplication(req.id, c.id) : of(null))))
      .subscribe((app) => {
        if (app) {
          this.snack.open(`${app.candidate.fullName} added to ${req.reqCode}`, 'Open', { duration: 4000 })
            .onAction().subscribe(() => this.router.navigate(['/recruitment/applications', app.id]));
          this.reload();
        }
      });
  }

  private patchCard(id: number, stage: ApplicationStage): void {
    const d = this.data();
    if (!d) { return; }
    this.data.set({ ...d, applications: d.applications.map((c) => (c.id === id ? { ...c, stage } : c)) });
  }
}
