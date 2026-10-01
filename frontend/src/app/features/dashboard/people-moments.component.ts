import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { PerformanceService } from '../../core/services/performance.service';
import { FeedbackNote } from '../../core/models/performance.models';
import { InitialsPipe } from '../../shared/pipes/labels.pipe';

/** Public appreciation only; posting reuses the existing feedback form and API. */
@Component({
  selector: 'hg-people-moments',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, InitialsPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="moments" aria-label="Team appreciation">
      <div class="intro">
        <span class="hg-eyebrow">THE SMALL THINGS ARE THE BIG THINGS</span>
        <h2>Good work deserves<br>a little <em>spotlight.</em></h2>
        <p>A helping hand. A thoughtful idea. A teammate who made your day. Let them know.</p>
        <button mat-flat-button (click)="giveShoutout()" [disabled]="!canGive || opening()"><mat-icon>volunteer_activism</mat-icon> Give a shout-out</button>
        @if (!canGive) { <small>Connect an employee profile to send appreciation.</small> }
      </div>
      <div class="spotlight">
        <div class="spotlight-heading"><span><mat-icon>auto_awesome</mat-icon> THE KUDOS CORNER</span>
          @if (notes().length > 1) {
            <div class="pager"><button mat-icon-button aria-label="Previous appreciation" (click)="move(-1)"><mat-icon>chevron_left</mat-icon></button><span>{{ index() + 1 }} / {{ notes().length }}</span><button mat-icon-button aria-label="Next appreciation" (click)="move(1)"><mat-icon>chevron_right</mat-icon></button></div>
          }
        </div>
        <div aria-live="polite" aria-atomic="true">
          @if (current(); as note) {
            <div class="recipient"><span class="hg-avatar">{{ note.toName | initials }}</span><div><strong>{{ note.toName }}</strong><small>A little appreciation, a lasting impact.</small></div></div>
            <blockquote>{{ note.message }}</blockquote>
            <div class="signature"><span>From <strong>{{ note.fromName }}</strong></span><time>{{ note.createdAt | date: 'MMM d' }}</time></div>
          } @else if (loading()) { <p class="empty">Gathering the good moments…</p> }
          @else if (failed()) { <p class="empty">Appreciation couldn't load just now.</p><button mat-stroked-button (click)="load()">Try again</button> }
          @else { <div class="empty"><mat-icon>celebration</mat-icon><h3>Be someone's bright spot.</h3><p>Your team's public shout-outs will appear here.<br>A simple thank-you is a lovely place to start.</p></div> }
        </div>
      </div>
    </section>
  `,
  styles: `
    :host { display: block; margin-top: 24px; }
    .moments { display: grid; grid-template-columns: 1fr 1.2fr; border: 1px solid var(--hg-border); border-radius: 20px; overflow: hidden; background: var(--hg-surface); }
    .intro { padding: 32px; background: var(--hg-tint); }
    h2 { font-size: 27px; font-weight: 500; letter-spacing: -.9px; line-height: 1.3; margin: 12px 0; }
    h2 em { font-family: Georgia, serif; color: var(--hg-primary); }
    .intro p { max-width: 310px; color: var(--hg-muted); font-size: 12px; line-height: 1.8; margin-bottom: 22px; }
    .intro small { display: block; color: var(--hg-muted); font-size: 11px; margin-top: 10px; }
    .spotlight { padding: 24px 30px; min-width: 0; }
    .spotlight-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 48px; margin-bottom: 18px; }
    .spotlight-heading > span { display: inline-flex; align-items: center; gap: 8px; font-size: 9px; font-weight: 700; letter-spacing: .12em; color: var(--hg-lavender); }
    .pager { display: flex; align-items: center; font-size: 10px; color: var(--hg-muted); }
    .recipient { display: flex; align-items: center; gap: 12px; font-size: 14px; }
    .recipient small { display: block; margin-top: 3px; font-size: 10px; color: var(--hg-muted); }
    blockquote { margin: 22px 0; font-size: 15px; line-height: 1.8; max-height: 180px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
    .signature { display: flex; align-items: center; justify-content: space-between; gap: 10px; border-top: 1px solid var(--hg-border); padding-top: 16px; font-size: 11px; color: var(--hg-muted); }
    .empty { color: var(--hg-muted); font-size: 12px; padding: 12px 0; line-height: 1.8; }
    .empty > mat-icon { color: var(--hg-lavender); font-size: 32px; width: 32px; height: 32px; }
    .empty h3 { color: var(--hg-text); font-size: 18px; font-weight: 500; }
    @media (max-width: 700px) { .moments { grid-template-columns: 1fr; } .intro, .spotlight { padding: 24px; } }
  `
})
export class PeopleMomentsComponent {
  private performance = inject(PerformanceService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  readonly canGive = inject(AuthService).user()?.employeeId != null;
  notes = signal<FeedbackNote[]>([]);
  index = signal(0);
  loading = signal(true);
  failed = signal(false);
  opening = signal(false);
  current = computed(() => this.notes()[this.index()]);

  constructor() { this.load(); }
  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.performance.wall(12).subscribe({
      next: notes => { this.notes.set(notes.filter(n => n.kind === 'PRAISE' && n.visibility === 'PUBLIC')); this.index.set(0); this.loading.set(false); },
      error: () => { this.failed.set(true); this.loading.set(false); }
    });
  }
  move(step: number): void { if (this.notes().length) { this.index.update(i => (i + step + this.notes().length) % this.notes().length); } }
  async giveShoutout(): Promise<void> {
    if (!this.canGive || this.opening()) { return; }
    this.opening.set(true);
    try {
      const { FeedbackDialogComponent } = await import('../performance/performance-dialogs.component');
      this.dialog.open(FeedbackDialogComponent, { width: '560px', maxWidth: '95vw' }).afterClosed().subscribe(ok => {
        if (ok) { this.snack.open('Appreciation sent. You made someone’s day.', undefined, { duration: 3500 }); this.load(); }
      });
    } catch { this.snack.open('The appreciation form could not open. Please try again.', undefined, { duration: 3500 }); }
    finally { this.opening.set(false); }
  }
}
