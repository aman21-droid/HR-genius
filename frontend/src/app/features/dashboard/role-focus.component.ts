import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { RoleDashboard } from '../../core/services/me.service';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

/** HR sees hiring and approvals; managers see their team's priorities. Employees see neither block. */
@Component({
  selector: 'hg-role-focus',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, MatIconModule, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data(); as d) {
      @if (d.hiring; as h) {
        <section class="hg-card focus" aria-labelledby="hiring-h">
          <div class="head"><div><span class="hg-eyebrow">Hiring and approvals</span><h2 id="hiring-h">What HR needs to move today</h2></div><mat-icon>work_outline</mat-icon></div>
          <div class="tiles">
            <a class="tile" routerLink="/approvals"><strong>{{ d.approvalsPending }}</strong><span>Approvals waiting on you</span></a>
            <a class="tile" routerLink="/recruitment"><strong>{{ h.openRequisitions }}</strong><span>Open positions</span>
              @if (h.requisitionsAwaitingApproval) { <small>{{ h.requisitionsAwaitingApproval }} awaiting approval</small> }</a>
            <a class="tile" routerLink="/recruitment"><strong>{{ h.offersAwaitingApproval }}</strong><span>Offers to approve</span>
              @if (h.offersOut) { <small>{{ h.offersOut }} out with candidates</small> }</a>
            <a class="tile" routerLink="/onboarding"><strong>{{ h.onboardingInProgress }}</strong><span>New hires onboarding</span></a>
          </div>
          <div class="cols">
            <div>
              <h3>Pipeline in open positions</h3>
              @for (p of h.pipeline; track p.stage) {
                <a class="bar" routerLink="/recruitment/candidates"><span>{{ p.stage | humanize }}</span><i [style.width.%]="pct(p.count, h.pipeline)"></i><b>{{ p.count }}</b></a>
              } @empty { <p class="muted">No active candidates.</p> }
            </div>
            <div>
              <h3>Interviews this week</h3>
              @for (i of h.interviews; track i.route + i.at) {
                <a class="line" [routerLink]="i.route"><span><strong>{{ i.title }}</strong><small>{{ i.detail }}</small></span><em>{{ i.at | date: 'EEE d MMM, h:mm a' }}</em></a>
              } @empty { <p class="muted">Nothing scheduled in the next 7 days.</p> }
              @if (h.joiners.length) {
                <h3>Joining soon</h3>
                @for (j of h.joiners; track j.name + j.joiningDate) {
                  <div class="line"><span><strong>{{ j.name }}</strong><small>{{ j.role ?? 'New hire' }}</small></span><em>{{ j.joiningDate | date: 'd MMM' }}</em></div>
                }
              }
            </div>
          </div>
        </section>
      }
      @if (d.team; as t) {
        <section class="hg-card focus" aria-labelledby="team-h">
          <div class="head"><div><span class="hg-eyebrow">Your team · {{ t.size }} {{ t.size === 1 ? 'person' : 'people' }}</span><h2 id="team-h">Your team's priorities</h2></div><mat-icon>diversity_3</mat-icon></div>
          <div class="tiles">
            <a class="tile" routerLink="/approvals"><strong>{{ d.approvalsPending }}</strong><span>Requests to decide</span></a>
            <a class="tile" routerLink="/performance"><strong>{{ t.reviewsToComplete }}</strong><span>Reviews to complete</span></a>
            <a class="tile" routerLink="/employees"><strong>{{ t.awayToday.length }}</strong><span>Away today</span></a>
          </div>
          <div class="cols">
            <div>
              <h3>Waiting for your approval</h3>
              @for (p of t.pendingLeave; track p.employeeId + p.from) {
                <a class="line" routerLink="/approvals"><span><strong>{{ p.name }}</strong><small>{{ p.leaveType }} · {{ p.days | number: '1.0-1' }} day{{ p.days === 1 ? '' : 's' }}</small></span><em>{{ p.from | date: 'd MMM' }}{{ p.to !== p.from ? ' – ' + (p.to | date: 'd MMM') : '' }}</em></a>
              } @empty { <p class="muted">No leave requests pending.</p> }
            </div>
            <div>
              <h3>Away this week</h3>
              @for (a of t.awayThisWeek; track a.employeeId + a.from) {
                <a class="line" [routerLink]="['/employees', a.employeeId]"><span><strong><i class="dot" [style.background]="a.color || 'var(--hg-primary)'"></i>{{ a.name }}</strong><small>{{ a.leaveType }}</small></span><em>{{ a.from | date: 'EEE d' }}{{ a.to !== a.from ? ' – ' + (a.to | date: 'EEE d') : '' }}</em></a>
              } @empty { <p class="muted">Everyone is in this week.</p> }
            </div>
          </div>
        </section>
      }
    }
  `,
  styles: `
    :host { display: grid; gap: 16px; margin-bottom: 16px; }
    .focus { padding: 20px 22px; display: grid; gap: 16px; }
    .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
    .head h2 { margin: 2px 0 0; font-size: 1.1rem; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
    .tile { display: grid; gap: 2px; padding: 12px 14px; border: 1px solid var(--hg-border); border-radius: 12px; color: inherit; text-decoration: none; }
    .tile:hover, .tile:focus-visible { border-color: var(--hg-primary); }
    .tile strong { font-size: 1.6rem; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .tile span { font-size: .85rem; } .tile small, small { color: var(--hg-muted); font-size: .76rem; }
    .cols { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
    @media (max-width: 760px) { .cols { grid-template-columns: minmax(0, 1fr); } }
    h3 { font-size: .8rem; text-transform: uppercase; letter-spacing: .06em; color: var(--hg-muted); margin: 0 0 8px; }
    h3:not(:first-child) { margin-top: 16px; }
    .bar { display: grid; grid-template-columns: 7.5em 1fr 2.5em; align-items: center; gap: 10px; padding: 5px 0; color: inherit; text-decoration: none; font-size: .86rem; }
    .bar i { height: 8px; border-radius: 4px; background: var(--hg-primary); min-width: 4px; }
    .bar b { text-align: right; font-variant-numeric: tabular-nums; }
    .line { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--hg-border); color: inherit; text-decoration: none; }
    .line span { display: grid; min-width: 0; } .line strong { font-size: .9rem; } .line em { font-style: normal; color: var(--hg-muted); font-size: .8rem; white-space: nowrap; }
    a.line:hover strong, a.bar:hover span { text-decoration: underline; }
    .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
    .muted { color: var(--hg-muted); font-size: .86rem; margin: 4px 0; }
  `
})
export class RoleFocusComponent {
  data = input<RoleDashboard | null>(null);

  pct(n: number, all: { count: number }[]): number {
    return (100 * n) / Math.max(1, ...all.map((p) => p.count));
  }
}
