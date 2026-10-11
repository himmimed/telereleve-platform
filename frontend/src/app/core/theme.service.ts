import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'telereleve.theme';

/** Light / dark mode. Follows the OS by default; the user's choice is remembered on this browser. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  // matchMedia is missing in some environments (jsdom in unit tests): fall back to light.
  private readonly media =
    typeof this.document.defaultView?.matchMedia === 'function'
      ? this.document.defaultView.matchMedia('(prefers-color-scheme: dark)')
      : undefined;
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly mode = signal<ThemeMode>(readStoredMode());
  readonly isDark = computed(() =>
    this.mode() === 'system' ? this.systemDark() : this.mode() === 'dark',
  );

  constructor() {
    this.media?.addEventListener('change', (event) => this.systemDark.set(event.matches));
    effect(() => {
      const mode = this.mode();
      const root = this.document.documentElement;
      if (mode === 'system') {
        root.removeAttribute('data-theme');
      } else {
        root.setAttribute('data-theme', mode);
      }
      try {
        localStorage.setItem(STORAGE_KEY, mode);
      } catch {
        // Storage unavailable (private mode): the choice simply is not remembered.
      }
    });
  }

  toggle(): void {
    this.mode.set(this.isDark() ? 'light' : 'dark');
  }
}

function readStoredMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

/**
 * Resolves a CSS colour (including var() and light-dark()) to an rgb() string for canvas-based
 * libraries such as Chart.js, which cannot read CSS variables themselves.
 */
export function resolveColor(css: string, host: HTMLElement = document.body): string {
  const probe = document.createElement('span');
  probe.style.color = css;
  probe.style.display = 'none';
  host.appendChild(probe);
  const color = getComputedStyle(probe).color;
  probe.remove();
  return color;
}
