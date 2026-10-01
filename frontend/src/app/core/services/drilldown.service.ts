import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { take } from 'rxjs';
import { OrgService } from './org.service';
import { LookupItem } from '../models/common.models';

/**
 * Chart drill-downs: charts show names, the employee directory filters by id. Resolves the name
 * against the organisation lookups and opens the directory pre-filtered. A bar that matches no
 * master (e.g. "Unassigned") opens nothing rather than a misleading unfiltered list.
 */
@Injectable({ providedIn: 'root' })
export class DrilldownService {
  private router = inject(Router);
  private org = inject(OrgService);

  openDepartment(name: string): void {
    this.open(name, (l) => l.departments, 'departmentId');
  }

  openLocation(name: string): void {
    this.open(name, (l) => l.locations, 'locationId');
  }

  openPayrollRun(id: number): void {
    this.router.navigate(['/payroll/runs', id]);
  }

  private open(name: string, pick: (l: { departments: LookupItem[]; locations: LookupItem[] }) => LookupItem[], param: string): void {
    this.org.lookups().pipe(take(1)).subscribe((l) => {
      const match = pick(l).find((x) => x.name.toLowerCase() === name.toLowerCase());
      if (match) {
        this.router.navigate(['/employees'], { queryParams: { [param]: match.id } });
      }
    });
  }
}
