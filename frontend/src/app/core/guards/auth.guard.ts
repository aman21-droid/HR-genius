import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Blocks unauthenticated access; redirects to /login preserving the intended URL. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};

/** Permission guard: route data { permissions: ['EMPLOYEE_WRITE'] } (any one suffices). */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data?.['permissions'] as string[]) ?? [];
  if (required.length === 0 || required.some((p) => auth.hasPermission(p))) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};

/** Role guard factory: use in route data as { roles: ['HR_ADMIN', ...] }. */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data?.['roles'] as string[]) ?? [];
  if (required.length === 0 || auth.hasAnyRole(required)) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
