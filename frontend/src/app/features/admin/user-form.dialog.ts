import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { UserApi } from '../../core/api/api.services';
import { problemMessage } from '../../core/http/interceptors';
import { ROLE_LABEL } from '../../core/labels';
import { Role, User, UserRequest } from '../../core/models';

@Component({
  selector: 'app-user-form-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ user ? 'Modifier l’utilisateur' : 'Nouvel utilisateur' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <div class="grid">
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Nom complet</mat-label>
            <input matInput formControlName="fullName" autocomplete="off" />
            @if (form.controls.fullName.hasError('required')) {
              <mat-error>Le nom est obligatoire.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Identifiant</mat-label>
            <input matInput formControlName="username" autocomplete="off" autocapitalize="off" spellcheck="false" />
            @if (form.controls.username.hasError('pattern')) {
              <mat-error>3 à 50 caractères : lettres minuscules, chiffres, point, tiret.</mat-error>
            } @else if (form.controls.username.hasError('required')) {
              <mat-error>L’identifiant est obligatoire.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Rôle</mat-label>
            <mat-select formControlName="role">
              @for (role of roles; track role) {
                <mat-option [value]="role">{{ roleLabel[role] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="wide">
            <mat-label>Adresse e-mail</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="off" />
            <mat-hint>Utilisée pour les notifications d’alertes critiques.</mat-hint>
            @if (form.controls.email.invalid) {
              <mat-error>Adresse e-mail invalide.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="wide">
            <mat-label>{{ user ? 'Nouveau mot de passe' : 'Mot de passe' }}</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            <mat-hint>{{ user ? 'Laisser vide pour conserver le mot de passe actuel.' : 'Au moins 8 caractères.' }}</mat-hint>
            @if (form.controls.password.invalid) {
              <mat-error>Au moins 8 caractères.</mat-error>
            }
          </mat-form-field>

          <mat-slide-toggle formControlName="enabled" class="wide">Compte actif</mat-slide-toggle>
        </div>

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ user ? 'Enregistrer' : 'Créer l’utilisateur' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; padding-top: 4px; }
    .wide { grid-column: 1 / -1; }
    mat-form-field { width: 100%; }
    mat-slide-toggle { margin: 8px 0 4px; }
    .error {
      margin-top: 12px;
      padding: 10px 12px;
      border-radius: var(--radius-control);
      background: color-mix(in srgb, var(--sev-critical) 10%, transparent);
      color: color-mix(in srgb, var(--sev-critical) 85%, var(--mat-sys-on-surface));
    }
    @media (max-width: 560px) { .grid { grid-template-columns: 1fr; } }
  `,
})
export class UserFormDialog {
  protected readonly user = inject<User | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<UserFormDialog, User>);
  private readonly userApi = inject(UserApi);

  protected readonly roles: Role[] = ['ADMIN', 'OPERATOR', 'TECHNICIAN'];
  protected readonly roleLabel = ROLE_LABEL;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: [this.user?.fullName ?? '', [Validators.required, Validators.maxLength(120)]],
    username: [this.user?.username ?? '', [Validators.required, Validators.pattern(/^[a-z0-9.-]{3,50}$/)]],
    email: [this.user?.email ?? '', [Validators.required, Validators.email]],
    role: [this.user?.role ?? ('OPERATOR' as Role), Validators.required],
    enabled: [this.user?.enabled ?? true],
    password: ['', this.user ? [Validators.minLength(8)] : [Validators.required, Validators.minLength(8)]],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const { password, ...rest } = this.form.getRawValue();
    const request: UserRequest = { ...rest, password: password || null };
    const call = this.user ? this.userApi.update(this.user.id, request) : this.userApi.create(request);
    call.subscribe({
      next: (user) => this.dialogRef.close(user),
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }
}
