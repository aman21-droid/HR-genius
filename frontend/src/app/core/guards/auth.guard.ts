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
