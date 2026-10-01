import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

export interface WorkspacePage { route: string; label: string; icon: string; }

@Component({
  selector: 'hg-workspace-search',
  standalone: true,
  imports: [RouterLink, MatIconModule],
  template: `
    <div class="global-search" (focusout)="!$any($event.currentTarget).contains($any($event.relatedTarget)) && open.set(false)">
      <mat-icon>search</mat-icon>
      <input id="workspace-search" type="search" placeholder="Find a page…" aria-label="Search workspace pages"
        [value]="query()" (input)="query.set($any($event.target).value)" (focus)="open.set(true)"
        (keydown.arrowdown)="focusResult($event, 0)" [attr.aria-controls]="open() ? 'page-search-results' : null" autocomplete="off">
      <kbd>Ctrl K</kbd>
      @if (open()) {
        <div class="search-results" id="page-search-results">
          <span class="hg-eyebrow">Jump to</span>
          @for (page of results(); track page.route; let i = $index) {
            <a [routerLink]="page.route" (click)="open.set(false); selected.emit()" (keydown.arrowdown)="focusResult($event, i + 1)" (keydown.arrowup)="focusResult($event, i - 1)">
              <mat-icon>{{ page.icon }}</mat-icon>{{ page.label }}<mat-icon class="result-arrow">north_east</mat-icon>
            </a>
          } @empty { <p>No pages found. Try “leave” or “employees”.</p> }
        </div>
      }
    </div>
  `,
  styleUrl: './workspace-search.component.scss'
})
export class WorkspaceSearchComponent {
  private element = inject<ElementRef<HTMLElement>>(ElementRef);
  pages = input.required<WorkspacePage[]>();
  selected = output<void>();
  query = signal('');
  open = signal(false);
  results = computed(() => this.pages().filter(p => p.label.toLowerCase().includes(this.query().trim().toLowerCase())).slice(0, 7));

  focusResult(event: Event, index: number): void {
    event.preventDefault();
    const links = this.element.nativeElement.querySelectorAll<HTMLAnchorElement>('.search-results a');
    if (links.length) { links[(index + links.length) % links.length].focus(); }
  }
  @HostListener('document:keydown', ['$event'])
  keyboard(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.element.nativeElement.querySelector('input')?.focus();
      this.open.set(true);
    }
    if (event.key === 'Escape' && this.open()) {
      this.element.nativeElement.querySelector('input')?.focus();
      this.open.set(false);
    }
  }
  @HostListener('document:click', ['$event'])
  outside(event: MouseEvent): void {
    if (!this.element.nativeElement.contains(event.target as Node)) { this.open.set(false); }
  }
}
