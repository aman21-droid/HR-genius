import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EmployeeService } from '../../../core/services/employee.service';
import { EmergencyContact } from '../../../core/models/employee.models';
import { ConfirmService } from '../../../shared/components/confirm-dialog.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';

/** Emergency contacts; editable by HR and by the employee themselves. */
@Component({
  selector: 'app-profile-contacts',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatCheckboxModule,
    MatTooltipModule, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hg-card">
      <div class="head">
        <h3 class="hg-section-title">Emergency contacts</h3>
        @if (editable() && editingId() === undefined && contacts().length < 5) {
          <button mat-stroked-button (click)="startEdit(null)"><mat-icon>add</mat-icon> Add contact</button>
        }
      </div>

      @if (editingId() !== undefined) {
        <form [formGroup]="form" (ngSubmit)="save()" class="hg-form-grid edit">
          <mat-form-field appearance="outline"><mat-label>Name</mat-label><input matInput formControlName="name" required />
            @if (form.controls.name.invalid) { <mat-error>Name is required</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Relationship</mat-label>
            <input matInput formControlName="relationship" placeholder="Spouse, Mother…" required />
            @if (form.controls.relationship.invalid) { <mat-error>Relationship is required</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Phone</mat-label><input matInput type="tel" formControlName="phone" required />
            @if (form.controls.phone.hasError('required')) { <mat-error>Phone is required</mat-error> }
            @else if (form.controls.phone.hasError('pattern')) { <mat-error>Enter a valid phone number</mat-error> }</mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Email</mat-label><input matInput type="email" formControlName="email" />
            @if (form.controls.email.invalid) { <mat-error>Enter a valid email</mat-error> }</mat-form-field>
          <mat-checkbox formControlName="primary">Primary contact</mat-checkbox>
          <div class="full actions">
            <button mat-button type="button" (click)="editingId.set(undefined)">Cancel</button>
            <button mat-flat-button color="primary" type="submit" [disabled]="saving()">Save</button>
          </div>
        </form>
      }

      <div class="grid">
        @for (c of contacts(); track c.id) {
          <div class="contact">
            <div class="top">
              <strong>{{ c.name }}</strong>
              @if (c.primary) { <span class="hg-chip hg-chip-info">Primary</span> }
            </div>
            <span class="muted">{{ c.relationship }}</span>
            <a [href]="'tel:' + c.phone">{{ c.phone }}</a>
            @if (c.email) { <a [href]="'mailto:' + c.email">{{ c.email }}</a> }
            @if (editable()) {
              <div class="row-actions">
                <button mat-icon-button (click)="startEdit(c)" matTooltip="Edit" [attr.aria-label]="'Edit ' + c.name"><mat-icon>edit</mat-icon></button>
                <button mat-icon-button (click)="remove(c)" matTooltip="Remove" [attr.aria-label]="'Remove ' + c.name"><mat-icon>delete</mat-icon></button>
              </div>
            }
          </div>
        } @empty {
          @if (!loading() && editingId() === undefined) {
            <hg-empty-state icon="contact_emergency" title="No emergency contacts"
                            [message]="editable() ? 'Add someone we can reach in an emergency.' : ''" />
          }
        }
      </div>
    </section>
  `,
  styles: [`
    .head { display: flex; justify-content: space-between; align-items: center; }
    .edit { margin-bottom: 16px; padding: 16px; border: 1px dashed var(--hg-border); border-radius: 10px; align-items: center; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
    .contact { display: flex; flex-direction: column; gap: 2px; padding: 14px; border: 1px solid var(--hg-border); border-radius: 10px; }
    .contact a { color: var(--hg-primary); text-decoration: none; }
    .top { display: flex; align-items: center; gap: 8px; }
    .row-actions { display: flex; justify-content: flex-end; }
  `]
})
export class ProfileContactsComponent implements OnInit {
  employeeId = input.required<number>();
  editable = input(false);

  private employees = inject(EmployeeService);
  private fb = inject(FormBuilder);
  private confirm = inject(ConfirmService);

  contacts = signal<EmergencyContact[]>([]);
  loading = signal(true);
  saving = signal(false);
  /** undefined = not editing, null = adding, number = editing that contact. */
  editingId = signal<number | null | undefined>(undefined);

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    relationship: ['', [Validators.required, Validators.maxLength(40)]],
    phone: ['', [Validators.required, Validators.pattern(/^[+0-9 ()-]{7,30}$/)]],
    email: ['', Validators.email],
    primary: [false]
  });

  ngOnInit(): void {
    this.reload();
  }

  startEdit(c: EmergencyContact | null): void {
    this.editingId.set(c?.id ?? null);
    this.form.reset(c ? { name: c.name, relationship: c.relationship, phone: c.phone, email: c.email ?? '', primary: c.primary }
      : { primary: this.contacts().length === 0 });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req = { ...v, email: v.email || null };
    const id = this.editingId();
    this.saving.set(true);
    const call = id ? this.employees.updateContact(this.employeeId(), id, req) : this.employees.addContact(this.employeeId(), req);
    call.subscribe({
      next: () => { this.saving.set(false); this.editingId.set(undefined); this.reload(); },
      error: () => this.saving.set(false)
    });
  }

  remove(c: EmergencyContact): void {
    this.confirm.ask({ title: `Remove ${c.name}?`, message: 'They will no longer be listed as an emergency contact.', confirmText: 'Remove', danger: true })
      .subscribe((ok) => ok && this.employees.deleteContact(this.employeeId(), c.id).subscribe(() => this.reload()));
  }

  private reload(): void {
    this.employees.contacts(this.employeeId()).subscribe({
      next: (list) => { this.contacts.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
