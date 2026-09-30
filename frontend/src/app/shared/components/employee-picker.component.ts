import { Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, FormControl, NgControl, ReactiveFormsModule } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { debounceTime, distinctUntilChanged, filter, of, switchMap, tap } from 'rxjs';
import { EmployeeService } from '../../core/services/employee.service';
import { EmployeeLookup } from '../../core/models/employee.models';

/** Minimal shape a picker needs to show a person; full lookups satisfy it too. */
export type PickedEmployee = Pick<EmployeeLookup, 'id' | 'fullName'> & Partial<EmployeeLookup>;

/**
 * Type-ahead employee picker bound to a form control holding a {@link PickedEmployee} (or null).
 * Searches the directory after 2 characters.
 */
@Component({
  selector: 'hg-employee-picker',
  standalone: true,
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  // Default change detection so the error state follows the parent form's touched/invalid state.
  template: `
    <mat-form-field appearance="outline" class="picker">
      <mat-label>{{ label() }}</mat-label>
      <input matInput [formControl]="query" [matAutocomplete]="auto" [placeholder]="placeholder()"
             (blur)="onTouched()" [required]="required()" [errorStateMatcher]="errorMatcher" />
      <mat-autocomplete #auto [displayWith]="display" (optionSelected)="pick($event)">
        @for (e of options(); track e.id) {
          <mat-option [value]="e">{{ e.fullName }} <small class="muted">· {{ e.employeeCode }}{{ e.designation ? ', ' + e.designation : '' }}</small></mat-option>
        }
      </mat-autocomplete>
      @if (hint()) { <mat-hint>{{ hint() }}</mat-hint> }
      <mat-error>{{ typedButNotPicked() ? 'Click a name in the list to select it' : 'Pick a person' }}</mat-error>
    </mat-form-field>
  `,
  styles: `.picker { width: 100%; }`
})
export class EmployeePickerComponent implements ControlValueAccessor {
  private employees = inject(EmployeeService);
  /** The parent form control; we register ourselves as its value accessor to read its validity. */
  private ngControl = inject(NgControl, { self: true, optional: true });

  /** Shows the red error once the parent control is invalid and touched (e.g. after Save). */
  readonly errorMatcher: ErrorStateMatcher = {
    isErrorState: () => !!this.ngControl?.invalid && !!(this.ngControl.touched || this.ngControl.dirty)
  };

  label = input('Employee');
  placeholder = input('Type 2+ letters');
  hint = input('');
  required = input(false);

  query = new FormControl<PickedEmployee | string | null>(null);
  options = signal<EmployeeLookup[]>([]);

  private onChange: (v: PickedEmployee | null) => void = () => {};
  onTouched: () => void = () => {};

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
    this.query.valueChanges.pipe(
      filter((v): v is string => typeof v === 'string'),
      // Typing clears any picked person straight away (before the debounce), so a late
      // keystroke can never wipe a selection made from the list.
      tap(() => this.onChange(null)),
      debounceTime(250), distinctUntilChanged(),
      switchMap((q) => (q.trim().length >= 2 ? this.employees.lookup(q, 8) : of([]))),
      takeUntilDestroyed()
    ).subscribe((list) => this.options.set(list));
  }

  typedButNotPicked(): boolean {
    const v = this.query.value;
    return typeof v === 'string' && v.trim().length > 0;
  }

  display = (e: PickedEmployee | string | null): string =>
    e && typeof e !== 'string' ? (e.employeeCode ? `${e.fullName} (${e.employeeCode})` : e.fullName) : (e ?? '');

  pick(ev: MatAutocompleteSelectedEvent): void {
    this.onChange(ev.option.value as PickedEmployee);
  }

  writeValue(v: PickedEmployee | null): void {
    this.query.setValue(v, { emitEvent: false });
  }

  registerOnChange(fn: (v: PickedEmployee | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    if (disabled) { this.query.disable({ emitEvent: false }); } else { this.query.enable({ emitEvent: false }); }
  }
}

/** Multi-select variant rendered as removable chips; the bound value is a PickedEmployee[]. */
@Component({
  selector: 'hg-employee-multi-picker',
  standalone: true,
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule, MatChipsModule, MatIconModule],
  template: `
    <mat-form-field appearance="outline" class="picker">
      <mat-label>{{ label() }}</mat-label>
      <mat-chip-grid #grid [attr.aria-label]="label()">
        @for (e of selected(); track e.id) {
          <mat-chip-row (removed)="remove(e)">
            {{ e.fullName }}
            <button matChipRemove [attr.aria-label]="'Remove ' + e.fullName"><mat-icon>cancel</mat-icon></button>
          </mat-chip-row>
        }
        <input [formControl]="query" [matAutocomplete]="auto" [matChipInputFor]="grid" placeholder="Type 2+ letters"
               (blur)="onTouched()" />
      </mat-chip-grid>
      <mat-autocomplete #auto (optionSelected)="add($event)">
        @for (e of options(); track e.id) {
          <mat-option [value]="e">{{ e.fullName }} <small class="muted">· {{ e.employeeCode }}{{ e.designation ? ', ' + e.designation : '' }}</small></mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `.picker { width: 100%; }`
})
export class EmployeeMultiPickerComponent implements ControlValueAccessor {
  private employees = inject(EmployeeService);
  private ngControl = inject(NgControl, { self: true, optional: true });

  label = input('People');

  query = new FormControl<string>('');
  options = signal<EmployeeLookup[]>([]);
  selected = signal<PickedEmployee[]>([]);

  private onChange: (v: PickedEmployee[]) => void = () => {};
  onTouched: () => void = () => {};

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
    this.query.valueChanges.pipe(
      filter((v): v is string => typeof v === 'string'),
      debounceTime(250), distinctUntilChanged(),
      switchMap((q) => (q.trim().length >= 2 ? this.employees.lookup(q, 8) : of([]))),
      takeUntilDestroyed()
    ).subscribe((list) => this.options.set(list.filter((e) => !this.selected().some((s) => s.id === e.id))));
  }

  add(ev: MatAutocompleteSelectedEvent): void {
    const e = ev.option.value as PickedEmployee;
    if (!this.selected().some((s) => s.id === e.id)) {
      this.selected.update((list) => [...list, e]);
      this.onChange(this.selected());
    }
    this.query.setValue('', { emitEvent: false });
    this.options.set([]);
  }

  remove(e: PickedEmployee): void {
    this.selected.update((list) => list.filter((s) => s.id !== e.id));
    this.onChange(this.selected());
  }

  writeValue(v: PickedEmployee[] | null): void {
    this.selected.set(v ?? []);
  }

  registerOnChange(fn: (v: PickedEmployee[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
}
