import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
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
import { Observable, debounceTime, distinctUntilChanged, skip } from 'rxjs';
import { OrgService } from '../../core/services/org.service';
import { ASSET_CATEGORIES, ASSET_STATUSES, Asset, AssetCategory, AssetStatus } from '../../core/models/org.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { CellDefDirective, ColumnDef, DataTableComponent } from '../../shared/components/data-table.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { ConfirmService } from '../../shared/components/confirm-dialog.component';
import { HumanizePipe } from '../../shared/pipes/labels.pipe';
import {
  AssetAssignDialogComponent, AssetFormDialogComponent, AssetHistoryDialogComponent, AssetReturnDialogComponent
} from './asset-dialogs.component';

@Component({
  selector: 'app-asset-list',
  standalone: true,
  imports: [RouterLink, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatMenuModule, PageHeaderComponent, DataTableComponent, CellDefDirective, StatusChipComponent, HumanizePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Assets" subtitle="Laptops, ID cards and other company equipment"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Assets' }]">
        <button mat-flat-button color="primary" (click)="edit(null)"><mat-icon>add</mat-icon> Add asset</button>
      </hg-page-header>

      <div class="hg-filters" role="search">
        <mat-form-field appearance="outline" class="search">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search tag, name or serial" [ngModel]="search()" (ngModelChange)="search.set($event)" aria-label="Search assets" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-select placeholder="Category" [ngModel]="category()" (ngModelChange)="category.set($event); refresh()" aria-label="Category">
            <mat-option [value]="null">All categories</mat-option>
            @for (c of categories; track c) { <mat-option [value]="c">{{ c | humanize }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-select placeholder="Status" [ngModel]="status()" (ngModelChange)="status.set($event); refresh()" aria-label="Status">
            <mat-option [value]="null">Any status</mat-option>
            @for (s of statuses; track s) { <mat-option [value]="s">{{ s | humanize }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>

      <hg-data-table [columns]="columns" [rows]="rows()" [total]="total()" [pageIndex]="page()" [pageSize]="size()"
                     [loading]="loading()" sortActive="assetTag" storageKey="assets" emptyIcon="devices_other"
                     emptyTitle="No assets found" (pageChange)="onPage($event)"
                     (sortChange)="sort.set($event || 'assetTag,asc'); reload()">
        <ng-template hgCell="asset" let-row>
          <div class="hg-person"><span class="meta"><strong>{{ row.assetTag }}</strong><small>{{ row.name }}</small></span></div>
        </ng-template>
        <ng-template hgCell="holder" let-row>
          @if (row.currentEmployeeId) {
            <a [routerLink]="['/employees', row.currentEmployeeId]">{{ row.currentEmployeeName }}</a>
          } @else { <span class="muted">—</span> }
        </ng-template>
        <ng-template hgCell="status" let-row><hg-status [value]="row.status" /></ng-template>
        <ng-template hgCell="actions" let-row>
          <button mat-icon-button [matMenuTriggerFor]="menu" [attr.aria-label]="'Actions for ' + row.assetTag"><mat-icon>more_vert</mat-icon></button>
          <mat-menu #menu="matMenu">
            @if (row.status === 'AVAILABLE') {
              <button mat-menu-item (click)="assign(row)"><mat-icon>person_add</mat-icon><span>Assign</span></button>
            }
            @if (row.status === 'ASSIGNED') {
              <button mat-menu-item (click)="returnAsset(row)"><mat-icon>assignment_return</mat-icon><span>Return</span></button>
            }
            <button mat-menu-item (click)="edit(row)"><mat-icon>edit</mat-icon><span>Edit</span></button>
            <button mat-menu-item (click)="history(row)"><mat-icon>history</mat-icon><span>History</span></button>
            @if (row.status !== 'ASSIGNED') {
              <button mat-menu-item (click)="remove(row)"><mat-icon>delete</mat-icon><span>Delete</span></button>
            }
          </mat-menu>
        </ng-template>
      </hg-data-table>
    </div>
  `
})
export class AssetListComponent {
  private org = inject(OrgService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  readonly categories = ASSET_CATEGORIES;
  readonly statuses = ASSET_STATUSES;
  readonly columns: ColumnDef<Asset>[] = [
    { key: 'asset', label: 'Asset', sortable: true, sortKey: 'assetTag', pinned: true },
    { key: 'category', label: 'Category', sortable: true, value: (r) => r.category.replace('_', ' ').toLowerCase() },
    { key: 'serialNumber', label: 'Serial', hiddenByDefault: true },
    { key: 'holder', label: 'Assigned to' },
    { key: 'purchaseDate', label: 'Purchased', sortable: true, hiddenByDefault: true },
    { key: 'status', label: 'Status', sortable: true },
    { key: 'actions', label: '', pinned: true, align: 'end' }
  ];

  rows = signal<Asset[]>([]);
  total = signal(0);
  page = signal(0);
  size = signal(20);
  sort = signal('assetTag,asc');
  search = signal('');
  category = signal<AssetCategory | null>(null);
  status = signal<AssetStatus | null>(null);
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
    this.org.assets({ search: this.search(), category: this.category(), status: this.status() },
      { page: this.page(), size: this.size(), sort: this.sort() }).subscribe({
      next: (res) => { this.rows.set(res.content); this.total.set(res.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  edit(asset: Asset | null): void {
    this.afterSave(this.dialog.open(AssetFormDialogComponent, { data: asset, width: '640px', maxWidth: '95vw' }).afterClosed(), 'Asset saved');
  }

  assign(asset: Asset): void {
    this.afterSave(this.dialog.open(AssetAssignDialogComponent, { data: asset, width: '520px', maxWidth: '95vw' }).afterClosed(),
      `${asset.assetTag} assigned`);
  }

  returnAsset(asset: Asset): void {
    this.afterSave(this.dialog.open(AssetReturnDialogComponent, { data: asset, width: '520px', maxWidth: '95vw' }).afterClosed(),
      `${asset.assetTag} returned`);
  }

  history(asset: Asset): void {
    this.dialog.open(AssetHistoryDialogComponent, { data: asset, width: '520px', maxWidth: '95vw' });
  }

  remove(asset: Asset): void {
    this.confirm.ask({ title: `Delete ${asset.assetTag}?`, message: 'The asset is removed from the register; its history is kept.', confirmText: 'Delete', danger: true })
      .subscribe((ok) => ok && this.org.deleteAsset(asset.id).subscribe(() => {
        this.snack.open(`${asset.assetTag} deleted`, undefined, { duration: 2500 });
        this.reload();
      }));
  }

  private afterSave(closed: Observable<boolean | undefined>, message: string): void {
    closed.subscribe((ok) => {
      if (ok) {
        this.snack.open(message, undefined, { duration: 2500 });
        this.reload();
      }
    });
  }
}
