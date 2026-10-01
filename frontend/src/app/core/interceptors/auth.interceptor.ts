import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, finalize, map, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

// One refresh shared by every request that hits a 401 while it is in flight. If it fails, all of
// them fail with it (instead of waiting forever) and the user is signed out exactly once.
let refresh$: Observable<string> | null = null;

/**
 * Attaches the Bearer access token and, on a 401, transparently refreshes it once
 * and replays the original request. Auth endpoints themselves are skipped.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthCall = req.url.includes('/auth/login')
    || req.url.includes('/auth/refresh')
    || req.url.includes('/auth/logout');

  const token = auth.accessToken;
  const authReq = token && !isAuthCall
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthCall || !auth.refreshToken) {
        return throwError(() => error);
      }
      if (!refresh$) {
        refresh$ = auth.refresh().pipe(
          map((res) => res.accessToken),
          catchError((err) => {
            auth.logout();
            return throwError(() => err);
          }),
          finalize(() => (refresh$ = null)),
          shareReplay(1)
        );
      }
      return refresh$.pipe(
        switchMap((t) => next(req.clone({ setHeaders: { Authorization: `Bearer ${t}` } }))),
        // A failed refresh means the session is over: surface it as the original 401 so the
        // error interceptor stays quiet while the user is taken back to the login page.
        catchError((err) => throwError(() => (err instanceof HttpErrorResponse && err.url?.includes('/auth/refresh') ? error : err)))
      );
    })
  );
};
