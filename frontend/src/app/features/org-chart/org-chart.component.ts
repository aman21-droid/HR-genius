import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { EmployeeService } from '../../core/services/employee.service';
import { OrgChartNode } from '../../core/models/employee.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { InitialsPipe } from '../../shared/pipes/labels.pipe';

interface TreeNode {
  node: OrgChartNode;
  children: TreeNode[];
}

const DEFAULT_DEPTH = 2;   // levels expanded on first load
const ZOOM_MIN = 0.4;
const ZOOM_MAX = 1.6;

/**
 * Interactive org chart built from a flat node list. Zoom via buttons or Ctrl+wheel, search
 * to jump to a person (ancestors auto-expand), click a card to open the profile.
 */
@Component({
  selector: 'app-org-chart',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink, FormsModule, MatButtonModule, MatIconModule, MatTooltipModule, MatFormFieldModule,
    MatInputModule, MatAutocompleteModule, PageHeaderComponent, InitialsPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './org-chart.component.html',
  styleUrl: './org-chart.component.scss'
})
export class OrgChartComponent {
  private employees = inject(EmployeeService);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);
  private canvas = viewChild<ElementRef<HTMLElement>>('canvas');

  nodes = signal<OrgChartNode[]>([]);
  loading = signal(true);
  zoom = signal(0.9);
  expanded = signal<Set<number>>(new Set());
  highlighted = signal<number | null>(null);
  query = signal('');

  private byId = computed(() => new Map(this.nodes().map((n) => [n.id, n])));

  roots = computed<TreeNode[]>(() => {
    const ids = this.byId();
    const children = new Map<number, OrgChartNode[]>();
    const roots: OrgChartNode[] = [];
    for (const n of this.nodes()) {
      if (n.managerId != null && ids.has(n.managerId)) {
        const list = children.get(n.managerId) ?? [];
        list.push(n);
        children.set(n.managerId, list);
      } else {
        roots.push(n);
      }
    }
    // Managers first, then alphabetical, so big branches sit together.
    const sort = (a: OrgChartNode, b: OrgChartNode) => b.directReports - a.directReports || a.name.localeCompare(b.name);
    const build = (n: OrgChartNode): TreeNode => ({ node: n, children: (children.get(n.id) ?? []).sort(sort).map(build) });
    return roots.sort(sort).map(build);
  });

  matches = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (q.length < 2) return [];
    return this.nodes().filter((n) => n.name.toLowerCase().includes(q) || n.employeeCode.toLowerCase().includes(q)
      || (n.designation ?? '').toLowerCase().includes(q)).slice(0, 8);
  });

  constructor() {
    this.employees.orgChart().subscribe({
      next: (list) => {
        this.nodes.set(list);
        this.expanded.set(this.initialExpansion());
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  toggle(id: number, event: Event): void {
    event.stopPropagation();
    const next = new Set(this.expanded());
    next.has(id) ? next.delete(id) : next.add(id);
    this.expanded.set(next);
  }

  expandAll(): void {
    this.expanded.set(new Set(this.nodes().filter((n) => n.directReports > 0).map((n) => n.id)));
  }

  collapseAll(): void {
    this.expanded.set(this.initialExpansion());
  }

  zoomBy(delta: number): void {
    this.zoom.set(Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, +(this.zoom() + delta).toFixed(2))));
  }

  onWheel(e: WheelEvent): void {
    if (e.ctrlKey) {
      e.preventDefault();
      this.zoomBy(e.deltaY < 0 ? 0.1 : -0.1);
    }
  }

  open(id: number): void {
    this.router.navigate(['/employees', id]);
  }

  displayNode = (n: OrgChartNode | string | null): string => (n && typeof n !== 'string' ? n.name : (n ?? ''));

  /** Expands the path to a person, highlights them and scrolls them into view. */
  focus(n: OrgChartNode): void {
    const next = new Set(this.expanded());
    for (let cur = n.managerId; cur != null; cur = this.byId().get(cur)?.managerId ?? null) {
      next.add(cur);
    }
    this.expanded.set(next);
    this.highlighted.set(n.id);
    this.query.set('');
    setTimeout(() => {
      const el = this.host.nativeElement.querySelector(`#node-${n.id}`) as HTMLElement | null;
      el?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });
      el?.focus({ preventScroll: true });
    }, 50);
  }

  private initialExpansion(): Set<number> {
    const set = new Set<number>();
    const walk = (list: TreeNode[], depth: number) => {
      for (const t of list) {
        if (depth < DEFAULT_DEPTH && t.children.length) {
          set.add(t.node.id);
          walk(t.children, depth + 1);
        }
      }
    };
    walk(this.roots(), 0);
    return set;
  }
}
