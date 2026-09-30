import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

// Shared refresh state so concurrent 401s trigger a single refresh call.
let refreshing = false;
const refreshed$ = new BehaviorSubject<string | null>(null);

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

      if (refreshing) {
        // Queue behind the in-flight refresh, then replay.
        return refreshed$.pipe(
          filter((t) => t !== null),
          take(1),
          switchMap((t) => next(req.clone({ setHeaders: { Authorization: `Bearer ${t}` } })))
        );
      }

      refreshing = true;
      refreshed$.next(null);
      return auth.refresh().pipe(
        switchMap((res) => {
          refreshing = false;
          refreshed$.next(res.accessToken);
          return next(req.clone({ setHeaders: { Authorization: `Bearer ${res.accessToken}` } }));
        }),
        catchError((err) => {
          refreshing = false;
          auth.logout();
          return throwError(() => err);
        })
      );
    })
  );
};
