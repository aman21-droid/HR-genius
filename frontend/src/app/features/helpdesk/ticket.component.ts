import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { HelpdeskService } from '../../core/services/helpdesk.service';
import { TICKET_PRIORITIES, Ticket, TicketPriority } from '../../core/models/services.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { HumanizePipe, InitialsPipe } from '../../shared/pipes/labels.pipe';

/** A ticket's conversation, with triage controls for agents and close/reopen/rate for the requester. */
@Component({
  selector: 'app-ticket',
  standalone: true,
  imports: [DatePipe, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, PageHeaderComponent, StatusChipComponent, HumanizePipe, InitialsPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      @if (t(); as t) {
        <hg-page-header [title]="t.subject" [subtitle]="t.ticketNo + ' · ' + (t.category === 'IT' ? 'IT' : (t.category | humanize)) + ' · raised by ' + t.requesterName + (t.requesterDepartment ? ' (' + t.requesterDepartment + ')' : '')"
                        [breadcrumbs]="[{ label: 'Helpdesk', link: '/helpdesk' }, { label: t.ticketNo }]">
          <hg-status [value]="t.status" />
        </hg-page-header>

        <div class="layout">
          <div class="thread">
            <article class="msg requester">
              <header><span class="avatar">{{ t.requesterName | initials }}</span><strong>{{ t.requesterName }}</strong>
                <span class="muted small">{{ t.createdAt | date: 'MMM d, HH:mm' }}</span></header>
              <p>{{ t.description }}</p>
            </article>
            @for (c of t.comments; track c.id) {
              <article class="msg" [class.requester]="c.byRequester" [class.internal]="c.internalNote">
                <header><span class="avatar">{{ c.authorName | initials }}</span><strong>{{ c.authorName }}</strong>
                  @if (c.internalNote) { <span class="tag">Internal note</span> }
                  <span class="muted small">{{ c.createdAt | date: 'MMM d, HH:mm' }}</span></header>
                <p>{{ c.body }}</p>
              </article>
            }
            @if (t.resolutionNote && (t.status === 'RESOLVED' || t.status === 'CLOSED')) {
              <article class="msg resolution"><header><mat-icon>task_alt</mat-icon><strong>Resolution</strong>
                <span class="muted small">{{ t.resolvedAt | date: 'MMM d, HH:mm' }}</span></header><p>{{ t.resolutionNote }}</p></article>
            }

            @if (t.status === 'RESOLVED' && t.viewerIsRequester) {
              <section class="confirm">
                <p><strong>Did this fix your issue?</strong> Rate the support and close the ticket, or reopen it.</p>
                <div class="rate" role="radiogroup" aria-label="Satisfaction">
                  @for (n of [1, 2, 3, 4, 5]; track n) {
                    <button type="button" role="radio" [attr.aria-checked]="csat === n" [class.on]="csat >= n" (click)="csat = n"
                            [attr.aria-label]="n + ' of 5'">★</button>
                  }
                </div>
                <div class="acts">
                  <button mat-stroked-button (click)="reopen()">Still broken — reopen</button>
                  <button mat-flat-button color="primary" (click)="close()">Close ticket</button>
                </div>
              </section>
            }

            @if (t.status !== 'CLOSED') {
              <section class="reply">
                <mat-form-field appearance="outline" class="full"><mat-label>{{ internal ? 'Internal note (agents only)' : 'Reply' }}</mat-label>
                  <textarea matInput rows="3" [(ngModel)]="body" maxlength="4000"></textarea></mat-form-field>
                <div class="acts">
                  @if (t.viewerIsAgent) { <mat-checkbox [(ngModel)]="internal">Internal note</mat-checkbox> }
                  <span class="grow"></span>
                  @if (t.viewerIsRequester && t.status !== 'RESOLVED') { <button mat-button (click)="close()">Close ticket</button> }
                  <button mat-flat-button color="primary" (click)="send()" [disabled]="!body.trim() || busy()">Send</button>
                </div>
              </section>
            } @else {
              <p class="muted">Closed {{ t.closedAt | date: 'MMM d, y' }}@if (t.satisfaction) { · rated {{ t.satisfaction }}/5 }</p>
            }
          </div>

          <aside class="side">
            <dl>
              <dt>Priority</dt><dd>{{ t.priority | humanize }}</dd>
              <dt>{{ t.overdue ? 'Overdue since' : 'Due by' }}</dt><dd [class.late]="t.overdue">{{ t.dueAt | date: 'MMM d, HH:mm' }}</dd>
              <dt>Assignee</dt><dd>{{ t.assigneeName || 'Unassigned' }}</dd>
              <dt>First response</dt><dd>{{ t.firstResponseAt ? (t.firstResponseAt | date: 'MMM d, HH:mm') : '—' }}</dd>
            </dl>
            @if (t.viewerIsAgent && t.status !== 'CLOSED') {
              <h3>Triage</h3>
              <div class="triage">
                @if (t.assigneeId !== me) { <button mat-stroked-button (click)="assignToMe()">Assign to me</button> }
                <mat-form-field appearance="outline"><mat-label>Priority</mat-label>
                  <mat-select [ngModel]="t.priority" (ngModelChange)="setPriority($event)">
                    @for (p of priorities; track p) { <mat-option [value]="p">{{ p | humanize }}</mat-option> }
                  </mat-select></mat-form-field>
                @if (t.status !== 'RESOLVED') {
                  <mat-form-field appearance="outline"><mat-label>Resolution note</mat-label>
                    <textarea matInput rows="3" [(ngModel)]="resolution" maxlength="2000"></textarea></mat-form-field>
                  <button mat-flat-button color="primary" (click)="resolve()" [disabled]="!resolution.trim()">Resolve</button>
                }
              </div>
            }
          </aside>
        </div>
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 1rem; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .thread { display: grid; gap: 0.6rem; }
    .msg { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.7rem 0.9rem; margin-right: 3rem; }
    .msg.requester { margin: 0 0 0 3rem; background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.02)); }
    .msg.internal { border-style: dashed; background: #fffbeb; color: #78350f; }
    .msg.resolution { border-color: #86efac; background: #f0fdf4; color: #14532d; margin: 0; }
    .msg header { display: flex; align-items: center; gap: 0.5rem; font-size: 0.88rem; }
    .msg header .small { margin-left: auto; }
    .msg p { margin: 0.4rem 0 0; white-space: pre-line; }
    .tag { font-size: 0.7rem; padding: 0 0.4rem; border-radius: 999px; background: #fde68a; }
    .avatar { display: inline-grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; font-size: 0.7rem; font-weight: 600;
      background: var(--mat-sys-primary-container, #dbeafe); }
    .small { font-size: 0.78rem; }
    .reply, .confirm { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.75rem 0.9rem; }
    .full { width: 100%; }
    .acts { display: flex; align-items: center; gap: 0.5rem; justify-content: flex-end; }
    .grow { flex: 1; }
    .rate button { border: none; background: none; cursor: pointer; font-size: 1.5rem; color: #d1d5db; }
    .rate button.on { color: #f59e0b; }
    .side { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.9rem 1rem; }
    .side dl { display: grid; grid-template-columns: auto 1fr; gap: 0.35rem 0.75rem; margin: 0; font-size: 0.86rem; }
    .side dt { opacity: 0.65; } .side dd { margin: 0; }
    .side h3 { font-size: 0.85rem; margin: 1rem 0 0.5rem; }
    .triage { display: grid; gap: 0.25rem; }
    .late { color: var(--mat-sys-error, #c62828); font-weight: 600; }
  `
})
export class TicketComponent {
  id = input.required<string>();

  private helpdesk = inject(HelpdeskService);
  private snack = inject(MatSnackBar);
  readonly me = inject(AuthService).user()?.employeeId ?? null;
  readonly priorities = TICKET_PRIORITIES;

  t = signal<Ticket | null>(null);
  busy = signal(false);
  body = '';
  internal = false;
  resolution = '';
  csat = 0;

  ngOnInit(): void {
    this.helpdesk.get(Number(this.id())).subscribe((t) => this.t.set(t));
  }

  send(): void {
    this.run(this.helpdesk.comment(this.t()!.id, this.body.trim(), this.internal), () => { this.body = ''; this.internal = false; });
  }

  assignToMe(): void {
    if (this.me != null) { this.run(this.helpdesk.update(this.t()!.id, { assigneeId: this.me }), undefined, 'Assigned to you'); }
  }

  setPriority(p: TicketPriority): void {
    this.run(this.helpdesk.update(this.t()!.id, { priority: p }), undefined, 'Priority updated; the deadline moved');
  }

  resolve(): void {
    this.run(this.helpdesk.update(this.t()!.id, { status: 'RESOLVED', resolutionNote: this.resolution.trim() }),
      () => (this.resolution = ''), 'Resolved. The requester can confirm or reopen.');
  }

  close(): void {
    this.run(this.helpdesk.close(this.t()!.id, this.csat || null), undefined, 'Ticket closed. Thanks!');
  }

  reopen(): void {
    this.run(this.helpdesk.reopen(this.t()!.id), undefined, 'Ticket reopened');
  }

  private run(call: Observable<Ticket>, after?: () => void, message?: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (t) => {
        this.t.set(t);
        this.busy.set(false);
        after?.();
        if (message) { this.snack.open(message, undefined, { duration: 2500 }); }
      },
      error: () => this.busy.set(false)
    });
  }
}
