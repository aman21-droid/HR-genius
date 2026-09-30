import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';

/** Tabbed frame for the recruiter's two list views; each tab is its own lazy child route. */
@Component({
  selector: 'app-recruitment-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatTabsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav mat-tab-nav-bar [tabPanel]="panel" class="rec-tabs" aria-label="Recruitment sections">
      <a mat-tab-link routerLink="/recruitment" routerLinkActive #r1="routerLinkActive"
         [routerLinkActiveOptions]="{ exact: true }" [active]="r1.isActive">Requisitions</a>
      <a mat-tab-link routerLink="/recruitment/candidates" routerLinkActive #r2="routerLinkActive"
         [active]="r2.isActive">Candidates</a>
    </nav>
    <mat-tab-nav-panel #panel><router-outlet /></mat-tab-nav-panel>
  `,
  styles: `.rec-tabs { max-width: 1320px; margin: 0 auto; padding: 0 24px; }`
})
export class RecruitmentShellComponent {}
