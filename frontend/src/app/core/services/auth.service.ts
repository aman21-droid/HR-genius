import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, TokenResponse, UserSummary } from '../models/auth.models';

const ACCESS_KEY = 'hg_access';
const REFRESH_KEY = 'hg_refresh';
const USER_KEY = 'hg_user';

/**
 * Central auth state via signals. Holds tokens (localStorage) and the current user,
 * and exposes role/permission checks used by guards and templates.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly _user = signal<UserSummary | null>(this.readUser());
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  login(request: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${environment.apiBaseUrl}/auth/login`, request)
      .pipe(tap((res) => this.storeSession(res)));
  }

  refresh(): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${environment.apiBaseUrl}/auth/refresh`, {
      refreshToken: this.refreshToken
    }).pipe(tap((res) => this.storeSession(res)));
  }

  logout(): void {
    const token = this.refreshToken;
    if (token) {
      this.http.post(`${environment.apiBaseUrl}/auth/logout`, { refreshToken: token })
        .subscribe({ error: () => void 0 });
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  hasRole(role: string): boolean {
    return this._user()?.roles.includes(role) ?? false;
  }

  hasPermission(permission: string): boolean {
    return this._user()?.permissions.includes(permission) ?? false;
  }

  hasAnyRole(roles: string[]): boolean {
    return roles.some((r) => this.hasRole(r));
  }

  private storeSession(res: TokenResponse): void {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this._user.set(res.user);
  }

  private clearSession(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
  }

  private readUser(): UserSummary | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  }
}
