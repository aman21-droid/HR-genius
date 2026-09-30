import { Injectable, signal } from '@angular/core';

const THEME_KEY = 'hg_theme';
type Theme = 'light' | 'dark';

/** Persists the light/dark preference per user and toggles the body class. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<Theme>(this.read());
  readonly theme = this._theme.asReadonly();

  constructor() {
    this.apply(this._theme());
  }

  toggle(): void {
    const next: Theme = this._theme() === 'dark' ? 'light' : 'dark';
    this._theme.set(next);
    localStorage.setItem(THEME_KEY, next);
    this.apply(next);
  }

  private apply(theme: Theme): void {
    document.body.classList.toggle('dark-theme', theme === 'dark');
  }

  private read(): Theme {
    const stored = localStorage.getItem(THEME_KEY) as Theme | null;
    if (stored) {
      return stored;
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
