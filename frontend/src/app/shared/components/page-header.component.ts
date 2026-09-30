import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

export interface Breadcrumb {
  label: string;
  link?: string | unknown[];
}

/** Page title with breadcrumbs; action buttons are projected into the right-hand side. */
@Component({
  selector: 'hg-page-header',
  standalone: true,
  imports: [RouterLink, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="hg-page-header">
      <div class="titles">
        @if (breadcrumbs().length) {
          <nav class="crumbs" aria-label="Breadcrumb">
            @for (c of breadcrumbs(); track c.label; let last = $last) {
              @if (c.link && !last) {
                <a [routerLink]="c.link">{{ c.label }}</a>
              } @else {
                <span [attr.aria-current]="last ? 'page' : null">{{ c.label }}</span>
              }
              @if (!last) { <mat-icon aria-hidden="true">chevron_right</mat-icon> }
            }
          </nav>
        }
        <h1>{{ title() }}</h1>
        @if (subtitle()) { <p class="subtitle">{{ subtitle() }}</p> }
      </div>
      <div class="actions"><ng-content /></div>
    </header>
  `
})
export class PageHeaderComponent {
  title = input.required<string>();
  subtitle = input<string>('');
  breadcrumbs = input<Breadcrumb[]>([]);
}
