import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MeterApi } from '../../core/api/api.services';
import { problemMessage } from '../../core/http/interceptors';
import { Meter, MeterRequest } from '../../core/models';

export const SERIAL_PATTERN = /^CEAC-[EW]-\d{6}$/;

/** The serial prefix must match the energy: CEAC-E-... for electricity, CEAC-W-... for water. */
export const serialMatchesType: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const serial = group.get('serial')?.value as string | undefined;
  const type = group.get('type')?.value as string | undefined;
  if (!serial || !SERIAL_PATTERN.test(serial)) {
    return null;
  }
  const expected = type === 'WATER' ? 'W' : 'E';
  return serial.charAt(5) === expected ? null : { serialType: true };
};

@Component({
  selector: 'app-meter-form-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ meter ? 'Modifier le compteur' : 'Nouveau compteur' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <div class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Numéro de série</mat-label>
            <input matInput formControlName="serial" placeholder="CEAC-E-000123" class="mono" />
            @if (form.controls.serial.hasError('pattern')) {
              <mat-error>Format attendu : CEAC-E-000123 ou CEAC-W-000123.</mat-error>
            } @else if (form.controls.serial.hasError('required')) {
              <mat-error>Le numéro de série est obligatoire.</mat-error>
            }
            @if (form.hasError('serialType') && form.controls.serial.valid) {
              <mat-hint class="warn">Le préfixe ne correspond pas au type (E pour électricité, W pour eau).</mat-hint>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Type</mat-label>
            <mat-select formControlName="type">
              <mat-option value="ELECTRICITY">Électricité</mat-option>
              <mat-option value="WATER">Eau</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="wide">
            <mat-label>Client</mat-label>
            <input matInput formControlName="clientName" />
            @if (form.controls.clientName.hasError('required')) {
              <mat-error>Le nom du client est obligatoire.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="wide">
            <mat-label>Adresse</mat-label>
            <input matInput formControlName="address" />
            @if (form.controls.address.hasError('required')) {
              <mat-error>L’adresse est obligatoire.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Latitude</mat-label>
            <input matInput type="number" step="0.000001" formControlName="latitude" class="num" />
            @if (form.controls.latitude.invalid) {
              <mat-error>Entre -90 et 90.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Longitude</mat-label>
            <input matInput type="number" step="0.000001" formControlName="longitude" class="num" />
            @if (form.controls.longitude.invalid) {
              <mat-error>Entre -180 et 180.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Intervalle de relève (min)</mat-label>
            <input matInput type="number" min="1" formControlName="readingIntervalMin" class="num" />
            @if (form.controls.readingIntervalMin.invalid) {
              <mat-error>Entre 1 et 1440 minutes.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Statut</mat-label>
            <mat-select formControlName="status">
              <mat-option value="ACTIVE">Actif</mat-option>
              <mat-option value="INACTIVE">Inactif</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()">
          {{ meter ? 'Enregistrer' : 'Créer le compteur' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px 12px;
      padding-top: 4px;
    }
    .wide { grid-column: 1 / -1; }
    mat-form-field { width: 100%; }
    .warn { color: var(--status-alert); }
    .error {
      padding: 10px 12px;
      border-radius: var(--radius-control);
      background: color-mix(in srgb, var(--sev-critical) 10%, transparent);
      color: color-mix(in srgb, var(--sev-critical) 85%, var(--mat-sys-on-surface));
    }
    @media (max-width: 560px) { .grid { grid-template-columns: 1fr; } }
  `,
})
export class MeterFormDialog {
  protected readonly meter = inject<Meter | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<MeterFormDialog, Meter>);
  private readonly meterApi = inject(MeterApi);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      serial: [this.meter?.serial ?? '', [Validators.required, Validators.pattern(SERIAL_PATTERN)]],
      type: [this.meter?.type ?? 'ELECTRICITY', Validators.required],
      clientName: [this.meter?.clientName ?? '', [Validators.required, Validators.maxLength(120)]],
      address: [this.meter?.address ?? '', [Validators.required, Validators.maxLength(255)]],
      latitude: [this.meter?.latitude ?? 34.0333, [Validators.required, Validators.min(-90), Validators.max(90)]],
      longitude: [this.meter?.longitude ?? -5.0, [Validators.required, Validators.min(-180), Validators.max(180)]],
      readingIntervalMin: [
        this.meter?.readingIntervalMin ?? 15,
        [Validators.required, Validators.min(1), Validators.max(1440)],
      ],
      status: [this.meter?.status ?? 'ACTIVE', Validators.required],
    },
    { validators: serialMatchesType },
  );

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const request = this.form.getRawValue() as MeterRequest;
    const call = this.meter ? this.meterApi.update(this.meter.id, request) : this.meterApi.create(request);
    call.subscribe({
      next: (meter) => this.dialogRef.close(meter),
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }
}
