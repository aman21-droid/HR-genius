import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EmployeeService } from '../../../core/services/employee.service';
import { AuthService } from '../../../core/services/auth.service';
import { DOCUMENT_CATEGORIES, EmployeeDetail, EmployeeDocument } from '../../../core/models/employee.models';
import { saveBlob, toIsoDate } from '../../../core/utils/http.utils';
import { ConfirmService } from '../../../shared/components/confirm-dialog.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state.component';
import { FileSizePipe, HumanizePipe } from '../../../shared/pipes/labels.pipe';

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPT = '.pdf,.png,.jpg,.jpeg,.docx';

@Component({
  selector: 'app-profile-documents',
  standalone: true,
  imports: [
    DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatTooltipModule, MatProgressBarModule, EmptyStateComponent,
    FileSizePipe, HumanizePipe
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hg-card">
      <div class="head">
        <h3 class="hg-section-title">Documents</h3>
        @if (canUpload()) {
          <button mat-stroked-button (click)="showForm.set(!showForm())">
            <mat-icon>{{ showForm() ? 'close' : 'upload' }}</mat-icon> {{ showForm() ? 'Cancel' : 'Upload' }}
          </button>
        }
      </div>

      @if (showForm()) {
        <form [formGroup]="form" (ngSubmit)="upload()" class="hg-form-grid upload">
          <mat-form-field appearance="outline">
            <mat-label>Category</mat-label>
            <mat-select formControlName="category" required>
              @for (c of categories; track c) { <mat-option [value]="c">{{ c | humanize }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Title</mat-label>
            <input matInput formControlName="title" required placeholder="e.g. Passport" />
            @if (form.controls.title.hasError('required')) { <mat-error>Give the document a title</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Expiry date</mat-label>
            <input matInput [matDatepicker]="exp" formControlName="expiryDate" />
            <mat-datepicker-toggle matIconSuffix [for]="exp" />
            <mat-datepicker #exp />
            <mat-hint>For visas, contracts, certifications</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Notes</mat-label>
            <input matInput formControlName="notes" />
          </mat-form-field>
          <div class="full file-row">
            <button mat-stroked-button type="button" (click)="picker.click()"><mat-icon>attach_file</mat-icon> Choose file</button>
            <input #picker type="file" hidden [accept]="accept" (change)="pick(picker.files); picker.value = ''" />
            <span class="muted">{{ file()?.name ?? 'PDF, PNG, JPEG or DOCX, up to 10 MB' }}</span>
            @if (fileError()) { <span class="err" role="alert">{{ fileError() }}</span> }
            <span class="spacer"></span>
            <button mat-flat-button color="primary" type="submit" [disabled]="uploading()">
              {{ uploading() ? 'Uploading…' : 'Upload' }}
            </button>
          </div>
          @if (uploading()) { <mat-progress-bar class="full" mode="indeterminate" /> }
        </form>
      }

      @if (docs().length) {
        <ul class="doc-list">
          @for (d of docs(); track d.id) {
            <li>
              <mat-icon class="type" aria-hidden="true">{{ d.contentType.startsWith('image/') ? 'image' : 'description' }}</mat-icon>
              <div class="info">
                <strong>{{ d.title }}</strong>
                <small class="muted">{{ d.category | humanize }} · {{ d.fileName }} · {{ d.sizeBytes | fileSize }}
                  · uploaded {{ d.uploadedAt | date: 'mediumDate' }}</small>
              </div>
              @if (d.expiryDate) {
                <span class="hg-chip" [class]="expiryClass(d)" [matTooltip]="'Expires ' + (d.expiryDate | date: 'mediumDate')">
                  {{ expiryLabel(d) }}
                </span>
              }
              <span class="hg-chip" [class]="d.verified ? 'hg-chip hg-chip-success' : 'hg-chip hg-chip-warn'">
                {{ d.verified ? 'Verified' : 'Pending review' }}
              </span>
              <button mat-icon-button (click)="download(d)" matTooltip="Download" [attr.aria-label]="'Download ' + d.title">
                <mat-icon>download</mat-icon></button>
              @if (canManage) {
                <button mat-icon-button (click)="toggleVerified(d)" [matTooltip]="d.verified ? 'Mark unverified' : 'Mark verified'"
                        [attr.aria-label]="d.verified ? 'Mark unverified' : 'Mark verified'">
                  <mat-icon>{{ d.verified ? 'remove_done' : 'done_all' }}</mat-icon></button>
                <button mat-icon-button (click)="remove(d)" matTooltip="Delete" [attr.aria-label]="'Delete ' + d.title">
                  <mat-icon>delete</mat-icon></button>
              }
            </li>
          }
        </ul>
      } @else if (!loading()) {
        <hg-empty-state icon="folder_open" title="No documents yet"
                        [message]="canUpload() ? 'Upload ID proofs, certificates, contracts and more.' : ''" />
      }
    </section>
  `,
  styles: [`
    .head { display: flex; justify-content: space-between; align-items: center; }
    .upload { margin: 8px 0 16px; padding: 16px; border: 1px dashed var(--hg-border); border-radius: 10px; }
    .file-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .err { color: #b91c1c; font-size: 13px; }
    .doc-list { list-style: none; margin: 0; padding: 0; }
    .doc-list li { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--hg-border); flex-wrap: wrap; }
    .doc-list li:last-child { border-bottom: none; }
    .info { flex: 1 1 260px; display: flex; flex-direction: column; }
    .type { color: var(--hg-muted); }
  `]
})
export class ProfileDocumentsComponent implements OnInit {
  employee = input.required<EmployeeDetail>();

  private employees = inject(EmployeeService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private confirm = inject(ConfirmService);

  readonly categories = DOCUMENT_CATEGORIES;
  readonly accept = ACCEPT;
  readonly canManage = this.auth.hasPermission('EMPLOYEE_WRITE');

  docs = signal<EmployeeDocument[]>([]);
  loading = signal(true);
  showForm = signal(false);
  uploading = signal(false);
  file = signal<File | null>(null);
  fileError = signal('');
  canUpload = computed(() => this.canManage || this.auth.user()?.employeeId === this.employee().id);

  form = this.fb.group({
    category: ['ID_PROOF', Validators.required],
    title: ['', [Validators.required, Validators.maxLength(160)]],
    expiryDate: [null as Date | null],
    notes: ['', Validators.maxLength(500)]
  });

  ngOnInit(): void {
    this.reload();
  }

  pick(files: FileList | null): void {
    const f = files?.item(0) ?? null;
    this.fileError.set('');
    if (f && f.size > MAX_BYTES) {
      this.fileError.set('That file is larger than 10 MB');
      this.file.set(null);
      return;
    }
    this.file.set(f);
    if (f && !this.form.value.title) {
      this.form.patchValue({ title: f.name.replace(/\.[^.]+$/, '') });
    }
  }

  upload(): void {
    const f = this.file();
    if (this.form.invalid || !f) {
      this.form.markAllAsTouched();
      if (!f) this.fileError.set('Choose a file');
      return;
    }
    const v = this.form.getRawValue();
    this.uploading.set(true);
    this.employees.uploadDocument(this.employee().id, f, {
      category: v.category!, title: v.title!, expiryDate: toIsoDate(v.expiryDate), notes: v.notes || null
    }).subscribe({
      next: () => {
        this.uploading.set(false);
        this.showForm.set(false);
        this.file.set(null);
        this.form.reset({ category: 'ID_PROOF' });
        this.snack.open('Document uploaded', undefined, { duration: 2500 });
        this.reload();
      },
      error: () => this.uploading.set(false)
    });
  }

  download(d: EmployeeDocument): void {
    this.employees.downloadDocument(d.id).subscribe((res) => saveBlob(res, d.fileName));
  }

  toggleVerified(d: EmployeeDocument): void {
    this.employees.verifyDocument(d.id, !d.verified).subscribe((updated) =>
      this.docs.update((list) => list.map((x) => (x.id === updated.id ? updated : x))));
  }

  remove(d: EmployeeDocument): void {
    this.confirm.ask({ title: `Delete "${d.title}"?`, message: 'The file will be removed from the vault.', confirmText: 'Delete', danger: true })
      .subscribe((ok) => ok && this.employees.deleteDocument(d.id).subscribe(() => {
        this.docs.update((list) => list.filter((x) => x.id !== d.id));
        this.snack.open('Document deleted', undefined, { duration: 2500 });
      }));
  }

  expiryLabel(d: EmployeeDocument): string {
    const n = d.daysToExpiry ?? 0;
    if (n < 0) return `Expired ${-n}d ago`;
    if (n === 0) return 'Expires today';
    if (n <= 30) return `Expires in ${n}d`;
    return 'Valid';
  }

  expiryClass(d: EmployeeDocument): string {
    const n = d.daysToExpiry ?? 0;
    return 'hg-chip ' + (n < 0 ? 'hg-chip-danger' : n <= 30 ? 'hg-chip-warn' : 'hg-chip-neutral');
  }

  private reload(): void {
    this.loading.set(true);
    this.employees.documents(this.employee().id).subscribe({
      next: (list) => { this.docs.set(list); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
