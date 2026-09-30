import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Sends the session token with every API call. When the server rejects it (expired, or signed with a key
 * that changed on restart), the session is dropped and read requests are replayed as a visitor.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.session()?.token;
  if (!token || !req.url.startsWith('/api/') || req.url.startsWith('/api/auth/')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        auth.expire();
        if (req.method === 'GET') {
          return next(req);
        }
      }
      return throwError(() => err);
    }),
  );
};
