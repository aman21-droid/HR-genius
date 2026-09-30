import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { EmployeeService } from '../../core/services/employee.service';
import { ImportReport } from '../../core/models/employee.models';
import { saveBlob } from '../../core/utils/http.utils';

/**
 * Two-step bulk import: validate (dry run) shows every problem by row/column; the import
 * button is enabled only for a clean file. The backend is all-or-nothing either way.
 */
@Component({
  selector: 'app-import-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Import employees from Excel</h2>
    <mat-dialog-content>
      @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
      <ol class="steps">
        <li>
          <button mat-button color="primary" (click)="downloadTemplate()"><mat-icon>download</mat-icon> Download the template</button>
          and fill one employee per row.
        </li>
        <li>
          Choose the file and validate it. Nothing is saved until every row is valid.
          <div class="drop" [class.over]="dragOver()" (dragover)="$event.preventDefault(); dragOver.set(true)"
               (dragleave)="dragOver.set(false)" (drop)="onDrop($event)">
            <mat-icon>upload_file</mat-icon>
            @if (file()) { <strong>{{ file()!.name }}</strong> } @else { <span>Drag an .xlsx file here, or</span> }
            <button mat-stroked-button (click)="picker.click()">{{ file() ? 'Choose another' : 'Browse' }}</button>
            <input #picker type="file" hidden accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                   (change)="onPick(picker.files); picker.value = ''" />
          </div>
        </li>
      </ol>

      @if (report(); as r) {
        @if (r.created > 0) {
          <div class="result ok" role="status"><mat-icon>check_circle</mat-icon>
            Imported {{ r.created }} employee{{ r.created === 1 ? '' : 's' }}.</div>
        } @else if (!r.errors.length) {
          <div class="result ok" role="status"><mat-icon>task_alt</mat-icon>
            All {{ r.totalRows }} row{{ r.totalRows === 1 ? '' : 's' }} are valid and ready to import.</div>
        } @else {
          <div class="result bad" role="alert"><mat-icon>error</mat-icon>
            {{ r.errors.length }} problem{{ r.errors.length === 1 ? '' : 's' }} found in
            {{ r.totalRows - r.validRows }} of {{ r.totalRows }} rows. Fix them in the file and validate again.</div>
          <div class="errors">
            <table mat-table [dataSource]="r.errors">
              <ng-container matColumnDef="row"><th mat-header-cell *matHeaderCellDef>Row</th><td mat-cell *matCellDef="let e">{{ e.row }}</td></ng-container>
              <ng-container matColumnDef="column"><th mat-header-cell *matHeaderCellDef>Column</th><td mat-cell *matCellDef="let e">{{ e.column }}</td></ng-container>
              <ng-container matColumnDef="message"><th mat-header-cell *matHeaderCellDef>Problem</th><td mat-cell *matCellDef="let e">{{ e.message }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="cols; sticky: true"></tr>
              <tr mat-row *matRowDef="let row; columns: cols"></tr>
            </table>
          </div>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="close()">{{ report()?.created ? 'Done' : 'Cancel' }}</button>
      @if (!report()?.created) {
        <button mat-stroked-button (click)="run(true)" [disabled]="!file() || busy()">Validate</button>
        <button mat-flat-button color="primary" (click)="run(false)"
                [disabled]="!file() || busy() || !validated()">Import</button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .steps { padding-left: 20px; margin: 8px 0; line-height: 1.6; }
    .steps li { margin-bottom: 12px; }
    .drop { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 8px; padding: 16px;
            border: 2px dashed var(--hg-border); border-radius: 10px; }
    .drop.over { border-color: var(--hg-primary); background: color-mix(in srgb, var(--hg-primary) 6%, transparent); }
    .result { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: 8px; margin: 8px 0; }
    .result.ok { background: #dcfce7; color: #166534; }
    .result.bad { background: #fee2e2; color: #991b1b; }
    .errors { max-height: 260px; overflow: auto; border: 1px solid var(--hg-border); border-radius: 8px; }
    .errors table { width: 100%; }
  `]
})
export class ImportDialogComponent {
  private employees = inject(EmployeeService);
  private ref = inject(MatDialogRef<ImportDialogComponent, number>);

  readonly cols = ['row', 'column', 'message'];
  file = signal<File | null>(null);
  report = signal<ImportReport | null>(null);
  busy = signal(false);
  dragOver = signal(false);
  /** Import is enabled only after a clean dry run of the current file. */
  validated = signal(false);

  downloadTemplate(): void {
    this.employees.importTemplate().subscribe((res) => saveBlob(res, 'employee-import-template.xlsx'));
  }

  onPick(files: FileList | null): void {
    this.setFile(files?.item(0) ?? null);
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.dragOver.set(false);
    this.setFile(e.dataTransfer?.files.item(0) ?? null);
  }

  run(dryRun: boolean): void {
    const f = this.file();
    if (!f) return;
    this.busy.set(true);
    this.employees.import(f, dryRun).subscribe({
      next: (r) => {
        this.report.set(r);
        this.validated.set(dryRun && r.errors.length === 0);
        this.busy.set(false);
      },
      error: () => this.busy.set(false)
    });
  }

  close(): void {
    this.ref.close(this.report()?.created ?? 0);
  }

  private setFile(f: File | null): void {
    this.file.set(f);
    this.report.set(null);
    this.validated.set(false);
  }
}
