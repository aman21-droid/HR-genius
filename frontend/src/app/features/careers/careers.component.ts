import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CareersService } from '../../core/services/careers.service';
import { ThemeService } from '../../core/services/theme.service';
import { CareerJob } from '../../core/models/recruitment.models';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

const MAX_RESUME_BYTES = 10 * 1024 * 1024;
const RESUME_TYPES = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];

/** Frame shared by the public careers pages: brand bar, content, sign-in link for staff. */
@Component({
  selector: 'app-careers-frame',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="careers">
      <header class="bar">
        <a routerLink="/careers" class="brand"><mat-icon>groups</mat-icon> HRGenius <span>Careers</span></a>
        <span class="spacer"></span>
        <button mat-icon-button (click)="theme.toggle()" [attr.aria-label]="theme.theme() === 'dark' ? 'Light mode' : 'Dark mode'">
          <mat-icon>{{ theme.theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon></button>
        <a mat-button routerLink="/login">Employee sign in</a>
      </header>
      <main class="content"><ng-content /></main>
      <footer class="foot muted">We never ask candidates for payment. Questions? Reply to any email from our recruiting team.</footer>
    </div>
  `,
  styles: `
    .careers { min-height: 100vh; background: var(--hg-bg, var(--mat-sys-surface)); color: var(--mat-sys-on-surface); display: flex; flex-direction: column; }
    .bar { display: flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1.25rem; border-bottom: 1px solid var(--hg-border, rgba(0,0,0,0.1)); }
    .brand { display: inline-flex; align-items: center; gap: 0.4rem; font-weight: 700; font-size: 1.1rem; color: inherit; text-decoration: none; }
    .brand span { font-weight: 400; opacity: 0.7; }
    .spacer { flex: 1; }
    .content { flex: 1; width: 100%; max-width: 960px; margin: 0 auto; padding: 1.5rem 16px 3rem; box-sizing: border-box; }
    .foot { text-align: center; font-size: 0.8rem; padding: 1.25rem 16px; }
  `
})
export class CareersFrameComponent {
  theme = inject(ThemeService);
}

// ============================================================ job list
@Component({
  selector: 'app-careers',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    HumanizePipe, CareersFrameComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-careers-frame>
      <section class="hero">
        <h1>Build the future of work with us</h1>
        <p class="muted">We're a small team making HR software people actually enjoy. Find a role that fits you.</p>
      </section>

      <div class="filters">
        <mat-form-field appearance="outline" class="q">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search roles or skills" (input)="q.set($any($event.target).value)" aria-label="Search roles" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-select placeholder="Location" (selectionChange)="loc.set($event.value)" aria-label="Location">
            <mat-option [value]="null">All locations</mat-option>
            @for (l of locations(); track l) { <mat-option [value]="l">{{ l }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>

      @if (loading()) {
        <p class="muted">Loading open roles…</p>
      } @else {
        <p class="muted small">{{ filtered().length }} open role{{ filtered().length === 1 ? '' : 's' }}</p>
        <div class="jobs">
          @for (j of filtered(); track j.reqCode) {
            <a class="job" [routerLink]="['/careers', j.reqCode]">
              <div>
                <h2>{{ j.title }}</h2>
                <p class="muted">{{ j.department }} · {{ j.location }} · {{ j.employmentType | humanize }}
                  @if (j.minExperience != null) { · {{ j.minExperience | number: '1.0-1' }}{{ j.maxExperience != null ? '–' + (j.maxExperience | number: '1.0-1') : '+' }} yrs }</p>
                @if (j.skills) { <p class="skills">{{ j.skills }}</p> }
              </div>
              <span class="posted muted">{{ j.postedAt ? 'Posted ' + (j.postedAt | date: 'MMM d') : '' }}</span>
              <mat-icon class="go">chevron_right</mat-icon>
            </a>
          } @empty {
            <p class="muted">No roles match right now. Check back soon!</p>
          }
        </div>
      }
    </app-careers-frame>
  `,
  styles: `
    .hero { padding: 1rem 0 1.5rem; }
    .hero h1 { font-size: clamp(1.6rem, 4vw, 2.2rem); margin: 0 0 0.5rem; }
    .filters { display: flex; flex-wrap: wrap; gap: 0.75rem; }
    .filters .q { flex: 1; min-width: 220px; }
    .small { font-size: 0.85rem; }
    .jobs { display: grid; gap: 0.6rem; }
    .job { display: flex; align-items: center; gap: 1rem; padding: 1rem 1.25rem; border: 1px solid var(--hg-border, rgba(0,0,0,0.12));
      border-radius: 14px; color: inherit; text-decoration: none; }
    .job:hover, .job:focus-visible { border-color: var(--mat-sys-primary, #1565c0); }
    .job > div { flex: 1; min-width: 0; }
    .job h2 { font-size: 1.05rem; margin: 0 0 0.2rem; }
    .job p { margin: 0; font-size: 0.88rem; }
    .skills { margin-top: 0.35rem !important; opacity: 0.8; }
    .posted { font-size: 0.8rem; white-space: nowrap; }
    @media (max-width: 600px) { .posted { display: none; } }
  `
})
export class CareersComponent {
  private careers = inject(CareersService);

  jobs = signal<CareerJob[]>([]);
  loading = signal(true);
  q = signal('');
  loc = signal<string | null>(null);

  locations = computed(() => [...new Set(this.jobs().map((j) => j.location))].sort());
  filtered = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.jobs().filter((j) =>
      (!this.loc() || j.location === this.loc())
      && (!q || `${j.title} ${j.skills ?? ''} ${j.department}`.toLowerCase().includes(q)));
  });

  constructor() {
    this.careers.jobs().subscribe({
      next: (j) => { this.jobs.set(j); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}

// ============================================================ job detail + apply
@Component({
  selector: 'app-career-job',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, HumanizePipe, CareersFrameComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-careers-frame>
      <a routerLink="/careers" class="back"><mat-icon inline>arrow_back</mat-icon> All roles</a>
      @if (job(); as j) {
        <header class="head">
          <h1>{{ j.title }}</h1>
          <p class="muted">{{ j.department }} · {{ j.location }} · {{ j.employmentType | humanize }}
            @if (j.minExperience != null) { · {{ j.minExperience | number: '1.0-1' }}{{ j.maxExperience != null ? '–' + (j.maxExperience | number: '1.0-1') : '+' }} years' experience }</p>
        </header>

        <div class="layout">
          <article class="about">
            @if (j.description) { <p class="desc">{{ j.description }}</p> }
            @if (j.skills) {
              <h2>What we're looking for</h2>
              <ul class="skills">@for (s of skillList(); track s) { <li>{{ s }}</li> }</ul>
            }
            @if (j.postedAt) { <p class="muted small">Posted {{ j.postedAt | date: 'MMMM d, y' }} · Ref {{ j.reqCode }}</p> }
          </article>

          <section class="apply" aria-labelledby="applyTitle">
            @if (sent()) {
              <div class="thanks" role="status">
                <mat-icon>mark_email_read</mat-icon>
                <h2>Application received</h2>
                <p>{{ sent() }}</p>
                <a mat-stroked-button routerLink="/careers">See other roles</a>
              </div>
            } @else {
              <h2 id="applyTitle">Apply for this role</h2>
              <form [formGroup]="form" (ngSubmit)="submit(j)" class="form" novalidate>
                <div class="two">
                  <mat-form-field appearance="outline"><mat-label>First name</mat-label>
                    <input matInput formControlName="firstName" autocomplete="given-name" required maxlength="80" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Last name</mat-label>
                    <input matInput formControlName="lastName" autocomplete="family-name" required maxlength="80" /></mat-form-field>
                </div>
                <mat-form-field appearance="outline"><mat-label>Email</mat-label>
                  <input matInput type="email" formControlName="email" autocomplete="email" required maxlength="160" />
                  @if (form.controls.email.hasError('email')) { <mat-error>Enter a valid email</mat-error> }</mat-form-field>
                <mat-form-field appearance="outline"><mat-label>Phone</mat-label>
                  <input matInput type="tel" formControlName="phone" autocomplete="tel" maxlength="30" />
                  @if (form.controls.phone.hasError('pattern')) { <mat-error>Digits, spaces, + - ( ) only</mat-error> }</mat-form-field>
                <div class="two">
                  <mat-form-field appearance="outline"><mat-label>Current company</mat-label>
                    <input matInput formControlName="currentCompany" autocomplete="organization" maxlength="120" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Current title</mat-label>
                    <input matInput formControlName="currentTitle" autocomplete="organization-title" maxlength="120" /></mat-form-field>
                </div>
                <div class="two">
                  <mat-form-field appearance="outline"><mat-label>Experience (years)</mat-label>
                    <input matInput type="number" min="0" max="60" step="0.5" formControlName="totalExperience" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Notice period (days)</mat-label>
                    <input matInput type="number" min="0" max="365" formControlName="noticePeriodDays" /></mat-form-field>
                </div>
                <mat-form-field appearance="outline"><mat-label>City</mat-label>
                  <input matInput formControlName="city" autocomplete="address-level2" maxlength="80" /></mat-form-field>
                <mat-form-field appearance="outline"><mat-label>LinkedIn profile</mat-label>
                  <input matInput type="url" formControlName="linkedinUrl" placeholder="https://" maxlength="300" />
                  @if (form.controls.linkedinUrl.hasError('pattern')) { <mat-error>Must start with http:// or https://</mat-error> }</mat-form-field>
                <mat-form-field appearance="outline"><mat-label>Why this role? (optional)</mat-label>
                  <textarea matInput rows="3" formControlName="coverNote" maxlength="2000"></textarea></mat-form-field>

                <!-- Honeypot: hidden from people and assistive tech; bots tend to fill it. -->
                <div class="hp" aria-hidden="true">
                  <label>Website <input type="text" formControlName="website" tabindex="-1" autocomplete="off" /></label>
                </div>

                <div class="file" [class.err]="fileError()">
                  <input #cv type="file" id="cv" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                         (change)="pick(cv)" hidden />
                  <button mat-stroked-button type="button" (click)="cv.click()"><mat-icon>upload_file</mat-icon> {{ file() ? 'Change resume' : 'Attach resume' }}</button>
                  <span class="small">{{ file()?.name || 'PDF or DOCX, up to 10 MB' }}</span>
                  @if (fileError()) { <p class="small errtxt" role="alert">{{ fileError() }}</p> }
                </div>

                <p class="muted small">By applying you agree that we store your details and resume to consider you for roles at HRGenius.</p>
                <button mat-flat-button color="primary" type="submit" [disabled]="sending()">
                  {{ sending() ? 'Sending…' : 'Submit application' }}</button>
              </form>
            }
          </section>
        </div>
      } @else if (notFound()) {
        <div class="gone">
          <h1>This role is no longer open</h1>
          <p class="muted">It may have been filled. Have a look at our other openings.</p>
          <a mat-flat-button color="primary" routerLink="/careers">View open roles</a>
        </div>
      } @else {
        <p class="muted">Loading…</p>
      }
    </app-careers-frame>
  `,
  styles: `
    .back { display: inline-flex; align-items: center; gap: 0.25rem; font-size: 0.88rem; margin-bottom: 1rem; }
    .head h1 { font-size: clamp(1.5rem, 4vw, 2rem); margin: 0 0 0.35rem; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 420px); gap: 2rem; margin-top: 1.25rem; align-items: start; }
    @media (max-width: 860px) { .layout { grid-template-columns: 1fr; } }
    .desc { white-space: pre-line; line-height: 1.6; }
    .about h2, .apply h2 { font-size: 1.05rem; }
    .skills { padding-left: 1.2rem; line-height: 1.7; }
    .small { font-size: 0.82rem; }
    .apply { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 16px; padding: 1.25rem; }
    .apply h2 { margin-top: 0; }
    .form { display: grid; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 0 0.75rem; }
    @media (max-width: 480px) { .two { grid-template-columns: 1fr; } }
    .hp { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
    .file { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; margin: 0.25rem 0 0.75rem; }
    .file.err button { border-color: var(--mat-sys-error, #c62828); }
    .errtxt { color: var(--mat-sys-error, #c62828); flex-basis: 100%; margin: 0; }
    .thanks { text-align: center; padding: 1rem 0; }
    .thanks mat-icon { font-size: 44px; height: 44px; width: 44px; color: #16a34a; }
    .gone { text-align: center; padding: 3rem 0; }
  `
})
export class CareerJobComponent {
  code = input.required<string>();

  private careers = inject(CareersService);
  private fb = inject(FormBuilder);

  job = signal<CareerJob | null>(null);
  notFound = signal(false);
  file = signal<File | null>(null);
  fileError = signal<string | null>(null);
  sending = signal(false);
  sent = signal<string | null>(null);

  skillList = computed(() => (this.job()?.skills ?? '').split(',').map((s) => s.trim()).filter(Boolean));

  form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.pattern(/^[+0-9 ()-]{7,30}$/)],
    currentCompany: [''],
    currentTitle: [''],
    totalExperience: [null as number | null, [Validators.min(0), Validators.max(60)]],
    noticePeriodDays: [null as number | null, [Validators.min(0), Validators.max(365)]],
    city: [''],
    linkedinUrl: ['', Validators.pattern(/^(https?:\/\/.*)?$/)],
    coverNote: [''],
    website: ['']
  });

  ngOnInit(): void {
    this.careers.job(this.code()).subscribe({
      next: (j) => this.job.set(j),
      error: () => this.notFound.set(true)
    });
  }

  pick(input: HTMLInputElement): void {
    const f = input.files?.[0] ?? null;
    input.value = '';
    if (!f) { return; }
    const okType = RESUME_TYPES.includes(f.type) || /\.(pdf|docx)$/i.test(f.name);
    if (!okType) { this.fileError.set('Please attach a PDF or DOCX file.'); return; }
    if (f.size > MAX_RESUME_BYTES) { this.fileError.set('That file is over 10 MB.'); return; }
    this.fileError.set(null);
    this.file.set(f);
  }

  submit(j: CareerJob): void {
    if (!this.file()) { this.fileError.set('Please attach your resume.'); }
    if (this.form.invalid || !this.file()) { this.form.markAllAsTouched(); return; }
    this.sending.set(true);
    const v = this.form.getRawValue();
    this.careers.apply(j.reqCode, {
      firstName: v.firstName!.trim(), lastName: v.lastName!.trim(), email: v.email!.trim(), phone: v.phone,
      currentCompany: v.currentCompany, currentTitle: v.currentTitle, totalExperience: v.totalExperience,
      noticePeriodDays: v.noticePeriodDays, city: v.city, linkedinUrl: v.linkedinUrl, coverNote: v.coverNote,
      website: v.website
    }, this.file()!).subscribe({
      next: (res) => { this.sending.set(false); this.sent.set(res.message); },
      error: () => this.sending.set(false)
    });
  }
}
