import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { OrgService } from '../../core/services/org.service';
import { Master, MasterRequest, MasterTypeInfo } from '../../core/models/org.models';
import { ApiError } from '../../core/models/common.models';

export interface MasterDialogData {
  info: MasterTypeInfo;
  master: Master | null;
}

/** Create/edit any org master; type-specific fields appear only for that type. */
@Component({
  selector: 'app-master-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.master ? 'Edit' : 'Add' }} {{ data.info.singular.toLowerCase() }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="hg-form-grid" id="masterForm" (ngSubmit)="save()">
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" required cdkFocusInitial />
          @if (form.controls.code.hasError('required')) { <mat-error>Code is required</mat-error> }
          @else if (form.controls.code.hasError('pattern')) { <mat-error>Letters, digits, - or _ (max 30)</mat-error> }
          @else if (form.controls.code.hasError('server')) { <mat-error>{{ form.controls.code.getError('server') }}</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('required')) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <input matInput formControlName="description" />
        </mat-form-field>

        @if (type === 'departments') {
          <mat-form-field appearance="outline">
            <mat-label>Business unit</mat-label>
            <mat-select formControlName="businessUnitId">
              <mat-option [value]="null">—</mat-option>
              @for (b of lookups()?.businessUnits ?? []; track b.id) { <mat-option [value]="b.id">{{ b.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Cost center</mat-label>
            <mat-select formControlName="costCenterId">
              <mat-option [value]="null">—</mat-option>
              @for (c of lookups()?.costCenters ?? []; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Parent department</mat-label>
            <mat-select formControlName="parentId">
              <mat-option [value]="null">— (top level)</mat-option>
              @for (d of lookups()?.departments ?? []; track d.id) {
                @if (d.id !== data.master?.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }
              }
            </mat-select>
          </mat-form-field>
        }
        @if (type === 'grades') {
          <mat-form-field appearance="outline">
            <mat-label>Level (1 = most junior)</mat-label>
            <input matInput type="number" min="1" max="99" formControlName="levelNo" />
          </mat-form-field>
        }
        @if (type === 'locations') {
          <mat-form-field appearance="outline" class="full"><mat-label>Address</mat-label><input matInput formControlName="addressLine" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>City</mat-label><input matInput formControlName="city" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>State</mat-label><input matInput formControlName="state" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Country</mat-label><input matInput formControlName="country" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Postal code</mat-label><input matInput formControlName="postalCode" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Time zone</mat-label>
            <input matInput formControlName="timezone" placeholder="Asia/Kolkata" /></mat-form-field>
        }
        <mat-slide-toggle formControlName="active" class="full">Active (available in forms)</mat-slide-toggle>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="masterForm" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `
})
export class MasterDialogComponent {
  data = inject<MasterDialogData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<MasterDialogComponent, boolean>);
  private org = inject(OrgService);
  private fb = inject(FormBuilder);

  readonly type = this.data.info.type;
  lookups = toSignal(this.org.lookups());
  saving = signal(false);

  form = this.fb.group({
    code: [this.data.master?.code ?? '', [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]{1,30}$/)]],
    name: [this.data.master?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    description: [this.data.master?.description ?? ''],
    active: [this.data.master?.active ?? true],
    businessUnitId: [this.data.master?.businessUnitId ?? null as number | null],
    costCenterId: [this.data.master?.costCenterId ?? null as number | null],
    parentId: [this.data.master?.parentId ?? null as number | null],
    levelNo: [this.data.master?.levelNo ?? null as number | null, [Validators.min(1), Validators.max(99)]],
    addressLine: [this.data.master?.addressLine ?? ''],
    city: [this.data.master?.city ?? ''],
    state: [this.data.master?.state ?? ''],
    country: [this.data.master?.country ?? ''],
    postalCode: [this.data.master?.postalCode ?? ''],
    timezone: [this.data.master?.timezone ?? '']
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: MasterRequest = {
      ...v,
      code: v.code!.trim().toUpperCase(),
      name: v.name!.trim(),
      active: !!v.active,
      // Keep the department head chosen elsewhere (not editable here yet).
      headEmployeeId: this.data.master?.headEmployeeId ?? null
    };
    this.saving.set(true);
    const call = this.data.master
      ? this.org.updateMaster(this.type, this.data.master.id, req)
      : this.org.createMaster(this.type, req);
    call.subscribe({
      next: () => this.ref.close(true),
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        const body = err.error as ApiError | undefined;
        if (err.status === 409 && body?.message?.toLowerCase().includes('code')) {
          this.form.controls.code.setErrors({ server: body.message });
        }
      }
    });
  }
}
