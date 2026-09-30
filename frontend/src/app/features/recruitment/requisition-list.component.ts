import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AsyncPipe, DatePipe } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { OrgService } from '../../core/services/org.service';
import { REQUISITION_STATUSES, Requisition, RequisitionStatus } from '../../core/models/recruitment.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { CellDefDirective, ColumnDef, DataTableComponent } from '../../shared/components/data-table.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import { RequisitionDialogComponent } from './recruitment-dialogs.component';

@Component({
  selector: 'app-requisition-list',
  standalone: true,
  imports: [AsyncPipe, DatePipe, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatMenuModule, PageHeaderComponent, DataTableComponent, CellDefDirective, StatusChipComponent,
    HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Recruitment" subtitle="Open roles, approvals and hiring pipelines"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Recruitment' }]">
        <button mat-flat-button color="primary" (click)="edit(null)"><mat-icon>add</mat-icon> New requisition</button>
      </hg-page-header>

      <div class="hg-filters" role="search">
        <mat-form-field appearance="outline" class="search">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search title or REQ code" [ngModel]="search()" (ngModelChange)="search.set($event)"
                 aria-label="Search requisitions" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-select placeholder="Status" [ngModel]="status()" (ngModelChange)="status.set($event); refresh()" aria-label="Status">
            <mat-option [value]="null">Any status</mat-option>
            @for (s of statuses; track s) { <mat-option [value]="s">{{ s | humanize }}</mat-option> }
          </mat-select>
        </mat-form-field>
        @if (lookups$ | async; as lk) {
          <mat-form-field appearance="outline">
            <mat-select placeholder="Department" [ngModel]="departmentId()" (ngModelChange)="departmentId.set($event); refresh()"
                        aria-label="Department">
              <mat-option [value]="null">All departments</mat-option>
              @for (d of lk.departments; track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
        }
      </div>

      <hg-data-table [columns]="columns" [rows]="rows()" [total]="total()" [pageIndex]="page()" [pageSize]="size()"
                     [loading]="loading()" sortActive="createdAt" sortDirection="desc" storageKey="requisitions"
                     emptyIcon="work_outline" emptyTitle="No requisitions yet" [clickable]="true"
                     emptyMessage="Raise a requisition to start hiring for a role."
                     (rowClick)="open($event)" (pageChange)="onPage($event)"
                     (sortChange)="sort.set($event || 'createdAt,desc'); reload()">
        <ng-template hgCell="role" let-row>
          <div class="hg-person"><span class="meta"><strong>{{ row.title }}</strong>
            <small>{{ row.reqCode }} · {{ row.departmentName }} · {{ row.locationName }}</small></span></div>
        </ng-template>
        <ng-template hgCell="openings" let-row>
          <span class="fill" [attr.aria-label]="row.filled + ' of ' + row.openings + ' filled'">
            <span class="bar"><i [style.width.%]="100 * row.filled / row.openings"></i></span>
            {{ row.filled }}/{{ row.openings }}
          </span>
        </ng-template>
        <ng-template hgCell="pipeline" let-row>
          @if (row.activeApplications) { <strong>{{ row.activeApplications }}</strong> active }
          @else { <span class="muted">—</span> }
        </ng-template>
        <ng-template hgCell="status" let-row><hg-status [value]="row.status" /></ng-template>
        <ng-template hgCell="actions" let-row>
          <button mat-icon-button [matMenuTriggerFor]="menu" (click)="$event.stopPropagation()"
                  [attr.aria-label]="'Actions for ' + row.reqCode"><mat-icon>more_vert</mat-icon></button>
          <mat-menu #menu="matMenu">
            <button mat-menu-item (click)="open(row)"><mat-icon>view_kanban</mat-icon><span>Open pipeline</span></button>
            @if (canEdit(row)) {
              <button mat-menu-item (click)="edit(row)"><mat-icon>edit</mat-icon><span>Edit</span></button>
            }
            @if (row.status === 'DRAFT' || row.status === 'REJECTED') {
              <button mat-menu-item (click)="submit(row)"><mat-icon>send</mat-icon><span>Submit for approval</span></button>
            }
            @if (row.status === 'OPEN') {
              <button mat-menu-item (click)="setStatus(row, 'ON_HOLD')"><mat-icon>pause_circle</mat-icon><span>Put on hold</span></button>
            }
            @if (row.status === 'ON_HOLD') {
              <button mat-menu-item (click)="setStatus(row, 'OPEN')"><mat-icon>play_circle</mat-icon><span>Reopen</span></button>
            }
            @if (row.status === 'OPEN' || row.status === 'ON_HOLD') {
              <button mat-menu-item (click)="setStatus(row, 'CLOSED')"><mat-icon>lock</mat-icon><span>Close</span></button>
            }
            @if (row.status === 'DRAFT' || row.status === 'REJECTED') {
              <button mat-menu-item (click)="setStatus(row, 'CANCELLED')"><mat-icon>cancel</mat-icon><span>Cancel</span></button>
            }
          </mat-menu>
        </ng-template>
      </hg-data-table>
    </div>
  `,
  styles: `
    .fill { display: inline-flex; align-items: center; gap: 0.5rem; font-variant-numeric: tabular-nums; }
    .fill .bar { width: 56px; height: 6px; border-radius: 3px; background: var(--hg-border, rgba(0,0,0,0.1)); overflow: hidden; }
    .fill .bar i { display: block; height: 100%; background: var(--mat-sys-primary, #1565c0); }
  `
})
export class RequisitionListComponent {
  private recruitment = inject(RecruitmentService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);
  private router = inject(Router);

  readonly lookups$ = inject(OrgService).lookups();
  readonly statuses = REQUISITION_STATUSES;
  readonly columns: ColumnDef<Requisition>[] = [
    { key: 'role', label: 'Role', sortable: true, sortKey: 'title', pinned: true },
    { key: 'hiringManagerName', label: 'Hiring manager', value: (r) => r.hiringManagerName },
    { key: 'openings', label: 'Filled' },
    { key: 'pipeline', label: 'Pipeline' },
    { key: 'targetDate', label: 'Target', sortable: true, value: (r) => r.targetDate ?? '—', hiddenByDefault: true },
    { key: 'status', label: 'Status', sortable: true },
    { key: 'actions', label: '', pinned: true, align: 'end' }
  ];

  rows = signal<Requisition[]>([]);
  total = signal(0);
  page = signal(0);
  size = signal(20);
  sort = signal('createdAt,desc');
  search = signal('');
  status = signal<RequisitionStatus | null>(null);
  departmentId = signal<number | null>(null);
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
    this.recruitment.requisitions(
      { search: this.search(), status: this.status(), departmentId: this.departmentId() },
      { page: this.page(), size: this.size(), sort: this.sort() }
    ).subscribe({
      next: (res) => { this.rows.set(res.content); this.total.set(res.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  canEdit(r: Requisition): boolean {
    return !['PENDING_APPROVAL', 'CLOSED', 'CANCELLED'].includes(r.status);
  }

  open(r: Requisition): void {
    this.router.navigate(['/recruitment/requisitions', r.id]);
  }

  edit(r: Requisition | null): void {
    this.dialog.open(RequisitionDialogComponent, { data: r, width: '760px', maxWidth: '95vw' }).afterClosed()
      .subscribe((saved?: Requisition) => {
        if (saved) {
          this.snack.open(r ? 'Requisition updated' : `${saved.reqCode} created as a draft`, undefined, { duration: 3000 });
          this.reload();
        }
      });
  }

  submit(r: Requisition): void {
    this.confirm.ask({
      title: `Submit ${r.reqCode} for approval?`,
      message: `${r.hiringManagerName} (hiring manager) and then HR will be asked to approve. It opens for applications once approved.`,
      confirmText: 'Submit'
    }).subscribe((yes) => yes && this.recruitment.submitRequisition(r.id).subscribe((res) => {
      this.snack.open(res.status === 'OPEN' ? `${r.reqCode} is now open` : `${r.reqCode} sent for approval`,
        undefined, { duration: 3000 });
      this.reload();
    }));
  }

  setStatus(r: Requisition, status: RequisitionStatus): void {
    const verbs: Partial<Record<RequisitionStatus, string>> = {
      ON_HOLD: 'Put on hold', OPEN: 'Reopen', CLOSED: 'Close', CANCELLED: 'Cancel'
    };
    const destructive = status === 'CLOSED' || status === 'CANCELLED';
    this.confirm.ask({
      title: `${verbs[status]} ${r.reqCode}?`,
      message: destructive ? 'It stops accepting applications and leaves the careers page. This cannot be undone.'
        : status === 'ON_HOLD' ? 'It is hidden from the careers page until reopened.' : 'It will accept applications again.',
      confirmText: verbs[status] ?? 'Confirm', danger: destructive
    }).subscribe((yes) => yes && this.recruitment.setRequisitionStatus(r.id, status).subscribe(() => {
      this.snack.open(`${r.reqCode} updated`, undefined, { duration: 2500 });
      this.reload();
    }));
  }
}
