import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { AuthService } from '../../core/services/auth.service';
import {
  ApplicationDetail, ApplicationStage, HireResponse, Interview, InterviewStatus, Offer
} from '../../core/models/recruitment.models';
import { saveBlob } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { FileSizePipe, HumanizePipe, InrPipe } from '../../shared/pipes/labels.pipe';
import {
  CandidateDialogComponent, HireDialogComponent, OfferDialogComponent, ReasonDialogComponent, ReasonDialogData,
  ScheduleInterviewDialogComponent
} from './recruitment-dialogs.component';

/** Manual stage moves offered from the header menu (HIRED only via the hire flow). */
const MOVABLE: ApplicationStage[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'];

@Component({
  selector: 'app-application-detail',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatMenuModule,
    MatFormFieldModule, MatInputModule, MatTooltipModule, PageHeaderComponent, StatusChipComponent, HumanizePipe,
    FileSizePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      @if (app(); as a) {
        <hg-page-header [title]="a.candidate.fullName"
                        [subtitle]="'Applying for ' + a.requisition.title + ' (' + a.requisition.reqCode + ')'"
                        [breadcrumbs]="[{ label: 'Recruitment', link: '/recruitment' },
                                        { label: a.requisition.reqCode, link: ['/recruitment/requisitions', a.requisition.id] },
                                        { label: a.candidate.fullName }]">
          <hg-status [value]="a.stage" />
          @if (a.stage !== 'HIRED') {
            <button mat-stroked-button [matMenuTriggerFor]="stageMenu"><mat-icon>swap_horiz</mat-icon> Move to</button>
            <mat-menu #stageMenu="matMenu">
              @for (s of movable; track s) {
                @if (s !== a.stage) {
                  <button mat-menu-item (click)="move(s)">{{ s | humanize }}</button>
                }
              }
            </mat-menu>
          }
          @if (a.employeeId) {
            <a mat-flat-button color="primary" [routerLink]="['/employees', a.employeeId]"><mat-icon>badge</mat-icon> Employee profile</a>
          }
        </hg-page-header>

        @if (a.rejectionReason && (a.stage === 'REJECTED' || a.stage === 'WITHDRAWN')) {
          <p class="banner danger" role="status"><mat-icon>block</mat-icon> {{ a.stage | humanize }}: {{ a.rejectionReason }}</p>
        }

        <div class="layout">
          <div class="main">
            <!-- ================= Offer ================= -->
            <section class="card">
              <header>
                <h2>Offer</h2>
                @if (canDraftOffer()) {
                  <button mat-stroked-button (click)="editOffer(null)"><mat-icon>request_quote</mat-icon> Draft offer</button>
                }
              </header>
              @for (o of a.offers; track o.id; let first = $first) {
                <div class="offer" [class.past]="!first">
                  <div class="offer-top">
                    <div>
                      <strong>{{ o.designationName }}</strong>{{ o.gradeName ? ' · ' + o.gradeName : '' }}
                      <div class="muted small">{{ o.departmentName }} · {{ o.locationName }} · {{ o.employmentType | humanize }}</div>
                    </div>
                    <hg-status [value]="o.status" />
                  </div>
                  <dl class="facts">
                    <div><dt>Annual CTC</dt><dd>{{ o.annualCtc | inr }}</dd></div>
                    <div><dt>Joining</dt><dd>{{ o.joiningDate | date: 'MMM d, y' }}</dd></div>
                    <div><dt>Valid until</dt><dd>{{ o.expiryDate ? (o.expiryDate | date: 'MMM d, y') : '—' }}</dd></div>
                    @if (o.sentAt) { <div><dt>Sent</dt><dd>{{ o.sentAt | date: 'MMM d' }}</dd></div> }
                  </dl>
                  @if (o.declineReason) { <p class="small">Declined: {{ o.declineReason }}</p> }
                  @if (first) {
                    <div class="acts">
                      <button mat-button (click)="letter(o)"><mat-icon>picture_as_pdf</mat-icon> Letter</button>
                      @if (o.status === 'DRAFT' || o.status === 'REJECTED') {
                        <button mat-button (click)="editOffer(o)"><mat-icon>edit</mat-icon> Edit</button>
                        <button mat-flat-button color="primary" (click)="submitOffer(o)"><mat-icon>send</mat-icon> Submit for approval</button>
                      }
                      @if (o.status === 'PENDING_APPROVAL') {
                        <span class="muted small waiting"><mat-icon inline>hourglass_top</mat-icon> Awaiting hiring manager &amp; HR approval</span>
                      }
                      @if (o.status === 'APPROVED') {
                        <button mat-flat-button color="primary" (click)="sendOffer(o)"><mat-icon>mark_email_read</mat-icon> Mark as sent</button>
                      }
                      @if (o.status === 'SENT') {
                        <button mat-stroked-button color="warn" (click)="respond(o, false)">Declined</button>
                        <button mat-flat-button color="primary" (click)="respond(o, true)"><mat-icon>handshake</mat-icon> Accepted</button>
                      }
                      @if (o.status === 'ACCEPTED' && !a.employeeId) {
                        @if (canHire) {
                          <button mat-flat-button color="primary" (click)="hire(o)"><mat-icon>person_add_alt_1</mat-icon> Convert to employee</button>
                        } @else {
                          <span class="muted small">Accepted. An HR admin converts the hire into an employee.</span>
                        }
                      }
                      @if (['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT'].includes(o.status)) {
                        <button mat-button color="warn" (click)="withdraw(o)">Withdraw</button>
                      }
                    </div>
                  }
                </div>
              } @empty {
                <p class="muted">No offer yet. Draft one once the panel is happy.</p>
              }
            </section>

            <!-- ================= Interviews ================= -->
            <section class="card">
              <header>
                <h2>Interviews</h2>
                @if (!isTerminal()) {
                  <button mat-stroked-button (click)="schedule()"><mat-icon>event</mat-icon> Schedule</button>
                }
              </header>
              @for (i of a.interviews; track i.id) {
                <div class="round">
                  <div class="round-top">
                    <div>
                      <strong>{{ i.roundName }}</strong>
                      <div class="muted small">{{ i.scheduledAt | date: 'EEE, MMM d · HH:mm' }} · {{ i.durationMinutes }} min ·
                        {{ i.mode | humanize }}@if (i.locationOrLink) { · {{ i.locationOrLink }} }</div>
                    </div>
                    <hg-status [value]="i.status" />
                    @if (i.status === 'SCHEDULED') {
                      <button mat-icon-button [matMenuTriggerFor]="im" [attr.aria-label]="'Actions for ' + i.roundName">
                        <mat-icon>more_vert</mat-icon></button>
                      <mat-menu #im="matMenu">
                        <button mat-menu-item (click)="setInterview(i, 'COMPLETED')">Mark completed</button>
                        <button mat-menu-item (click)="setInterview(i, 'NO_SHOW')">Candidate no-show</button>
                        <button mat-menu-item (click)="setInterview(i, 'CANCELLED')">Cancel interview</button>
                      </mat-menu>
                    }
                  </div>
                  <ul class="panel">
                    @for (f of i.panel; track f.id) {
                      <li>
                        <span class="who">{{ f.interviewerName }}</span>
                        @if (f.submittedAt) {
                          <span class="stars" [attr.aria-label]="f.rating + ' out of 5'">
                            @for (n of five; track n) { <mat-icon inline [class.on]="n <= (f.rating ?? 0)">star</mat-icon> }
                          </span>
                          <hg-status [value]="f.recommendation" />
                          @if (f.strengths) { <p class="fb"><strong>+</strong> {{ f.strengths }}</p> }
                          @if (f.concerns) { <p class="fb"><strong>−</strong> {{ f.concerns }}</p> }
                        } @else {
                          <span class="muted small">Feedback pending</span>
                        }
                      </li>
                    }
                  </ul>
                </div>
              } @empty {
                <p class="muted">No interviews scheduled.</p>
              }
            </section>

            <!-- ================= Activity ================= -->
            <section class="card">
              <header><h2>Activity</h2></header>
              <div class="note">
                <mat-form-field appearance="outline" class="grow">
                  <mat-label>Add a note</mat-label>
                  <input matInput [(ngModel)]="note" maxlength="1000" (keydown.enter)="addNote()" />
                </mat-form-field>
                <button mat-stroked-button (click)="addNote()" [disabled]="!note.trim()">Add</button>
              </div>
              <ol class="trail">
                @for (e of a.events; track e.id) {
                  <li>
                    <mat-icon class="ev">{{ eventIcon(e.eventType) }}</mat-icon>
                    <div>
                      <div>{{ e.message }}</div>
                      <div class="muted small">{{ e.actorName }} · {{ e.createdAt | date: 'MMM d, y, HH:mm' }}</div>
                    </div>
                  </li>
                }
              </ol>
            </section>
          </div>

          <!-- ================= Candidate side panel ================= -->
          <aside class="card side">
            <header>
              <h2>Candidate</h2>
              <button mat-icon-button (click)="editCandidate()" aria-label="Edit candidate"><mat-icon>edit</mat-icon></button>
            </header>
            <dl class="kv">
              <dt>Email</dt><dd><a [href]="'mailto:' + a.candidate.email">{{ a.candidate.email }}</a></dd>
              @if (a.candidate.phone) { <dt>Phone</dt><dd>{{ a.candidate.phone }}</dd> }
              <dt>Current</dt><dd>{{ a.candidate.currentTitle || '—' }}{{ a.candidate.currentCompany ? ' at ' + a.candidate.currentCompany : '' }}</dd>
              <dt>Experience</dt><dd>{{ a.candidate.totalExperience != null ? (a.candidate.totalExperience | number: '1.0-1') + ' yrs' : '—' }}</dd>
              <dt>Current CTC</dt><dd>{{ a.candidate.currentCtc | inr }}</dd>
              <dt>Expected CTC</dt><dd>{{ a.candidate.expectedCtc | inr }}</dd>
              <dt>Notice</dt><dd>{{ a.candidate.noticePeriodDays != null ? a.candidate.noticePeriodDays + ' days' : '—' }}</dd>
              @if (a.candidate.city) { <dt>City</dt><dd>{{ a.candidate.city }}</dd> }
              <dt>Source</dt><dd>{{ a.source | humanize }}{{ a.candidate.referredByName ? ' · ' + a.candidate.referredByName : '' }}</dd>
              @if (a.candidate.linkedinUrl) {
                <dt>LinkedIn</dt><dd><a [href]="a.candidate.linkedinUrl" target="_blank" rel="noopener noreferrer">Profile</a></dd>
              }
            </dl>
            <div class="resume">
              @if (a.candidate.hasResume) {
                <button mat-stroked-button (click)="resume()"><mat-icon>description</mat-icon>
                  {{ a.candidate.resumeFileName }} @if (a.candidate.resumeSize) { ({{ a.candidate.resumeSize | fileSize }}) }</button>
              } @else { <span class="muted small">No resume on file</span> }
              <input #rf type="file" hidden accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                     (change)="upload(rf)" />
              <button mat-button (click)="rf.click()"><mat-icon>upload</mat-icon> {{ a.candidate.hasResume ? 'Replace' : 'Upload' }}</button>
            </div>
            @if (a.coverNote) { <h3>Cover note</h3><p class="small">{{ a.coverNote }}</p> }
            @if (a.candidate.notes) { <h3>Recruiter notes</h3><p class="small">{{ a.candidate.notes }}</p> }
            @if (otherApplications().length) {
              <h3>Other applications</h3>
              <ul class="others">
                @for (o of otherApplications(); track o.applicationId) {
                  <li><a [routerLink]="['/recruitment/applications', o.applicationId]">{{ o.requisitionTitle }}</a>
                    <hg-status [value]="o.stage" /></li>
                }
              </ul>
            }
          </aside>
        </div>
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .banner { display: flex; gap: 0.5rem; align-items: center; padding: 0.6rem 0.9rem; border-radius: 10px; margin: 0 0 1rem; }
    .banner.danger { background: #fee2e2; color: #991b1b; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 1rem; align-items: start; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
    .main { display: grid; gap: 1rem; }
    .card { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 14px; padding: 1rem 1.15rem; }
    .card > header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
    .card h2 { font-size: 1rem; margin: 0; }
    .card h3 { font-size: 0.85rem; margin: 1rem 0 0.25rem; opacity: 0.8; }
    .small { font-size: 0.82rem; }
    .offer { padding: 0.75rem 0; border-top: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .offer:first-of-type { border-top: none; }
    .offer.past { opacity: 0.6; }
    .offer-top, .round-top { display: flex; justify-content: space-between; gap: 0.75rem; align-items: flex-start; }
    .round-top > div { flex: 1; }
    .facts { display: flex; flex-wrap: wrap; gap: 0.5rem 1.75rem; margin: 0.6rem 0; }
    .facts dt { font-size: 0.72rem; opacity: 0.65; margin: 0; } .facts dd { margin: 0; font-weight: 600; }
    .acts { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; justify-content: flex-end; }
    .waiting { display: inline-flex; align-items: center; gap: 0.25rem; }
    .round { padding: 0.75rem 0; border-top: 1px solid var(--hg-border, rgba(0,0,0,0.08)); }
    .round:first-of-type { border-top: none; }
    .panel { list-style: none; margin: 0.5rem 0 0; padding: 0; display: grid; gap: 0.5rem; }
    .panel li { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
    .panel .who { font-weight: 600; font-size: 0.88rem; min-width: 130px; }
    .stars mat-icon { font-size: 16px; height: 16px; width: 16px; opacity: 0.25; }
    .stars mat-icon.on { opacity: 1; color: #f59e0b; }
    .fb { flex-basis: 100%; margin: 0 0 0 130px; font-size: 0.82rem; opacity: 0.85; }
    @media (max-width: 600px) { .fb { margin-left: 0; } }
    .note { display: flex; gap: 0.5rem; align-items: baseline; }
    .note .grow { flex: 1; }
    .trail { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
    .trail li { display: flex; gap: 0.6rem; font-size: 0.88rem; }
    .trail .ev { font-size: 18px; height: 18px; width: 18px; opacity: 0.55; margin-top: 2px; }
    .side .kv { display: grid; grid-template-columns: 105px 1fr; gap: 0.35rem 0.5rem; margin: 0; font-size: 0.86rem; }
    .side dt { opacity: 0.65; } .side dd { margin: 0; overflow-wrap: anywhere; }
    .resume { display: flex; flex-wrap: wrap; gap: 0.25rem; align-items: center; margin-top: 1rem; }
    .others { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.35rem; font-size: 0.86rem; }
    .others li { display: flex; justify-content: space-between; gap: 0.5rem; align-items: center; }
  `
})
export class ApplicationDetailComponent {
  id = input.required<string>();

  private recruitment = inject(RecruitmentService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  /** Converting creates an employee record, which needs EMPLOYEE_WRITE on top of recruiting rights. */
  readonly canHire = inject(AuthService).hasPermission('EMPLOYEE_WRITE');
  readonly movable = MOVABLE;
  readonly five = [1, 2, 3, 4, 5];
  app = signal<ApplicationDetail | null>(null);
  note = '';

  isTerminal = computed(() => ['HIRED', 'REJECTED', 'WITHDRAWN'].includes(this.app()?.stage ?? ''));
  canDraftOffer = computed(() => {
    const a = this.app();
    return !!a && !this.isTerminal()
      && !a.offers.some((o) => ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT', 'ACCEPTED'].includes(o.status));
  });
  otherApplications = computed(() => {
    const a = this.app();
    return a ? a.candidate.applications.filter((x) => x.applicationId !== a.id) : [];
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.recruitment.application(Number(this.id())).subscribe((a) => this.app.set(a));
  }

  // ---- stage & notes ----

  move(stage: ApplicationStage): void {
    const a = this.app()!;
    if (stage === 'REJECTED' || stage === 'WITHDRAWN') {
      this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
        width: '460px', maxWidth: '95vw',
        data: {
          title: stage === 'REJECTED' ? `Reject ${a.candidate.fullName}?` : `Mark ${a.candidate.fullName} as withdrawn?`,
          label: stage === 'REJECTED' ? 'Reason (kept on the record)' : 'Note (optional)',
          confirmText: stage === 'REJECTED' ? 'Reject' : 'Mark withdrawn', required: stage === 'REJECTED', danger: true
        }
      }).afterClosed().subscribe((reason) => {
        if (reason !== undefined) { this.apply(this.recruitment.moveStage(a.id, stage, reason || null), 'Stage updated'); }
      });
      return;
    }
    this.apply(this.recruitment.moveStage(a.id, stage), `Moved to ${stage.toLowerCase()}`);
  }

  addNote(): void {
    const text = this.note.trim();
    if (!text) { return; }
    this.recruitment.addNote(this.app()!.id, text).subscribe((a) => { this.app.set(a); this.note = ''; });
  }

  // ---- interviews ----

  schedule(): void {
    this.dialog.open(ScheduleInterviewDialogComponent, { data: this.app(), width: '680px', maxWidth: '95vw' })
      .afterClosed().subscribe((ok) => {
        if (ok) { this.snack.open('Interview scheduled; the panel sees it under My interviews', undefined, { duration: 3000 }); this.reload(); }
      });
  }

  setInterview(i: Interview, status: InterviewStatus): void {
    const labels: Record<string, string> = { COMPLETED: 'Mark completed', NO_SHOW: 'Record a no-show', CANCELLED: 'Cancel interview' };
    this.confirm.ask({
      title: `${labels[status]}: ${i.roundName}?`,
      message: status === 'COMPLETED' ? 'Panelists can still submit their scorecards afterwards.'
        : 'Pending scorecards for this round will no longer be requested.',
      confirmText: labels[status], danger: status !== 'COMPLETED'
    }).subscribe((yes) => yes && this.apply(this.recruitment.updateInterview(i.id, { status }), 'Interview updated'));
  }

  // ---- offers ----

  editOffer(offer: Offer | null): void {
    this.dialog.open(OfferDialogComponent, { data: { app: this.app()!, offer }, width: '720px', maxWidth: '95vw' })
      .afterClosed().subscribe((ok) => {
        if (ok) { this.snack.open(offer ? 'Offer updated' : 'Offer drafted', undefined, { duration: 2500 }); this.reload(); }
      });
  }

  submitOffer(o: Offer): void {
    this.confirm.ask({
      title: 'Submit offer for approval?',
      message: `${this.app()!.requisition.hiringManagerName} (hiring manager) and then HR will review ₹${o.annualCtc.toLocaleString('en-IN')} per annum.`,
      confirmText: 'Submit'
    }).subscribe((yes) => yes && this.apply(this.recruitment.submitOffer(o.id), 'Offer sent for approval'));
  }

  sendOffer(o: Offer): void {
    this.confirm.ask({
      title: 'Mark the offer as sent?',
      message: 'Download the letter and send it to the candidate, then record their answer here.',
      confirmText: 'Mark as sent'
    }).subscribe((yes) => yes && this.apply(this.recruitment.sendOffer(o.id), 'Offer marked as sent'));
  }

  respond(o: Offer, accepted: boolean): void {
    if (accepted) {
      this.confirm.ask({ title: 'Record acceptance?', message: 'You can then convert the candidate into an employee.', confirmText: 'Accepted' })
        .subscribe((yes) => yes && this.apply(this.recruitment.respondToOffer(o.id, true), 'Offer accepted 🎉'));
      return;
    }
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '460px', maxWidth: '95vw',
      data: { title: 'Record a decline', label: 'Reason, if shared', confirmText: 'Declined', required: false, danger: true }
    }).afterClosed().subscribe((reason) => {
      if (reason !== undefined) { this.apply(this.recruitment.respondToOffer(o.id, false, reason || null), 'Decline recorded'); }
    });
  }

  withdraw(o: Offer): void {
    this.confirm.ask({ title: 'Withdraw this offer?', message: 'Any pending approval is cancelled. You can draft a new offer afterwards.',
      confirmText: 'Withdraw', danger: true })
      .subscribe((yes) => yes && this.apply(this.recruitment.withdrawOffer(o.id), 'Offer withdrawn'));
  }

  letter(o: Offer): void {
    this.recruitment.offerLetter(o.id).subscribe((res) => saveBlob(res, 'offer-letter.pdf'));
  }

  hire(o: Offer): void {
    this.dialog.open(HireDialogComponent, { data: { app: this.app()!, offer: o }, width: '640px', maxWidth: '95vw', disableClose: true })
      .afterClosed().subscribe((res?: HireResponse) => {
        if (res) { this.snack.open(`${res.employeeName} is now ${res.employeeCode}`, undefined, { duration: 3500 }); this.reload(); }
      });
  }

  // ---- candidate ----

  editCandidate(): void {
    this.dialog.open(CandidateDialogComponent, { data: this.app()!.candidate, width: '720px', maxWidth: '95vw' })
      .afterClosed().subscribe((c) => c && this.reload());
  }

  resume(): void {
    this.recruitment.downloadResume(this.app()!.candidate.id).subscribe((res) => saveBlob(res, 'resume'));
  }

  upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) { return; }
    this.recruitment.uploadResume(this.app()!.candidate.id, file).subscribe(() => {
      this.snack.open('Resume uploaded', undefined, { duration: 2500 });
      this.reload();
    });
  }

  eventIcon(type: string): string {
    switch (type) {
      case 'STAGE_CHANGED': return 'swap_horiz';
      case 'NOTE': return 'sticky_note_2';
      case 'INTERVIEW_SCHEDULED': return 'event';
      case 'INTERVIEW_UPDATED': return 'event_repeat';
      case 'FEEDBACK': return 'rate_review';
      case 'OFFER': return 'request_quote';
      case 'HIRED': return 'celebration';
      default: return 'circle';
    }
  }

  private apply(call: Observable<unknown>, message: string): void {
    call.subscribe(() => {
      this.snack.open(message, undefined, { duration: 2500 });
      this.reload();
    });
  }
}
