import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs';
import { OrgService } from '../../core/services/org.service';
import { AuditLogEntry } from '../../core/models/org.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { CellDefDirective, ColumnDef, DataTableComponent } from '../../shared/components/data-table.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';

const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'VIEW_SENSITIVE', 'IMPORT'];
const ENTITIES = ['Employee', 'EmployeeStatutory', 'EmployeeDocument', 'User'];
const EMPLOYEE_ENTITIES = new Set(['Employee', 'EmployeeStatutory']);

/** Who changed what, when: old -> new. Sensitive values are stored masked by the backend. */
@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [DatePipe, RouterLink, FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule,
    PageHeaderComponent, DataTableComponent, CellDefDirective, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Audit trail" subtitle="Changes to sensitive data: job moves, pay, bank and statutory details, access"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Audit trail' }]" />
      <div class="hg-filters" role="search">
        <mat-form-field appearance="outline">
          <mat-select placeholder="Record type" [ngModel]="entity()" (ngModelChange)="entity.set($event); refresh()" aria-label="Record type">
            <mat-option [value]="null">All records</mat-option>
            @for (e of entities; track e) { <mat-option [value]="e">{{ e }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-select placeholder="Action" [ngModel]="action()" (ngModelChange)="action.set($event); refresh()" aria-label="Action">
            <mat-option [value]="null">Any action</mat-option>
            @for (a of actions; track a) { <mat-option [value]="a">{{ a | humanize }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <input matInput placeholder="Record id" [ngModel]="entityId()" (ngModelChange)="entityId.set($event)" aria-label="Record id" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="search">
          <mat-icon matPrefix>person_search</mat-icon>
          <input matInput placeholder="Changed by (email)" [ngModel]="actor()" (ngModelChange)="actor.set($event)" aria-label="Changed by" />
        </mat-form-field>
      </div>

      <hg-data-table [columns]="columns" [rows]="rows()" [total]="total()" [pageIndex]="page()" [pageSize]="size()"
                     [loading]="loading()" sortActive="changedAt" sortDirection="desc" storageKey="audit"
                     emptyIcon="history" emptyTitle="No matching changes" (pageChange)="onPage($event)"
                     (sortChange)="sort.set($event || 'changedAt,desc'); reload()">
        <ng-template hgCell="changedAt" let-row>{{ row.changedAt | date: 'medium' }}</ng-template>
        <ng-template hgCell="action" let-row><span class="hg-chip" [class]="tone(row.action)">{{ row.action | humanize }}</span></ng-template>
        <ng-template hgCell="record" let-row>
          @if (isEmployee(row) && row.entityId) {
            <a [routerLink]="['/employees', row.entityId]">{{ row.entity }} #{{ row.entityId }}</a>
          } @else { {{ row.entity }}{{ row.entityId ? ' #' + row.entityId : '' }} }
        </ng-template>
        <ng-template hgCell="change" let-row>
          @if (row.field) {
            <span class="change"><strong>{{ row.field }}</strong>:
              <span class="old">{{ row.oldValue ?? '∅' }}</span> <mat-icon inline aria-label="changed to">arrow_forward</mat-icon>
              <span class="new">{{ row.newValue ?? '∅' }}</span></span>
          } @else { <span>{{ row.newValue }}</span> }
        </ng-template>
      </hg-data-table>
    </div>
  `,
  styles: [`
    .change { display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; }
    .old { text-decoration: line-through; color: var(--hg-muted); }
    .new { font-weight: 500; }
  `]
})
export class AuditLogComponent {
  private org = inject(OrgService);

  readonly actions = ACTIONS;
  readonly entities = ENTITIES;
  readonly columns: ColumnDef<AuditLogEntry>[] = [
    { key: 'changedAt', label: 'When', sortable: true, pinned: true },
    { key: 'actor', label: 'Changed by', sortable: true },
    { key: 'action', label: 'Action', sortable: true },
    { key: 'record', label: 'Record' },
    { key: 'change', label: 'Change', pinned: true }
  ];

  rows = signal<AuditLogEntry[]>([]);
  total = signal(0);
  page = signal(0);
  size = signal(20);
  sort = signal('changedAt,desc');
  entity = signal<string | null>(null);
  action = signal<string | null>(null);
  entityId = signal('');
  actor = signal('');
  loading = signal(true);

  constructor() {
    const typed = (s: typeof this.actor) => toObservable(s).pipe(skip(1), debounceTime(350), distinctUntilChanged(), takeUntilDestroyed());
    typed(this.actor).subscribe(() => this.refresh());
    typed(this.entityId).subscribe(() => this.refresh());
    this.reload();
  }

  isEmployee(row: AuditLogEntry): boolean {
    return EMPLOYEE_ENTITIES.has(row.entity);
  }

  tone(action: string): string {
    return 'hg-chip ' + ({ CREATE: 'hg-chip-success', DELETE: 'hg-chip-danger', VIEW_SENSITIVE: 'hg-chip-warn', IMPORT: 'hg-chip-info' }[action]
      ?? 'hg-chip-neutral');
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
    this.org.auditLogs({ entity: this.entity() ?? undefined, entityId: this.entityId(), actor: this.actor(), action: this.action() },
      { page: this.page(), size: this.size(), sort: this.sort() }).subscribe({
      next: (res) => { this.rows.set(res.content); this.total.set(res.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
