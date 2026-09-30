import {
  ChangeDetectionStrategy, Component, Directive, TemplateRef, computed, contentChildren, effect, inject, input,
  output, signal
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort, SortDirection } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatMenuModule } from '@angular/material/menu';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EmptyStateComponent } from './empty-state.component';

export interface ColumnDef<T> {
  key: string;
  label: string;
  sortable?: boolean;
  /** Backend sort property if different from key (e.g. 'department.name'). */
  sortKey?: string;
  /** Plain-text cell value when no <ng-template hgCell> is supplied. */
  value?: (row: T) => string | number | null | undefined;
  /** Hidden until the user enables it in the column chooser. */
  hiddenByDefault?: boolean;
  /** Cannot be hidden (e.g. the name column). */
  pinned?: boolean;
  align?: 'start' | 'end';
}

/** Custom cell: `<ng-template hgCell="status" let-row>...</ng-template>` */
@Directive({ selector: 'ng-template[hgCell]', standalone: true })
export class CellDefDirective {
  name = input.required<string>({ alias: 'hgCell' });
  template = inject<TemplateRef<{ $implicit: unknown }>>(TemplateRef);
}

/**
 * Server-driven table used by every list screen: sort/page events go to the parent, which
 * refetches. Column visibility is remembered per table in localStorage.
 */
@Component({
  selector: 'hg-data-table',
  standalone: true,
  imports: [
    NgTemplateOutlet, MatTableModule, MatSortModule, MatPaginatorModule, MatMenuModule, MatCheckboxModule,
    MatButtonModule, MatIconModule, MatTooltipModule, EmptyStateComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-table-card">
      <div class="hg-table-toolbar">
        <span class="count">
          @if (!loading() || rows().length) { {{ total() }} {{ total() === 1 ? 'record' : 'records' }} }
        </span>
        <button mat-icon-button [matMenuTriggerFor]="colMenu" matTooltip="Choose columns" aria-label="Choose columns">
          <mat-icon>view_column</mat-icon>
        </button>
        <mat-menu #colMenu="matMenu">
          @for (c of columns(); track c.key) {
            @if (!c.pinned) {
              <div mat-menu-item (click)="$event.stopPropagation()">
                <mat-checkbox [checked]="visible().has(c.key)" (change)="toggle(c.key)">{{ c.label }}</mat-checkbox>
              </div>
            }
          }
        </mat-menu>
      </div>

      <div class="hg-table-scroll">
        <table mat-table [dataSource]="rows()" matSort [matSortActive]="sortActive()" [matSortDirection]="sortDirection()"
               (matSortChange)="onSort($event)" [trackBy]="trackBy">
          @for (c of columns(); track c.key) {
            <ng-container [matColumnDef]="c.key">
              <th mat-header-cell *matHeaderCellDef [mat-sort-header]="c.sortKey ?? c.key" [disabled]="!c.sortable"
                  [class.align-end]="c.align === 'end'">{{ c.label }}</th>
              <td mat-cell *matCellDef="let row" [class.align-end]="c.align === 'end'">
                @if (templates().get(c.key); as tpl) {
                  <ng-container *ngTemplateOutlet="tpl; context: { $implicit: row }" />
                } @else {
                  {{ cellText(c, row) }}
                }
              </td>
            </ng-container>
          }
          <tr mat-header-row *matHeaderRowDef="displayed(); sticky: true"></tr>
          <tr mat-row *matRowDef="let row; columns: displayed()" [class.clickable]="clickable()"
              [attr.tabindex]="clickable() ? 0 : null" (click)="clickable() && rowClick.emit(row)"
              (keydown.enter)="clickable() && rowClick.emit(row)"></tr>
        </table>

        @if (loading() && !rows().length) {
          <div class="hg-skeleton-rows" aria-busy="true" aria-label="Loading">
            @for (i of skeleton; track i) { <div class="hg-skeleton"></div> }
          </div>
        }
        @if (!loading() && !rows().length) {
          <hg-empty-state [icon]="emptyIcon()" [title]="emptyTitle()" [message]="emptyMessage()">
            <ng-content select="[empty]" />
          </hg-empty-state>
        }
      </div>

      <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                     [pageSizeOptions]="[10, 20, 50, 100]" (page)="pageChange.emit($event)" showFirstLastButtons
                     aria-label="Select page" />
    </div>
  `
})
export class DataTableComponent<T extends object> {
  columns = input.required<ColumnDef<T>[]>();
  rows = input<T[]>([]);
  total = input(0);
  pageIndex = input(0);
  pageSize = input(20);
  loading = input(false);
  sortActive = input('');
  sortDirection = input<SortDirection>('asc');
  /** localStorage key for remembered column choices; omit to not persist. */
  storageKey = input<string>('');
  clickable = input(false);
  emptyTitle = input('Nothing here yet');
  emptyMessage = input('');
  emptyIcon = input('inbox');

  pageChange = output<PageEvent>();
  /** Emits "key,asc" / "key,desc", or '' when sorting is cleared. */
  sortChange = output<string>();
  rowClick = output<T>();

  private cellDefs = contentChildren(CellDefDirective);
  templates = computed(() => new Map(this.cellDefs().map((d) => [d.name(), d.template])));

  visible = signal<Set<string>>(new Set());
  displayed = computed(() => this.columns().filter((c) => c.pinned || this.visible().has(c.key)).map((c) => c.key));
  readonly skeleton = [1, 2, 3, 4, 5, 6];

  constructor() {
    // Initialise visibility once columns are known: stored choice, else column defaults.
    effect(() => {
      const cols = this.columns();
      const stored = this.readStored();
      const keys = stored ?? cols.filter((c) => !c.hiddenByDefault).map((c) => c.key);
      this.visible.set(new Set(keys));
    }, { allowSignalWrites: true });
  }

  trackBy = (_: number, row: T) => (row as { id?: unknown }).id ?? row;

  cellText(c: ColumnDef<T>, row: T): string | number {
    const v = c.value ? c.value(row) : (row as Record<string, unknown>)[c.key];
    return v === null || v === undefined || v === '' ? '—' : (v as string | number);
  }

  toggle(key: string): void {
    const next = new Set(this.visible());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.visible.set(next);
    if (this.storageKey()) {
      localStorage.setItem(`hg_cols_${this.storageKey()}`, JSON.stringify([...next]));
    }
  }

  onSort(sort: Sort): void {
    this.sortChange.emit(sort.direction ? `${sort.active},${sort.direction}` : '');
  }

  private readStored(): string[] | null {
    if (!this.storageKey()) {
      return null;
    }
    try {
      const raw = localStorage.getItem(`hg_cols_${this.storageKey()}`);
      return raw ? (JSON.parse(raw) as string[]) : null;
    } catch {
      return null;
    }
  }
}
