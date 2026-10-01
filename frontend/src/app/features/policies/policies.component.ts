import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { PolicyService } from '../../core/services/policy.service';
import { Compliance, Policy, PolicyRequest } from '../../core/models/services.models';
import { fromIsoDate, toIsoDate } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';

// ============================================================ editor
@Component({
  selector: 'app-policy-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatDatepickerModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ p ? 'Edit ' + p.title : 'New policy' }}</h2>
    <mat-dialog-content>
      @if (p?.status === 'PUBLISHED') {
        <p class="warn">Changing the title or wording publishes version {{ p!.versionNo + 1 }} straight away, and everyone has to acknowledge it again.</p>
      }
      <form [formGroup]="form" id="polForm" (ngSubmit)="save()" class="hg-form-grid">
        <mat-form-field appearance="outline"><mat-label>Code</mat-label>
          <input matInput formControlName="code" required maxlength="30" placeholder="e.g. TRAVEL" /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Category</mat-label>
          <input matInput formControlName="category" required maxlength="40" /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Title</mat-label>
          <input matInput formControlName="title" required maxlength="160" /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>One-line summary</mat-label>
          <input matInput formControlName="summary" maxlength="500" /></mat-form-field>
        <mat-form-field appearance="outline" class="full"><mat-label>Policy text</mat-label>
          <textarea matInput rows="10" formControlName="body" required maxlength="4000"></textarea></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Effective from</mat-label>
          <input matInput [matDatepicker]="ef" formControlName="effectiveDate" />
          <mat-datepicker-toggle matIconSuffix [for]="ef" /><mat-datepicker #ef /></mat-form-field>
        <mat-checkbox formControlName="requiresAck">Employees must acknowledge</mat-checkbox>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="polForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `.warn { background: #fef3c7; color: #92400e; padding: 0.5rem 0.75rem; border-radius: 8px; font-size: 0.86rem; }`
})
export class PolicyDialogComponent {
  p = inject<Policy | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<PolicyDialogComponent, boolean>);
  private policies = inject(PolicyService);
  private fb = inject(FormBuilder);

  saving = signal(false);
  form = this.fb.group({
    code: [{ value: this.p?.code ?? '', disabled: !!this.p }, [Validators.required, Validators.pattern(/^[A-Z0-9-]{2,30}$/)]],
    category: [this.p?.category ?? 'HR', Validators.required],
    title: [this.p?.title ?? '', Validators.required],
    summary: [this.p?.summary ?? ''],
    body: [this.p?.body ?? '', Validators.required],
    effectiveDate: [fromIsoDate(this.p?.effectiveDate)],
    requiresAck: [this.p?.requiresAck ?? true]
  });

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const req: PolicyRequest = {
      code: v.code!.trim().toUpperCase(), category: v.category!.trim(), title: v.title!.trim(), summary: v.summary || null,
      body: v.body!.trim(), effectiveDate: toIsoDate(v.effectiveDate), requiresAck: !!v.requiresAck
    };
    this.saving.set(true);
    (this.p ? this.policies.update(this.p.id, req) : this.policies.create(req)).subscribe({
      next: () => this.ref.close(true),
      error: () => this.saving.set(false)
    });
  }
}

