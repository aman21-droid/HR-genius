import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EmployeeService } from '../../../core/services/employee.service';
import { AuthService } from '../../../core/services/auth.service';
import { Statutory, StatutoryRequest } from '../../../core/models/employee.models';

/**
 * Bank & statutory IDs. Masked by default; "Reveal" is audit-logged server-side.
 * The edit form sends only fields the user actually typed into (PATCH), so masked values
 * are never written back and blank sensitive fields mean "keep the current value".
 */
@Component({
  selector: 'app-profile-statutory',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hg-card">
      <div class="head">
        <h3 class="hg-section-title">Bank & statutory details</h3>
        <div class="btns">
          @if (data()?.masked) {
            <button mat-stroked-button (click)="load(true)"><mat-icon>visibility</mat-icon> Reveal</button>
          } @else if (data()) {
            <button mat-stroked-button (click)="load(false)"><mat-icon>visibility_off</mat-icon> Hide</button>
          }
          @if (canEdit && !editing()) {
            <button mat-flat-button color="primary" (click)="startEdit()"><mat-icon>edit</mat-icon> Edit</button>
          }
        </div>
      </div>
      <p class="muted note"><mat-icon inline>shield</mat-icon> Encrypted at rest. Revealing full numbers is recorded in the audit trail.</p>

      @if (editing()) {
        <form [formGroup]="form" (ngSubmit)="save()" class="hg-form-grid">
          <mat-form-field appearance="outline"><mat-label>PAN</mat-label>
            <input matInput formControlName="pan" placeholder="Leave blank to keep" autocomplete="off" />
            @if (form.controls.pan.invalid) { <mat-error>Format: ABCDE1234F</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Aadhaar</mat-label>
            <input matInput formControlName="aadhaar" placeholder="Leave blank to keep" autocomplete="off" />
            @if (form.controls.aadhaar.invalid) { <mat-error>12 digits, not starting with 0 or 1</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>UAN (PF)</mat-label>
            <input matInput formControlName="uan" autocomplete="off" />
            @if (form.controls.uan.invalid) { <mat-error>12 digits</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>ESI number</mat-label>
            <input matInput formControlName="esiNumber" autocomplete="off" />
            @if (form.controls.esiNumber.invalid) { <mat-error>10-17 digits</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Bank name</mat-label><input matInput formControlName="bankName" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Account holder</mat-label><input matInput formControlName="accountHolderName" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Account number</mat-label>
            <input matInput formControlName="bankAccountNumber" placeholder="Leave blank to keep" autocomplete="off" />
            @if (form.controls.bankAccountNumber.invalid) { <mat-error>9-18 digits</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>IFSC</mat-label>
            <input matInput formControlName="bankIfsc" autocomplete="off" />
            @if (form.controls.bankIfsc.invalid) { <mat-error>Format: HDFC0001234</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Tax regime</mat-label>
            <mat-select formControlName="taxRegime">
              <mat-option value="NEW">New regime</mat-option><mat-option value="OLD">Old regime</mat-option>
            </mat-select></mat-form-field>
          <div class="full actions">
            <button mat-button type="button" (click)="editing.set(false)">Cancel</button>
            <button mat-flat-button color="primary" type="submit" [disabled]="saving()">Save</button>
          </div>
        </form>
      } @else {
        @if (data(); as s) {
        <dl class="hg-detail-grid">
          <div><dt>PAN</dt><dd class="mono">{{ s.pan || '—' }}</dd></div>
          <div><dt>Aadhaar</dt><dd class="mono">{{ s.aadhaar || '—' }}</dd></div>
          <div><dt>UAN (PF)</dt><dd class="mono">{{ s.uan || '—' }}</dd></div>
          <div><dt>ESI number</dt><dd class="mono">{{ s.esiNumber || '—' }}</dd></div>
          <div><dt>Bank</dt><dd>{{ s.bankName || '—' }}</dd></div>
          <div><dt>Account holder</dt><dd>{{ s.accountHolderName || '—' }}</dd></div>
          <div><dt>Account number</dt><dd class="mono">{{ s.bankAccountNumber || '—' }}</dd></div>
          <div><dt>IFSC</dt><dd class="mono">{{ s.bankIfsc || '—' }}</dd></div>
          <div><dt>Tax regime</dt><dd>{{ s.taxRegime ? (s.taxRegime === 'NEW' ? 'New regime' : 'Old regime') : '—' }}</dd></div>
        </dl>
        }
      }
    </section>
  `,
  styles: [`
    .head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
    .btns { display: flex; gap: 8px; }
    .note { display: flex; align-items: center; gap: 6px; font-size: 13px; margin: 0 0 16px; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; }
  `]
})
export class ProfileStatutoryComponent implements OnInit {
  employeeId = input.required<number>();

  private employees = inject(EmployeeService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  readonly canEdit = this.auth.hasPermission('EMPLOYEE_SENSITIVE_READ')
    && (this.auth.hasPermission('EMPLOYEE_WRITE') || this.auth.hasPermission('PAYROLL_RUN'));

  data = signal<Statutory | null>(null);
  editing = signal(false);
  saving = signal(false);

  form = this.fb.nonNullable.group({
    pan: ['', Validators.pattern(/^[A-Z]{5}[0-9]{4}[A-Z]$/)],
    aadhaar: ['', Validators.pattern(/^[2-9][0-9]{11}$/)],
    uan: ['', Validators.pattern(/^[0-9]{12}$/)],
    esiNumber: ['', Validators.pattern(/^[0-9]{10,17}$/)],
    bankName: [''],
    accountHolderName: [''],
    bankAccountNumber: ['', Validators.pattern(/^[0-9]{9,18}$/)],
    bankIfsc: ['', Validators.pattern(/^[A-Z]{4}0[A-Z0-9]{6}$/)],
    taxRegime: ['' as '' | 'OLD' | 'NEW']
  });

  ngOnInit(): void {
    this.load(false);
  }

  load(reveal: boolean): void {
    this.employees.statutory(this.employeeId(), reveal).subscribe((s) => this.data.set(s));
  }

  startEdit(): void {
    const s = this.data();
    // Sensitive fields start empty ("leave blank to keep"); non-secret fields are prefilled.
    this.form.reset({
      pan: '', aadhaar: '', bankAccountNumber: '',
      uan: s?.uan ?? '', esiNumber: s?.esiNumber ?? '', bankName: s?.bankName ?? '',
      accountHolderName: s?.accountHolderName ?? '', bankIfsc: s?.bankIfsc ?? '', taxRegime: s?.taxRegime ?? ''
    });
    this.editing.set(true);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const s = this.data();
    const req: StatutoryRequest = {};
    // Only send what changed; blank sensitive fields are omitted (= keep).
    if (v.pan) req.pan = v.pan.toUpperCase();
    if (v.aadhaar) req.aadhaar = v.aadhaar;
    if (v.bankAccountNumber) req.bankAccountNumber = v.bankAccountNumber;
    if (v.uan && v.uan !== s?.uan) req.uan = v.uan;
    if (v.esiNumber && v.esiNumber !== s?.esiNumber) req.esiNumber = v.esiNumber;
    if (v.bankName && v.bankName !== s?.bankName) req.bankName = v.bankName;
    if (v.accountHolderName && v.accountHolderName !== s?.accountHolderName) req.accountHolderName = v.accountHolderName;
    if (v.bankIfsc && v.bankIfsc !== s?.bankIfsc) req.bankIfsc = v.bankIfsc.toUpperCase();
    if (v.taxRegime && v.taxRegime !== s?.taxRegime) req.taxRegime = v.taxRegime;

    if (!Object.keys(req).length) {
      this.editing.set(false);
      return;
    }
    this.saving.set(true);
    this.employees.updateStatutory(this.employeeId(), req).subscribe({
      next: (updated) => {
        this.data.set(updated);
        this.saving.set(false);
        this.editing.set(false);
        this.snack.open('Bank & statutory details updated', undefined, { duration: 2500 });
      },
      error: () => this.saving.set(false)
    });
  }
}
