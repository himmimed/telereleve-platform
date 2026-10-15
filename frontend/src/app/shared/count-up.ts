import { Directive, ElementRef, effect, inject, input, OnDestroy } from '@angular/core';
import { formatNumber } from './format';
import { prefersReducedMotion } from './motion';

/**
 * Shows a number in French format and, when it changes, counts from the previous value to the new one
 * (from 0 on first display). The movement says "this figure was just updated", which matters on a
 * dashboard refreshed every 30 s. Written straight to the element, outside change detection.
 * Under reduced motion the value is set at once.
 */
@Directive({
  selector: '[appCountUp]',
})
export class CountUp implements OnDestroy {
  readonly appCountUp = input.required<number>();
  readonly decimals = input(0);

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private shown = 0;
  private frame = 0;

  constructor() {
    effect(() => this.animateTo(this.appCountUp(), this.decimals()));
  }

  private animateTo(target: number, decimals: number): void {
    cancelAnimationFrame(this.frame);
    const from = this.shown;
    if (prefersReducedMotion() || from === target) {
      this.render(target, decimals);
      return;
    }
    const duration = 900;
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      // Ease-out expo: fast at first, settling softly on the final value.
      const eased = t === 1 ? 1 : 1 - Math.pow(2, -10 * t);
      this.render(from + (target - from) * eased, decimals);
      if (t < 1) {
        this.frame = requestAnimationFrame(step);
      } else {
        this.render(target, decimals);
      }
    };
    this.frame = requestAnimationFrame(step);
  }

  private render(value: number, decimals: number): void {
    this.shown = value;
    this.element.textContent = formatNumber(value, decimals, decimals);
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.frame);
  }
}
