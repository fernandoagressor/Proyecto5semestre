import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';

type Theme = 'light' | 'dark';
const THEME_KEY = 'nexora_core_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Theme>('light');

  readonly theme = this.current.asReadonly();
  readonly isDark = computed(() => this.current() === 'dark');

  initialize(): void {
    const saved = localStorage.getItem(THEME_KEY) as Theme | null;
    const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
    this.apply(saved ?? (systemDark ? 'dark' : 'light'));
  }

  toggle(): void {
    this.apply(this.isDark() ? 'light' : 'dark');
  }

  private apply(theme: Theme): void {
    this.current.set(theme);
    this.document.documentElement.dataset['theme'] = theme;
    localStorage.setItem(THEME_KEY, theme);
  }
}
