import { Routes } from '@angular/router';
import { authGuard, permissionGuard, roleGuard } from './core/guards/auth.guard';

/**
 * All feature screens are lazy-loaded standalone components under the authenticated shell.
 * Guards mirror the backend's checks for UX only; the API enforces access regardless.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  // Public careers site: no login, outside the app shell.
  {
    path: 'careers',
    loadComponent: () => import('./features/careers/careers.component').then((m) => m.CareersComponent)
  },
  {
    path: 'careers/:code',
    loadComponent: () => import('./features/careers/careers.component').then((m) => m.CareerJobComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
      },
      {
        path: 'employees',
        loadComponent: () => import('./features/employees/employee-list.component').then((m) => m.EmployeeListComponent)
      },
      {
        path: 'employees/new',
        canActivate: [permissionGuard],
        data: { permissions: ['EMPLOYEE_WRITE'] },
        loadComponent: () => import('./features/employees/employee-form.component').then((m) => m.EmployeeFormComponent)
      },
      {
        path: 'employees/:id',
        loadComponent: () => import('./features/employees/employee-profile.component').then((m) => m.EmployeeProfileComponent)
      },
      {
        path: 'employees/:id/edit',
        canActivate: [permissionGuard],
        data: { permissions: ['EMPLOYEE_WRITE'] },
        loadComponent: () => import('./features/employees/employee-form.component').then((m) => m.EmployeeFormComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/employees/employee-profile.component').then((m) => m.EmployeeProfileComponent)
      },
      {
        path: 'org-chart',
        loadComponent: () => import('./features/org-chart/org-chart.component').then((m) => m.OrgChartComponent)
      },
      {
        path: 'attendance',
        loadComponent: () => import('./features/attendance/attendance.component').then((m) => m.AttendanceComponent)
      },
      {
        path: 'leave',
        loadComponent: () => import('./features/leave/leave.component').then((m) => m.LeaveComponent)
      },
      {
        path: 'leave/config',
        canActivate: [permissionGuard],
        data: { permissions: ['LEAVE_CONFIG'] },
        loadComponent: () => import('./features/leave/leave-config.component').then((m) => m.LeaveConfigComponent)
      },
      {
        path: 'approvals',
        loadComponent: () => import('./features/approvals/approvals.component').then((m) => m.ApprovalsComponent)
      },
      {
        path: 'recruitment',
        canActivate: [permissionGuard],
        data: { permissions: ['RECRUITMENT_MANAGE'] },
        loadComponent: () => import('./features/recruitment/recruitment-shell.component').then((m) => m.RecruitmentShellComponent),
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: () => import('./features/recruitment/requisition-list.component').then((m) => m.RequisitionListComponent)
          },
          {
            path: 'candidates',
            loadComponent: () => import('./features/recruitment/candidate-list.component').then((m) => m.CandidateListComponent)
          }
        ]
      },
      {
        path: 'recruitment/requisitions/:id',
        canActivate: [permissionGuard],
        data: { permissions: ['RECRUITMENT_MANAGE'] },
        loadComponent: () => import('./features/recruitment/pipeline.component').then((m) => m.PipelineComponent)
      },
      {
        path: 'recruitment/applications/:id',
        canActivate: [permissionGuard],
        data: { permissions: ['RECRUITMENT_MANAGE'] },
        loadComponent: () => import('./features/recruitment/application-detail.component').then((m) => m.ApplicationDetailComponent)
      },
      {
        path: 'interviews',
        loadComponent: () => import('./features/interviews/my-interviews.component').then((m) => m.MyInterviewsComponent)
      },
      {
        path: 'onboarding',
        loadComponent: () => import('./features/onboarding/onboarding.component').then((m) => m.OnboardingComponent)
      },
      {
        path: 'onboarding/plans/:id',
        loadComponent: () => import('./features/onboarding/plan-detail.component').then((m) => m.PlanDetailComponent)
      },
      {
        path: 'org/setup',
        canActivate: [permissionGuard],
        data: { permissions: ['ORG_MANAGE'] },
        loadComponent: () => import('./features/org/org-setup.component').then((m) => m.OrgSetupComponent)
      },
      {
        path: 'assets',
        canActivate: [permissionGuard],
        data: { permissions: ['ASSET_MANAGE'] },
        loadComponent: () => import('./features/assets/asset-list.component').then((m) => m.AssetListComponent)
      },
      {
        path: 'documents/expiring',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN', 'HR_ADMIN', 'HR_MANAGER'] },
        loadComponent: () => import('./features/documents/expiring-documents.component').then((m) => m.ExpiringDocumentsComponent)
      },
      {
        path: 'admin/audit-log',
        canActivate: [permissionGuard],
        data: { permissions: ['AUDIT_VIEW'] },
        loadComponent: () => import('./features/admin/audit-log.component').then((m) => m.AuditLogComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
