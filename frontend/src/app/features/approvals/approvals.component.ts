import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import {
  MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApprovalService } from '../../core/services/approval.service';
import { InboxItem, MyRequestItem } from '../../core/models/approval.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

/** Data handed to the decision dialog; approve flag is pre-set from the clicked button. */
interface DecisionData {
  item: InboxItem;
  approve: boolean;
}

// ============================================================ decision dialog
@Component({
  selector: 'app-decision-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.approve ? 'Approve' : 'Reject' }} request</h2>
    <mat-dialog-content>
      <p class="muted">{{ data.item.title }} — from {{ data.item.requesterName }}</p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>{{ data.approve ? 'Comment (optional)' : 'Reason' }}</mat-label>
        <textarea matInput rows="3" [(ngModel)]="comment" maxlength="500"
                  [placeholder]="data.approve ? 'Add a note for the record' : 'Why are you rejecting this?'"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button [color]="data.approve ? 'primary' : 'warn'" (click)="submit()" [disabled]="saving()">
        {{ data.approve ? 'Approve' : 'Reject' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `.full { width: 100%; } .muted { margin-top: 0; }`
})
export class DecisionDialogComponent {
  data = inject<DecisionData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<DecisionDialogComponent, boolean>);
  private approvals = inject(ApprovalService);

  comment = '';
  saving = signal(false);

  submit(): void {
    this.saving.set(true);
    this.approvals.decide(this.data.item.stepId, { approve: this.data.approve, comment: this.comment.trim() || null })
      .subscribe({
        next: () => this.ref.close(true),
        error: () => this.saving.set(false)
      });
  }
}

// ============================================================ approvals screen
@Component({
  selector: 'app-approvals',
  standalone: true,
  imports: [DatePipe, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatExpansionModule,
    PageHeaderComponent, StatusChipComponent, EmptyStateComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Approvals" subtitle="Requests awaiting your decision and the ones you have raised"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Approvals' }]" />

      <mat-tab-group>
        <mat-tab>
          <ng-template mat-tab-label>
            Inbox
            @if (inbox().length) { <span class="tab-badge">{{ inbox().length }}</span> }
          </ng-template>

          @if (loadingInbox()) {
            <p class="muted pad">Loading…</p>
          } @else if (!inbox().length) {
            <hg-empty-state icon="inbox" title="Nothing to approve"
                            message="When a leave or regularization request needs your sign-off, it shows up here." />
          } @else {
            <div class="cards">
              @for (it of inbox(); track it.stepId) {
                <article class="approval-card">
                  <div class="top">
                    <div>
                      <h3>{{ it.title }}</h3>
                      <p class="meta">
                        <span class="kind">{{ it.subjectType | humanize }}</span>
                        · from
                        @if (it.requesterEmpId) {
                          <a [routerLink]="['/employees', it.requesterEmpId]">{{ it.requesterName }}</a>
                        } @else { {{ it.requesterName }} }
                        · {{ it.createdAt | date: 'MMM d, y' }}
                      </p>
                    </div>
                    <span class="step">Step {{ it.stepNo }} of {{ it.totalSteps }}</span>
                  </div>
                  <div class="acts">
                    <button mat-stroked-button color="warn" (click)="decide(it, false)">
                      <mat-icon>close</mat-icon> Reject
                    </button>
                    <button mat-flat-button color="primary" (click)="decide(it, true)">
                      <mat-icon>check</mat-icon> Approve
                    </button>
                  </div>
                </article>
              }
            </div>
          }
        </mat-tab>

        <mat-tab label="My requests">
          @if (loadingMine()) {
            <p class="muted pad">Loading…</p>
          } @else if (!mine().length) {
            <hg-empty-state icon="outbox" title="No requests yet"
                            message="Leave applications and regularizations you raise are tracked here." />
          } @else {
            <div class="mine">
              @for (r of mine(); track r.requestId) {
                <mat-expansion-panel class="req">
                  <mat-expansion-panel-header>
                    <mat-panel-title>{{ r.title }}</mat-panel-title>
                    <mat-panel-description>
                      <hg-status [value]="r.status" />
                      <span class="muted">{{ r.createdAt | date: 'MMM d, y' }}</span>
                    </mat-panel-description>
                  </mat-expansion-panel-header>
                  <ol class="trail">
                    @for (s of r.steps; track s.stepNo) {
                      <li [class.done]="s.status !== 'PENDING'" [class.active]="s.stepNo === r.currentStep && r.status === 'PENDING'">
                        <span class="dot"><hg-status [value]="s.status" /></span>
                        <span class="who">{{ s.approverName || (s.roleHint | humanize) || 'Approver' }}</span>
                        @if (s.comment) { <span class="cmt">“{{ s.comment }}”</span> }
                        @if (s.decidedAt) { <span class="when">{{ s.decidedAt | date: 'MMM d, HH:mm' }}</span> }
                      </li>
                    }
                  </ol>
                </mat-expansion-panel>
              }
            </div>
          }
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: `
    .pad { padding: 1rem 0.25rem; }
    .cards { display: grid; gap: 0.75rem; padding-top: 1rem; }
    .approval-card { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 1rem 1.25rem; }
    .approval-card .top { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
    .approval-card h3 { margin: 0 0 0.25rem; font-size: 1rem; }
    .approval-card .meta { margin: 0; font-size: 0.85rem; opacity: 0.75; }
    .approval-card .kind { font-weight: 600; }
    .approval-card .step { font-size: 0.8rem; white-space: nowrap; opacity: 0.7; }
    .approval-card .acts { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 0.75rem; }
    .tab-badge { display: inline-block; min-width: 1.25rem; padding: 0 0.35rem; margin-left: 0.4rem;
      border-radius: 999px; background: var(--mat-sys-primary, #1565c0); color: #fff; font-size: 0.72rem; line-height: 1.25rem; text-align: center; }
    .mine { display: grid; gap: 0.5rem; padding-top: 1rem; }
    .mine mat-panel-description { gap: 0.6rem; align-items: center; }
    .trail { list-style: none; margin: 0; padding: 0.25rem 0 0; display: grid; gap: 0.6rem; }
    .trail li { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; font-size: 0.88rem; opacity: 0.6; }
    .trail li.done, .trail li.active { opacity: 1; }
    .trail .who { font-weight: 600; }
    .trail .cmt { font-style: italic; opacity: 0.8; }
    .trail .when { margin-left: auto; font-size: 0.78rem; opacity: 0.7; }
  `
})
export class ApprovalsComponent {
  private approvals = inject(ApprovalService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  inbox = signal<InboxItem[]>([]);
  mine = signal<MyRequestItem[]>([]);
  loadingInbox = signal(true);
  loadingMine = signal(true);

  constructor() {
    this.loadInbox();
    this.loadMine();
  }

  loadInbox(): void {
    this.loadingInbox.set(true);
    this.approvals.inbox().subscribe({
      next: (items) => { this.inbox.set(items); this.loadingInbox.set(false); },
      error: () => this.loadingInbox.set(false)
    });
  }

  loadMine(): void {
    this.loadingMine.set(true);
    this.approvals.mine().subscribe({
      next: (items) => { this.mine.set(items); this.loadingMine.set(false); },
      error: () => this.loadingMine.set(false)
    });
  }

  decide(item: InboxItem, approve: boolean): void {
    this.dialog.open(DecisionDialogComponent, { data: { item, approve }, width: '480px', maxWidth: '95vw' })
      .afterClosed().subscribe((ok) => {
        if (ok) {
          this.snack.open(approve ? 'Request approved' : 'Request rejected', undefined, { duration: 2500 });
          this.loadInbox();
          this.loadMine();
        }
      });
  }
}
