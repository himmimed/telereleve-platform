import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/theme.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  constructor() {
    // Phosphor icons through <mat-icon fontSet="ph" fontIcon="ph-...">.
    inject(MatIconRegistry).registerFontClassAlias('ph', 'ph');
    // Instantiated here so the stored light / dark preference applies before the first page renders.
    inject(ThemeService);
  }
}
