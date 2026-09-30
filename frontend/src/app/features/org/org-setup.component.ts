import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { OrgService } from '../../core/services/org.service';
import { AuthService } from '../../core/services/auth.service';
import { MASTER_TYPES } from '../../core/models/org.models';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { MasterTableComponent } from './master-table.component';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];

/** Company profile and org masters, one tab each. */
@Component({
  selector: 'app-org-setup',
  standalone: true,
  imports: [ReactiveFormsModule, MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, PageHeaderComponent, MasterTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hg-page">
      <hg-page-header title="Organization setup" subtitle="Company profile, departments, designations, grades and locations"
                      [breadcrumbs]="[{ label: 'Home', link: '/dashboard' }, { label: 'Organization setup' }]" />
      <mat-tab-group animationDuration="150ms" mat-stretch-tabs="false">
        <mat-tab label="Company">
          <div class="hg-card tab-pad">
            <form [formGroup]="company" (ngSubmit)="saveCompany()" class="hg-form-grid">
              <mat-form-field appearance="outline"><mat-label>Display name</mat-label><input matInput formControlName="name" required />
                @if (company.controls.name.invalid) { <mat-error>Name is required</mat-error> }</mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Legal name</mat-label><input matInput formControlName="legalName" /></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Registration no.</mat-label><input matInput formControlName="registrationNo" /></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Website</mat-label><input matInput formControlName="website" /></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Country</mat-label><input matInput formControlName="country" /></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Currency</mat-label><input matInput formControlName="currency" placeholder="INR" />
                @if (company.controls.currency.invalid) { <mat-error>3-letter code, e.g. INR</mat-error> }</mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Financial year starts</mat-label>
                <mat-select formControlName="fyStartMonth">
                  @for (m of months; track m; let i = $index) { <mat-option [value]="i + 1">{{ m }}</mat-option> }
                </mat-select></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Employee code prefix</mat-label>
                <input matInput formControlName="employeeCodePrefix" placeholder="EMP" />
                <mat-hint>New codes look like {{ company.value.employeeCodePrefix || 'EMP' }}0053</mat-hint>
                @if (company.controls.employeeCodePrefix.invalid) { <mat-error>1-10 capital letters</mat-error> }</mat-form-field>
              @if (canEdit) {
                <div class="full actions">
                  <button mat-flat-button color="primary" type="submit" [disabled]="saving() || company.pristine">Save</button>
                </div>
              }
            </form>
          </div>
        </mat-tab>
        @for (t of types; track t.type) {
          <mat-tab [label]="t.label">
            <ng-template matTabContent>
              <div class="tab-pad"><app-master-table [info]="t" [canEdit]="canEdit" /></div>
            </ng-template>
          </mat-tab>
        }
      </mat-tab-group>
    </div>
  `,
  styles: [`.tab-pad { margin-top: 16px; } .actions { display: flex; justify-content: flex-end; }`]
})
export class OrgSetupComponent {
  private org = inject(OrgService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  readonly types = MASTER_TYPES;
  readonly months = MONTHS;
  readonly canEdit = this.auth.hasPermission('ORG_MANAGE');
  saving = signal(false);

  company = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    legalName: [''],
    registrationNo: [''],
    website: [''],
    country: [''],
    currency: ['', Validators.pattern(/^[A-Z]{3}$/)],
    fyStartMonth: [4 as number | null],
    employeeCodePrefix: ['EMP', Validators.pattern(/^[A-Z]{1,10}$/)]
  });

  constructor() {
    this.org.company().subscribe((c) => {
      this.company.reset({
        name: c.name, legalName: c.legalName ?? '', registrationNo: c.registrationNo ?? '', website: c.website ?? '',
        country: c.country ?? '', currency: c.currency ?? '', fyStartMonth: c.fyStartMonth, employeeCodePrefix: c.employeeCodePrefix ?? ''
      });
      if (!this.canEdit) this.company.disable();
    });
  }

  saveCompany(): void {
    if (this.company.invalid) {
      this.company.markAllAsTouched();
      return;
    }
    const v = this.company.getRawValue();
    this.saving.set(true);
    this.org.updateCompany({
      name: v.name!, legalName: v.legalName || null, registrationNo: v.registrationNo || null, website: v.website || null,
      country: v.country || null, currency: v.currency || null, fyStartMonth: v.fyStartMonth ?? null,
      employeeCodePrefix: v.employeeCodePrefix || null
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.company.markAsPristine();
        this.snack.open('Company profile saved', undefined, { duration: 2500 });
      },
      error: () => this.saving.set(false)
    });
  }
}
