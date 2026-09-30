import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { CANDIDATE_SOURCES, Candidate, CandidateSource, CandidateSummary } from '../../core/models/recruitment.models';
import { saveBlob } from '../../core/utils/http.utils';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { CellDefDirective, ColumnDef, DataTableComponent } from '../../shared/components/data-table.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { FileSizePipe, HumanizePipe, InrPipe } from '../../shared/pipes/labels.pipe';
import { CandidateDialogComponent } from './recruitment-dialogs.component';

// ============================================================ profile dialog
@Component({
  selector: 'app-candidate-profile-dialog',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, MatDialogModule, MatButtonModule, MatIconModule, StatusChipComponent,
    HumanizePipe, FileSizePipe, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (c(); as c) {
      <h2 mat-dialog-title>{{ c.fullName }}</h2>
      <mat-dialog-content>
        <p class="muted">{{ c.currentTitle || '—' }}{{ c.currentCompany ? ' at ' + c.currentCompany : '' }}
          · {{ c.source | humanize }} · added {{ c.createdAt | date: 'MMM d, y' }}</p>
        <dl class="kv">
          <dt>Email</dt><dd><a [href]="'mailto:' + c.email">{{ c.email }}</a></dd>
          <dt>Phone</dt><dd>{{ c.phone || '—' }}</dd>
          <dt>Experience</dt><dd>{{ c.totalExperience != null ? (c.totalExperience | number: '1.0-1') + ' yrs' : '—' }}</dd>
          <dt>Expected CTC</dt><dd>{{ c.expectedCtc | inr }}</dd>
          <dt>Notice</dt><dd>{{ c.noticePeriodDays != null ? c.noticePeriodDays + ' days' : '—' }}</dd>
          <dt>City</dt><dd>{{ c.city || '—' }}</dd>
        </dl>
        <div class="resume">
          @if (c.hasResume) {
            <button mat-stroked-button (click)="resume(c)"><mat-icon>description</mat-icon>
              {{ c.resumeFileName }} @if (c.resumeSize) { ({{ c.resumeSize | fileSize }}) }</button>
          } @else { <span class="muted">No resume on file</span> }
          <input #rf type="file" hidden accept=".pdf,.docx" (change)="upload(c, rf)" />
          <button mat-button (click)="rf.click()"><mat-icon>upload</mat-icon> {{ c.hasResume ? 'Replace' : 'Upload' }}</button>
        </div>
        <h3>Applications</h3>
        @for (a of c.applications; track a.applicationId) {
          <div class="app">
            <a [routerLink]="['/recruitment/applications', a.applicationId]" mat-dialog-close>{{ a.requisitionTitle }}</a>
            <span class="muted">{{ a.reqCode }}</span>
            <hg-status [value]="a.stage" />
          </div>
        } @empty {
          <p class="muted">Not in any pipeline yet. Open a requisition and use "Add from pool".</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button (click)="edit(c)"><mat-icon>edit</mat-icon> Edit</button>
        <button mat-flat-button color="primary" mat-dialog-close>Close</button>
      </mat-dialog-actions>
    }
  `,
  styles: `
    .kv { display: grid; grid-template-columns: 120px 1fr; gap: 0.35rem 0.5rem; margin: 0.5rem 0; font-size: 0.9rem; }
    .kv dt { opacity: 0.65; } .kv dd { margin: 0; }
    .resume { display: flex; flex-wrap: wrap; gap: 0.25rem; align-items: center; margin: 0.75rem 0; }
    h3 { font-size: 0.9rem; margin: 1rem 0 0.4rem; }
    .app { display: flex; gap: 0.6rem; align-items: center; padding: 0.3rem 0; }
    .app a { font-weight: 600; }
    .app hg-status { margin-left: auto; }
  `
})
export class CandidateProfileDialogComponent {
  private id = inject<number>(MAT_DIALOG_DATA);
  private recruitment = inject(RecruitmentService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  c = signal<Candidate | null>(null);
  /** Read by the opener after close to decide whether the list needs a refresh. */
  changed = false;

  constructor() {
    this.load();
  }

  load(): void {
    this.recruitment.candidate(this.id).subscribe((c) => this.c.set(c));
  }

  resume(c: Candidate): void {
    this.recruitment.downloadResume(c.id).subscribe((res) => saveBlob(res, 'resume'));
  }

  upload(c: Candidate, input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) { return; }
    this.recruitment.uploadResume(c.id, file).subscribe((updated) => {
      this.c.set(updated);
      this.changed = true;
      this.snack.open('Resume uploaded', undefined, { duration: 2500 });
    });
  }

  edit(c: Candidate): void {
    this.dialog.open(CandidateDialogComponent, { data: c, width: '720px', maxWidth: '95vw' }).afterClosed()
      .subscribe((saved?: Candidate) => {
        if (saved) { this.changed = true; this.load(); }
      });
  }
}

// ============================================================ list
@Component({
  selector: 'app-candidate-list',
  standalone: true,
  imports: [DecimalPipe, DatePipe, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, PageHeaderComponent, DataTableComponent, CellDefDirective, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Candidates" subtitle="Everyone in the talent pool, across all requisitions"
                      [breadcrumbs]="[{ label: 'Recruitment', link: '/recruitment' }, { label: 'Candidates' }]">
        <button mat-flat-button color="primary" (click)="add()"><mat-icon>person_add</mat-icon> Add candidate</button>
      </hg-page-header>

      <div class="hg-filters" role="search">
        <mat-form-field appearance="outline" class="search">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Name, email, company or title" [ngModel]="search()" (ngModelChange)="search.set($event)"
                 aria-label="Search candidates" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-select placeholder="Source" [ngModel]="source()" (ngModelChange)="source.set($event); refresh()" aria-label="Source">
            <mat-option [value]="null">Any source</mat-option>
            @for (s of sources; track s) { <mat-option [value]="s">{{ s | humanize }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>

      <hg-data-table [columns]="columns" [rows]="rows()" [total]="total()" [pageIndex]="page()" [pageSize]="size()"
                     [loading]="loading()" sortActive="createdAt" sortDirection="desc" storageKey="candidates"
                     emptyIcon="person_search" emptyTitle="No candidates found" [clickable]="true"
                     (rowClick)="view($event)" (pageChange)="onPage($event)"
                     (sortChange)="sort.set($event || 'createdAt,desc'); reload()">
        <ng-template hgCell="name" let-row>
          <div class="hg-person"><span class="meta"><strong>{{ row.fullName }}</strong><small>{{ row.email }}</small></span></div>
        </ng-template>
        <ng-template hgCell="current" let-row>
          {{ row.currentTitle || '—' }}@if (row.currentCompany) { <span class="muted"> · {{ row.currentCompany }}</span> }
        </ng-template>
        <ng-template hgCell="exp" let-row>{{ row.totalExperience != null ? (row.totalExperience | number: '1.0-1') + ' yrs' : '—' }}</ng-template>
        <ng-template hgCell="resume" let-row>
          @if (row.hasResume) { <mat-icon aria-label="Resume on file" inline>description</mat-icon> }
        </ng-template>
        <ng-template hgCell="added" let-row>{{ row.createdAt | date: 'MMM d, y' }}</ng-template>
      </hg-data-table>
    </div>
  `
})
export class CandidateListComponent {
  private recruitment = inject(RecruitmentService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  readonly sources = CANDIDATE_SOURCES;
  readonly columns: ColumnDef<CandidateSummary>[] = [
    { key: 'name', label: 'Candidate', sortable: true, sortKey: 'firstName', pinned: true },
    { key: 'current', label: 'Current role' },
    { key: 'exp', label: 'Experience', sortable: true, sortKey: 'totalExperience' },
    { key: 'city', label: 'City', value: (r) => r.city ?? '—', hiddenByDefault: true },
    { key: 'source', label: 'Source', sortable: true, value: (r) => r.source.replace('_', ' ').toLowerCase() },
    { key: 'applications', label: 'Applications', value: (r) => r.applications, align: 'end' },
    { key: 'resume', label: 'CV' },
    { key: 'added', label: 'Added', sortable: true, sortKey: 'createdAt' }
  ];

  rows = signal<CandidateSummary[]>([]);
  total = signal(0);
  page = signal(0);
  size = signal(20);
  sort = signal('createdAt,desc');
  search = signal('');
  source = signal<CandidateSource | null>(null);
  loading = signal(true);

  constructor() {
    toObservable(this.search).pipe(skip(1), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.refresh());
    this.reload();
  }

  refresh(): void {
    this.page.set(0);
    this.reload();
  }

  onPage(e: PageEvent): void {
    this.page.set(e.pageIndex);
    this.size.set(e.pageSize);
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.recruitment.candidates({ search: this.search(), source: this.source() },
      { page: this.page(), size: this.size(), sort: this.sort() }).subscribe({
      next: (res) => { this.rows.set(res.content); this.total.set(res.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  view(c: CandidateSummary): void {
    const ref = this.dialog.open(CandidateProfileDialogComponent, { data: c.id, width: '620px', maxWidth: '95vw' });
    ref.afterClosed().subscribe(() => ref.componentInstance?.changed && this.reload());
  }

  add(): void {
    this.dialog.open(CandidateDialogComponent, { data: null, width: '720px', maxWidth: '95vw' }).afterClosed()
      .subscribe((c?: Candidate) => {
        if (c) {
          this.snack.open(`${c.fullName} added to the talent pool`, undefined, { duration: 2500 });
          this.reload();
        }
      });
  }
}
