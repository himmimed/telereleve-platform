import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { RuleApi, UserApi } from '../../core/api/api.services';
import { AuthStore } from '../../core/auth/auth.store';
import { ROLE_LABEL, RULE_DESCRIPTION, RULE_LABEL, RULE_SEVERITY } from '../../core/labels';
import { RuleCode, RuleConfig, User } from '../../core/models';
import { ConfirmDialog, ConfirmData } from '../../shared/confirm.dialog';
import { Badge, EmptyState, PageHeader, SeverityBadge, Skeleton } from '../../shared/ui';
import { UserFormDialog } from './user-form.dialog';

/** Which settings each rule uses, and what they mean (labels shown in the form). */
export const RULE_FIELDS: Record<RuleCode, { threshold?: { label: string; suffix: string }; window?: { label: string } }> = {
  A1: { window: { label: 'Fenêtre sans consommation' } },
  A2: { threshold: { label: 'Multiplicateur', suffix: '×' }, window: { label: 'Historique de référence' } },
  A3: { threshold: { label: 'Multiple de l’intervalle', suffix: '×' } },
  A4: {},
  A5: { threshold: { label: 'Tolérance autour de 230 V', suffix: '%' } },
};

type RuleForm = FormGroup<{
  enabled: FormControl<boolean>;
  threshold: FormControl<number | null>;
  windowHours: FormControl<number | null>;
}>;

type Loaded<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; value: T };

@Component({
  selector: 'app-admin-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatTabsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatTooltipModule,
    PageHeader,
    Skeleton,
    EmptyState,
    Badge,
    SeverityBadge,
  ],
  templateUrl: './admin.page.html',
  styleUrl: './admin.page.scss',
})
export class AdminPage {
  private readonly userApi = inject(UserApi);
  private readonly ruleApi = inject(RuleApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly auth = inject(AuthStore);

  protected readonly roleLabel = ROLE_LABEL;
  protected readonly ruleLabel = RULE_LABEL;
  protected readonly ruleDescription = RULE_DESCRIPTION;
  protected readonly ruleSeverity = RULE_SEVERITY;
  protected readonly ruleFields = RULE_FIELDS;
  protected readonly userColumns = ['name', 'username', 'email', 'role', 'enabled', 'actions'];

  // ------------------------------------------------------------------ users

  private readonly reloadUsers$ = new Subject<void>();
  private readonly usersState = toSignal(
    this.reloadUsers$.pipe(
      startWith(undefined),
      switchMap(() =>
        this.userApi.list().pipe(
          map((value): Loaded<User[]> => ({ status: 'ready', value })),
          catchError(() => of<Loaded<User[]>>({ status: 'error' })),
          startWith<Loaded<User[]>>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as Loaded<User[]> },
  );
  protected readonly users = linkedSignal<Loaded<User[]>, User[] | null>({
    source: this.usersState,
    computation: (state, previous) => (state.status === 'ready' ? state.value : (previous?.value ?? null)),
  });
  protected readonly usersFailed = computed(() => this.usersState().status === 'error');

  protected openUser(user: User | null): void {
    this.dialog
      .open(UserFormDialog, { data: user, width: '560px' })
      .afterClosed()
      .subscribe((saved?: User) => {
        if (saved) {
          this.snackBar.open(user ? 'Utilisateur mis à jour.' : `Utilisateur ${saved.username} créé.`, 'OK');
          this.reloadUsers$.next();
        }
      });
  }

  protected deleteUser(user: User): void {
    const data: ConfirmData = {
      title: 'Supprimer l’utilisateur ?',
      message: `Le compte de ${user.fullName} sera supprimé définitivement. Ses alertes assignées deviendront non assignées.`,
      confirmLabel: 'Supprimer',
      destructive: true,
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (confirmed) {
          this.userApi.delete(user.id).subscribe(() => {
            this.snackBar.open('Utilisateur supprimé.', 'OK');
            this.reloadUsers$.next();
          });
        }
      });
  }

  protected roleName(user: User): string {
    return ROLE_LABEL[user.role];
  }

  protected isSelf(user: User): boolean {
    return user.username === this.auth.username();
  }

  // ------------------------------------------------------------------ rules

  protected readonly savingRule = signal<RuleCode | null>(null);
  protected readonly rules = toSignal(
    this.ruleApi.list().pipe(
      map((rules): Loaded<{ rule: RuleConfig; form: RuleForm }[]> => ({
        status: 'ready',
        value: rules.map((rule) => ({ rule, form: this.ruleForm(rule) })),
      })),
      catchError(() => of<Loaded<{ rule: RuleConfig; form: RuleForm }[]>>({ status: 'error' })),
    ),
    { initialValue: { status: 'loading' } as Loaded<{ rule: RuleConfig; form: RuleForm }[]> },
  );

  protected readonly ruleRows = computed(() => {
    const state = this.rules();
    return state.status === 'ready' ? state.value : null;
  });

  private ruleForm(rule: RuleConfig): RuleForm {
    const fields = RULE_FIELDS[rule.ruleCode];
    return new FormGroup({
      enabled: new FormControl(rule.enabled, { nonNullable: true }),
      threshold: new FormControl(rule.threshold, fields.threshold ? [Validators.required, Validators.min(0.1)] : []),
      windowHours: new FormControl(rule.windowHours, fields.window ? [Validators.required, Validators.min(1), Validators.max(720)] : []),
    });
  }

  protected saveRule(code: RuleCode, form: RuleForm): void {
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    this.savingRule.set(code);
    const value = form.getRawValue();
    this.ruleApi.update(code, { ruleCode: code, ...value }).subscribe({
      next: (saved) => {
        this.savingRule.set(null);
        form.reset({ enabled: saved.enabled, threshold: saved.threshold, windowHours: saved.windowHours });
        this.snackBar.open(`Règle ${code} enregistrée.`, 'OK');
      },
      error: () => this.savingRule.set(null),
    });
  }
}
