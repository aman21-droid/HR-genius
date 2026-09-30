import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

/**
 * Surfaces backend errors as toast notifications with the server's human-readable message.
 * 401s are handled by the auth interceptor and intentionally not toasted here.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) {
        const message = error.error?.message
          || (error.status === 0 ? 'Cannot reach the server' : 'Something went wrong');
        snackBar.open(message, 'Dismiss', { duration: 5000, panelClass: 'hg-snack-error' });
      }
      return throwError(() => error);
    })
  );
};
