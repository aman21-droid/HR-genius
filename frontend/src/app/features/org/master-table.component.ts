import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { debounceTime, distinctUntilChanged, skip } from 'rxjs';
import { OrgService } from '../../core/services/org.service';
import { Master, MasterTypeInfo } from '../../core/models/org.models';
import { CellDefDirective, ColumnDef, DataTableComponent } from '../../shared/components/data-table.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { MasterDialogComponent } from './master-dialog.component';

/** One org master list (departments, grades, ...) with add/edit/delete. */
@Component({
  selector: 'app-master-table',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatMenuModule,
    DataTableComponent, CellDefDirective, StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-filters">
      <mat-form-field appearance="outline" class="search">
        <mat-icon matPrefix>search</mat-icon>
        <input matInput [placeholder]="'Search ' + info().label.toLowerCase()" [ngModel]="search()"
               (ngModelChange)="search.set($event)" [attr.aria-label]="'Search ' + info().label" />
      </mat-form-field>
      <span class="spacer"></span>
      @if (canEdit()) {
        <button mat-flat-button color="primary" (click)="edit(null)"><mat-icon>add</mat-icon> Add {{ info().singular.toLowerCase() }}</button>
      }
    </div>
    <hg-data-table [columns]="columns()" [rows]="rows()" [total]="total()" [pageIndex]="page()" [pageSize]="size()"
                   [loading]="loading()" sortActive="name" [storageKey]="'master-' + info().type"
                   [emptyIcon]="info().icon" [emptyTitle]="'No ' + info().label.toLowerCase() + ' yet'"
                   (pageChange)="onPage($event)" (sortChange)="sort.set($event || 'name,asc'); reload()">
      <ng-template hgCell="active" let-row><hg-status [value]="row.active ? 'ACTIVE' : 'INACTIVE'" /></ng-template>
      <ng-template hgCell="actions" let-row>
        @if (canEdit()) {
          <button mat-icon-button [matMenuTriggerFor]="menu" [attr.aria-label]="'Actions for ' + row.name"><mat-icon>more_vert</mat-icon></button>
          <mat-menu #menu="matMenu">
            <button mat-menu-item (click)="edit(row)"><mat-icon>edit</mat-icon><span>Edit</span></button>
            <button mat-menu-item (click)="remove(row)"><mat-icon>delete</mat-icon><span>Delete</span></button>
          </mat-menu>
        }
      </ng-template>
    </hg-data-table>
  `
})
export class MasterTableComponent implements OnInit {
  info = input.required<MasterTypeInfo>();
  canEdit = input(false);

  private org = inject(OrgService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);
  private destroyRef = inject(DestroyRef);

  rows = signal<Master[]>([]);
  total = signal(0);
  page = signal(0);
  size = signal(20);
  sort = signal('name,asc');
  search = signal('');
  loading = signal(true);

  columns = computed<ColumnDef<Master>[]>(() => {
    const t = this.info().type;
    const cols: ColumnDef<Master>[] = [
      { key: 'code', label: 'Code', sortable: true, pinned: true },
      { key: 'name', label: 'Name', sortable: true, pinned: true }
    ];
    if (t === 'departments') {
      cols.push({ key: 'businessUnitName', label: 'Business unit' }, { key: 'costCenterName', label: 'Cost center' },
        { key: 'headEmployeeName', label: 'Head' }, { key: 'parentName', label: 'Parent', hiddenByDefault: true });
    }
    if (t === 'grades') cols.push({ key: 'levelNo', label: 'Level', sortable: true });
    if (t === 'locations') cols.push({ key: 'city', label: 'City', sortable: true }, { key: 'country', label: 'Country' },
      { key: 'timezone', label: 'Time zone', hiddenByDefault: true });
    cols.push({ key: 'employeeCount', label: 'Employees', align: 'end' }, { key: 'active', label: 'Status' },
      { key: 'actions', label: '', pinned: true, align: 'end' });
    return cols;
  });

  constructor() {
    // skip(1): the initial empty value is covered by ngOnInit's first load.
    toObservable(this.search).pipe(skip(1), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => { this.page.set(0); this.reload(); });
  }

  ngOnInit(): void {
    this.reload();
  }

  onPage(e: PageEvent): void {
    this.page.set(e.pageIndex);
    this.size.set(e.pageSize);
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.org.masters(this.info().type, this.search(), { page: this.page(), size: this.size(), sort: this.sort() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => { this.rows.set(res.content); this.total.set(res.totalElements); this.loading.set(false); },
        error: () => this.loading.set(false)
      });
  }

  edit(master: Master | null): void {
    this.dialog.open(MasterDialogComponent, { data: { info: this.info(), master }, width: '640px', maxWidth: '95vw' })
      .afterClosed().subscribe((saved) => {
        if (saved) {
          this.org.refreshLookups();
          this.snack.open(`${this.info().singular} saved`, undefined, { duration: 2500 });
          this.reload();
        }
      });
  }

  remove(master: Master): void {
    this.confirm.ask({
      title: `Delete ${master.name}?`,
      message: master.employeeCount
        ? `${master.employeeCount} employee(s) are assigned to it, so it can't be deleted until they're moved. You can mark it inactive instead.`
        : 'It will no longer be available in forms.',
      confirmText: 'Delete', danger: true
    }).subscribe((ok) => ok && this.org.deleteMaster(this.info().type, master.id).subscribe(() => {
      this.org.refreshLookups();
      this.snack.open(`${master.name} deleted`, undefined, { duration: 2500 });
      this.reload();
    }));
  }
}
