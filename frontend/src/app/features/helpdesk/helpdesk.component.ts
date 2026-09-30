import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { AuthService } from '../../core/services/auth.service';
import { HelpdeskService } from '../../core/services/helpdesk.service';
import {
  QueueStats, TICKET_CATEGORIES, TICKET_PRIORITIES, TICKET_STATUSES, Ticket, TicketCategory, TicketStatus, TicketSummary
} from '../../core/models/services.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

// ============================================================ new ticket dialog
@Component({
  selector: 'app-new-ticket-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>How can we help?</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="tForm" (ngSubmit)="save()" class="hg-form-grid">
        <mat-form-field appearance="outline"><mat-label>Category</mat-label>
          <mat-select formControlName="category">
            @for (c of categories; track c) { <mat-option [value]="c">{{ c === 'IT' ? 'IT' : (c | humanize) }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Priority</mat-label>
          <mat-select formControlName="priority">
            @for (p of priorities; track p) { <mat-option [value]="p">{{ p | humanize }} · {{ slaLabel[p] }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Subject</mat-label>
          <input matInput formControlName="subject" required maxlength="200" /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Describe the problem</mat-label>
          <textarea matInput rows="5" formControlName="description" required maxlength="4000"
                    placeholder="What happened, when, and what you have already tried"></textarea></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="tForm" [disabled]="saving()">Submit ticket</button>
    </mat-dialog-actions>
  `
})
export class NewTicketDialogComponent {
  private ref = inject(MatDialogRef<NewTicketDialogComponent, Ticket>);
  private helpdesk = inject(HelpdeskService);
  private fb = inject(FormBuilder);

  readonly categories = TICKET_CATEGORIES;
  readonly priorities = TICKET_PRIORITIES;
  readonly slaLabel: Record<string, string> = { LOW: '5 days', MEDIUM: '3 days', HIGH: '1 day', URGENT: '4 hours' };
  saving = signal(false);
  form = this.fb.group({
    category: ['IT' as TicketCategory],
    priority: ['MEDIUM' as 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'],
    subject: ['', Validators.required],
    description: ['', Validators.required]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.helpdesk.create({ category: v.category!, priority: v.priority!, subject: v.subject!.trim(), description: v.description!.trim() })
      .subscribe({ next: (t) => this.ref.close(t), error: () => this.saving.set(false) });
  }
}

// ============================================================ helpdesk hub
@Component({
  selector: 'app-helpdesk',
  standalone: true,
  imports: [DatePipe, NgTemplateOutlet, FormsModule, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatSlideToggleModule, PageHeaderComponent, StatusChipComponent, EmptyStateComponent,
    HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Helpdesk" subtitle="IT, HR, payroll and facilities support"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Helpdesk' }]">
        <button mat-flat-button color="primary" (click)="newTicket()"><mat-icon>add</mat-icon> New ticket</button>
      </hg-page-header>

      <mat-tab-group animationDuration="0ms">
        <mat-tab label="My tickets">
          <div class="pad list">
            @for (t of mine(); track t.id) { <ng-container *ngTemplateOutlet="row; context: { $implicit: t }" /> }
            @empty { <hg-empty-state icon="support_agent" title="No tickets" message="Raise a ticket whenever you need help." /> }
          </div>
        </mat-tab>
        @if (isAgent) {
          <mat-tab>
            <ng-template mat-tab-label>Queue @if (stats()?.overdue) { <span class="badge">{{ stats()!.overdue }}</span> }</ng-template>
            <div class="pad">
              @if (stats(); as s) {
                <section class="kpis">
                  <div><small>Open</small><strong>{{ s.byStatus.OPEN }}</strong></div>
                  <div><small>In progress</small><strong>{{ s.byStatus.IN_PROGRESS }}</strong></div>
                  <div [class.alert]="s.overdue"><small>Overdue</small><strong>{{ s.overdue }}</strong></div>
                  <div><small>Unassigned</small><strong>{{ s.unassigned }}</strong></div>
                  <div><small>Satisfaction</small><strong>{{ s.averageSatisfaction != null ? s.averageSatisfaction + ' / 5' : '—' }}</strong></div>
                </section>
              }
              <div class="hg-filters">
                <mat-form-field appearance="outline" class="search"><mat-icon matPrefix>search</mat-icon>
                  <input matInput placeholder="Subject or ticket no." [(ngModel)]="search" (keyup.enter)="loadQueue()" /></mat-form-field>
                <mat-form-field appearance="outline"><mat-select placeholder="Status" [(ngModel)]="status" (selectionChange)="loadQueue()">
                  <mat-option [value]="null">Active (open + in progress)</mat-option>
                  @for (s of statuses; track s) { <mat-option [value]="s">{{ s | humanize }}</mat-option> }
                </mat-select></mat-form-field>
                <mat-form-field appearance="outline"><mat-select placeholder="Category" [(ngModel)]="category" (selectionChange)="loadQueue()">
                  <mat-option [value]="null">All categories</mat-option>
                  @for (c of categories; track c) { <mat-option [value]="c">{{ c === 'IT' ? 'IT' : (c | humanize) }}</mat-option> }
                </mat-select></mat-form-field>
                <mat-slide-toggle [(ngModel)]="mineOnly" (change)="loadQueue()">Assigned to me</mat-slide-toggle>
                <mat-slide-toggle [(ngModel)]="overdueOnly" (change)="loadQueue()">Overdue</mat-slide-toggle>
              </div>
              <div class="list">
                @for (t of queue(); track t.id) { <ng-container *ngTemplateOutlet="row; context: { $implicit: t, agent: true }" /> }
                @empty { <p class="muted">Nothing in the queue for these filters. 🎉</p> }
              </div>
            </div>
          </mat-tab>
        }
      </mat-tab-group>
    </div>

    <ng-template #row let-t let-agent="agent">
      <a class="ticket" [routerLink]="['/helpdesk/tickets', t.id]" [class.overdue]="t.overdue">
        <span class="prio" [class]="'prio prio-' + t.priority" [attr.aria-label]="t.priority + ' priority'"></span>
        <div class="grow">
          <div><span class="no">{{ t.ticketNo }}</span> <strong>{{ t.subject }}</strong></div>
          <div class="muted small">{{ t.category === 'IT' ? 'IT' : (t.category | humanize) }}
            @if (agent) { · {{ t.requesterName }} }
            · {{ t.assigneeName ? 'with ' + t.assigneeName : 'unassigned' }}
            · {{ t.comments }} repl{{ t.comments === 1 ? 'y' : 'ies' }}</div>
        </div>
        <div class="due small" [class.late]="t.overdue">
          @if (t.status === 'OPEN' || t.status === 'IN_PROGRESS') { {{ t.overdue ? 'Overdue ' : 'Due ' }}{{ t.dueAt | date: 'MMM d, HH:mm' }} }
          @else { Updated {{ (t.updatedAt || t.createdAt) | date: 'MMM d' }} }
        </div>
        <hg-status [value]="t.status" />
      </a>
    </ng-template>
  `,
  styles: `
    .pad { padding: 1rem 0.25rem; }
    .small { font-size: 0.8rem; }
    .badge { margin-left: 0.4rem; min-width: 1.25rem; padding: 0 0.35rem; border-radius: 999px; font-size: 0.72rem; line-height: 1.25rem;
      background: var(--mat-sys-error, #c62828); color: #fff; text-align: center; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 0.75rem; margin-bottom: 1rem; }
    .kpis > div { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.6rem 0.9rem; }
    .kpis > div.alert { border-color: #fca5a5; background: #fef2f2; color: #991b1b; }
    .kpis small { display: block; font-size: 0.72rem; opacity: 0.7; } .kpis strong { font-size: 1.2rem; }
    .hg-filters { align-items: center; }
    .list { display: grid; gap: 0.5rem; }
    .ticket { display: flex; align-items: center; gap: 0.75rem; padding: 0.7rem 1rem; border-radius: 12px; color: inherit; text-decoration: none;
      border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); }
    .ticket:hover { background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    .ticket.overdue { border-color: #fca5a5; }
    .grow { flex: 1; min-width: 0; }
    .no { font-family: ui-monospace, monospace; font-size: 0.8rem; opacity: 0.65; margin-right: 0.25rem; }
    .prio { width: 6px; align-self: stretch; border-radius: 3px; flex: none; }
    .prio-LOW { background: #9ca3af; } .prio-MEDIUM { background: #3b82f6; } .prio-HIGH { background: #f59e0b; } .prio-URGENT { background: #dc2626; }
    .due { white-space: nowrap; opacity: 0.8; } .late { color: var(--mat-sys-error, #c62828); font-weight: 600; opacity: 1; }
    @media (max-width: 640px) { .due { display: none; } }
  `
})
export class HelpdeskComponent {
  private helpdesk = inject(HelpdeskService);
  private dialog = inject(MatDialog);
  private router = inject(Router);

  readonly isAgent = inject(AuthService).hasPermission('HELPDESK_AGENT');
  readonly categories = TICKET_CATEGORIES;
  readonly statuses = TICKET_STATUSES;

  mine = signal<TicketSummary[]>([]);
  queue = signal<TicketSummary[]>([]);
  stats = signal<QueueStats | null>(null);
  search = '';
  status: TicketStatus | null = null;
  category: TicketCategory | null = null;
  mineOnly = false;
  overdueOnly = false;

  constructor() {
    this.helpdesk.mine().subscribe((t) => this.mine.set(t));
    if (this.isAgent) {
      this.helpdesk.stats().subscribe((s) => this.stats.set(s));
      this.loadQueue();
    }
  }

  loadQueue(): void {
    // "Active" (no status filter) hides resolved/closed tickets client-side to keep the queue focused.
    this.helpdesk.queue({ search: this.search, status: this.status, category: this.category, mine: this.mineOnly || null,
      overdue: this.overdueOnly || null }, { size: 100 }).subscribe((page) =>
      this.queue.set(this.status ? page.content : page.content.filter((t) => t.status === 'OPEN' || t.status === 'IN_PROGRESS')));
  }

  newTicket(): void {
    this.dialog.open(NewTicketDialogComponent, { width: '620px', maxWidth: '95vw' }).afterClosed()
      .subscribe((t?: Ticket) => t && this.router.navigate(['/helpdesk/tickets', t.id]));
  }
}