// ============================================================ compliance
@Component({
  selector: 'app-compliance-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (c(); as c) {
      <h2 mat-dialog-title>{{ c.title }} · v{{ c.versionNo }}</h2>
      <mat-dialog-content>
        <p><strong>{{ c.acknowledgedCount }}</strong> of {{ c.employeeCount }} current employees have acknowledged this version.</p>
        <mat-progress-bar aria-label="Policy acknowledgement progress" mode="determinate" [value]="c.employeeCount ? 100 * c.acknowledgedCount / c.employeeCount : 0" />
        <h3>Still pending ({{ c.pending.length }})</h3>
        <ul class="pending">
          @for (e of c.pending; track e.employeeId) { <li><strong>{{ e.employeeName }}</strong> <span class="muted">{{ e.employeeCode }} · {{ e.department }}</span></li> }
          @empty { <li class="muted">Everyone is up to date. 🎉</li> }
        </ul>
      </mat-dialog-content>
      <mat-dialog-actions align="end"><button mat-flat-button color="primary" mat-dialog-close>Close</button></mat-dialog-actions>
    } @else { <mat-dialog-content><p class="muted">Loading…</p></mat-dialog-content> }
  `,
  styles: `h3 { font-size: 0.9rem; margin: 1rem 0 0.4rem; } .pending { max-height: 320px; overflow: auto; padding-left: 1.1rem; margin: 0; display: grid; gap: 0.2rem; font-size: 0.88rem; }`
})
export class ComplianceDialogComponent {
  private id = inject<number>(MAT_DIALOG_DATA);
  c = signal<Compliance | null>(null);

  constructor() {
    inject(PolicyService).compliance(this.id).subscribe((c) => this.c.set(c));
  }
}

// ============================================================ library
@Component({
  selector: 'app-policies',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatMenuModule, MatProgressBarModule, PageHeaderComponent, StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Policies" subtitle="Company policies and your acknowledgements"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Policies' }]">
        @if (canManage) { <button mat-flat-button color="primary" (click)="edit(null)"><mat-icon>add</mat-icon> New policy</button> }
      </hg-page-header>

      @if (pendingCount()) {
        <p class="banner" role="status"><mat-icon>assignment_late</mat-icon>
          You have {{ pendingCount() }} polic{{ pendingCount() === 1 ? 'y' : 'ies' }} to read and acknowledge.</p>
      }

      <div class="list">
        @for (p of policies(); track p.id) {
          <article class="policy" [class.needs]="p.status === 'PUBLISHED' && p.requiresAck && !p.acknowledged">
            <header>
              <div class="grow">
                <strong>{{ p.title }}</strong> <span class="muted small">v{{ p.versionNo }} · {{ p.category }}</span>
                @if (p.summary) { <div class="muted small">{{ p.summary }}</div> }
              </div>
              @if (canManage) { <hg-status [value]="p.status" /> }
              @if (p.status === 'PUBLISHED' && p.requiresAck) {
                @if (p.acknowledged) { <span class="done"><mat-icon inline>check_circle</mat-icon> Acknowledged {{ p.acknowledgedAt | date: 'MMM d' }}</span> }
              }
              <button mat-button (click)="toggle(p)">{{ open() === p.id ? 'Hide' : 'Read' }}</button>
              @if (canManage) {
                <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Actions for ' + p.title"><mat-icon>more_vert</mat-icon></button>
                <mat-menu #m="matMenu">
                  @if (p.status !== 'ARCHIVED') { <button mat-menu-item (click)="edit(p)"><mat-icon>edit</mat-icon><span>Edit</span></button> }
                  @if (p.status === 'DRAFT') { <button mat-menu-item (click)="publish(p)"><mat-icon>publish</mat-icon><span>Publish</span></button> }
                  @if (p.status === 'PUBLISHED' && p.requiresAck) { <button mat-menu-item (click)="compliance(p)"><mat-icon>fact_check</mat-icon><span>Who has acknowledged</span></button> }
                  @if (p.status !== 'ARCHIVED') { <button mat-menu-item (click)="archive(p)"><mat-icon>archive</mat-icon><span>Archive</span></button> }
                </mat-menu>
              }
            </header>
            @if (canManage && p.status === 'PUBLISHED' && p.requiresAck && p.employeeCount) {
              <div class="rate small">
                <mat-progress-bar aria-label="Policy acknowledgement progress" mode="determinate" [value]="100 * (p.acknowledgedCount ?? 0) / p.employeeCount" />
                <span>{{ p.acknowledgedCount }}/{{ p.employeeCount }} acknowledged</span>
              </div>
            }
            @if (open() === p.id) {
              <div class="body">{{ p.body }}</div>
              <p class="muted small">@if (p.effectiveDate) { Effective {{ p.effectiveDate | date: 'MMM d, y' }} · } Published {{ p.publishedAt ? (p.publishedAt | date: 'MMM d, y') : '—' }}</p>
              @if (p.status === 'PUBLISHED' && p.requiresAck && !p.acknowledged) {
                <div class="ack"><button mat-flat-button color="primary" (click)="acknowledge(p)"><mat-icon>verified</mat-icon> I have read and agree</button></div>
              }
            }
          </article>
        }
      </div>
    </div>
  `,
  styles: `
    .banner { display: flex; gap: 0.5rem; align-items: center; background: #fef3c7; color: #92400e; padding: 0.6rem 0.9rem; border-radius: 10px; }
    .list { display: grid; gap: 0.6rem; margin-top: 1rem; }
    .policy { border: 1px solid var(--hg-border, rgba(0,0,0,0.12)); border-radius: 12px; padding: 0.75rem 1rem; }
    .policy.needs { border-left: 4px solid #f59e0b; }
    .policy header { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
    .grow { flex: 1; min-width: 220px; }
    .small { font-size: 0.8rem; }
    .done { color: var(--hg-primary); font-size: 0.85rem; }
    .rate { display: flex; align-items: center; gap: 0.75rem; margin-top: 0.5rem; }
    .rate mat-progress-bar { max-width: 280px; }
    .body { white-space: pre-line; line-height: 1.6; margin: 0.75rem 0 0.5rem; padding: 0.75rem 1rem; border-radius: 10px;
      background: var(--mat-sys-surface-container-low, rgba(0,0,0,0.03)); }
    .ack { display: flex; justify-content: flex-end; }
  `
})
export class PoliciesComponent {
  private service = inject(PolicyService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  readonly canManage = inject(AuthService).hasPermission('POLICY_MANAGE');
  policies = signal<Policy[]>([]);
  pendingCount = signal(0);
  open = signal<number | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.service.list().subscribe((p) => {
      this.policies.set(p);
      this.pendingCount.set(p.filter((x) => x.status === 'PUBLISHED' && x.requiresAck && !x.acknowledged).length);
    });
  }

  toggle(p: Policy): void {
    this.open.set(this.open() === p.id ? null : p.id);
  }

  acknowledge(p: Policy): void {
    this.service.acknowledge(p.id).subscribe(() => {
      this.snack.open(`Acknowledged ${p.title}`, undefined, { duration: 2500 });
      this.reload();
    });
  }

  edit(p: Policy | null): void {
    this.dialog.open(PolicyDialogComponent, { data: p, width: '760px', maxWidth: '95vw' }).afterClosed()
      .subscribe((ok) => { if (ok) { this.snack.open('Policy saved', undefined, { duration: 2500 }); this.reload(); } });
  }

  publish(p: Policy): void {
    this.confirm.ask({ title: `Publish ${p.title}?`, message: p.requiresAck ? 'Everyone will be asked to acknowledge it.' : 'It becomes visible to everyone.',
      confirmText: 'Publish' }).subscribe((yes) => yes && this.service.publish(p.id).subscribe(() => this.reload()));
  }

  archive(p: Policy): void {
    this.confirm.ask({ title: `Archive ${p.title}?`, message: 'It is hidden from employees; acknowledgement history is kept.',
      confirmText: 'Archive', danger: true }).subscribe((yes) => yes && this.service.archive(p.id).subscribe(() => this.reload()));
  }

  compliance(p: Policy): void {
    this.dialog.open(ComplianceDialogComponent, { data: p.id, width: '560px', maxWidth: '95vw' });
  }
}
