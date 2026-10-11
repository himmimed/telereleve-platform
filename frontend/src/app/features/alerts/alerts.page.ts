import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, map, of, startWith, switchMap } from 'rxjs';
import { AlertApi, UserApi } from '../../core/api/api.services';
import { AuthStore } from '../../core/auth/auth.store';
import { ALERT_STATUS_LABEL, RULE_LABEL, SEVERITY_LABEL } from '../../core/labels';
import { Alert, AlertStatus, AlertUpdate, Page, RuleCode, Severity, UserSummary } from '../../core/models';
import { DateTimePipe, RelativeTimePipe } from '../../shared/format';
import { AlertStatusBadge, EmptyState, MeterTypeLabel, PageHeader, SeverityBadge, Skeleton } from '../../shared/ui';
import { allowedTransitions } from './alert-rules';

type LoadState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; page: Page<Alert> };

@Component({
  selector: 'app-alerts-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    PageHeader,
    Skeleton,
    EmptyState,
    SeverityBadge,
    AlertStatusBadge,
    MeterTypeLabel,
    RelativeTimePipe,
    DateTimePipe,
  ],
  templateUrl: './alerts.page.html',
  styleUrl: './alerts.page.scss',
})
export class AlertsPage {
  /** Optional rule filter from the URL (?regle=A4), e.g. when coming from the dashboard. */
  readonly regle = input<RuleCode>();

  private readonly alertApi = inject(AlertApi);
  private readonly userApi = inject(UserApi);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly auth = inject(AuthStore);

  protected readonly ruleLabel = RULE_LABEL;
  protected readonly ruleCodes = Object.keys(RULE_LABEL) as RuleCode[];
  protected readonly severityLabel = SEVERITY_LABEL;
  protected readonly severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM'];
  protected readonly statusLabel = ALERT_STATUS_LABEL;
  protected readonly statuses: AlertStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED'];
  protected readonly columns = ['severity', 'rule', 'meter', 'message', 'detectedAt', 'status', 'assignee', 'actions'];

  protected readonly isOperator = computed(() => this.auth.role() === 'OPERATOR');
  protected readonly isTechnician = computed(() => this.auth.role() === 'TECHNICIAN');
  protected readonly canOpenMeter = computed(() => this.auth.hasAnyRole(['ADMIN', 'OPERATOR']));

  protected readonly status = signal<AlertStatus | null>(null);
  protected readonly severity = signal<Severity | null>(null);
  protected readonly ruleCode = linkedSignal<RuleCode | null>(() => this.regle() ?? null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly busyId = signal<string | null>(null);

  private readonly query = computed(() => ({
    status: this.status(),
    severity: this.severity(),
    ruleCode: this.ruleCode(),
    page: this.pageIndex(),
    size: this.pageSize(),
  }));

  private readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap((query) =>
        this.alertApi.search(query).pipe(
          map((page): LoadState => ({ status: 'ready', page })),
          catchError(() => of<LoadState>({ status: 'error' })),
          startWith<LoadState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as LoadState },
  );

  /** Current page; rows are replaced in place after an update, and kept while the next page loads. */
  protected readonly page = linkedSignal<LoadState, Page<Alert> | null>({
    source: this.state,
    computation: (state, previous) => (state.status === 'ready' ? state.page : (previous?.value ?? null)),
  });

  protected readonly loadingMore = computed(() => this.state().status === 'loading');
  protected readonly failed = computed(() => this.state().status === 'error');
  protected readonly hasFilters = computed(() => !!this.status() || !!this.severity() || !!this.ruleCode());

  /** Technicians an operator can assign alerts to. */
  protected readonly technicians = toSignal(
    toObservable(this.isOperator).pipe(
      switchMap((operator) => (operator ? this.userApi.technicians() : EMPTY)),
      catchError(() => of<UserSummary[]>([])),
    ),
    { initialValue: [] as UserSummary[] },
  );

  protected readonly subtitle = computed(() => {
    const total = this.page()?.totalElements;
    if (total === undefined) {
      return this.isTechnician() ? 'Alertes qui vous sont assignées' : 'Chargement';
    }
    const noun = total > 1 ? 'alertes' : 'alerte';
    return this.isTechnician() ? `${total} ${noun} qui vous sont assignées` : `${total} ${noun}`;
  });

  protected readonly transitionLabel: Record<AlertStatus, string> = {
    IN_PROGRESS: 'Prendre en charge',
    RESOLVED: 'Marquer comme résolue',
    NEW: 'Rouvrir',
  };

  protected readonly transitionIcon: Record<AlertStatus, string> = {
    IN_PROGRESS: 'ph-wrench',
    RESOLVED: 'ph-check-circle',
    NEW: 'ph-arrow-counter-clockwise',
  };

  protected ruleName(alert: Alert): string {
    return RULE_LABEL[alert.ruleCode];
  }

  protected transitions(alert: Alert): AlertStatus[] {
    const role = this.auth.role();
    // The API only returns a technician's own alerts, so every row they see is assigned to them.
    return role ? allowedTransitions(alert.status, role, role === 'TECHNICIAN') : [];
  }

  protected setFilter(kind: 'status' | 'severity' | 'ruleCode', value: string | null): void {
    if (kind === 'status') {
      this.status.set(value as AlertStatus | null);
    } else if (kind === 'severity') {
      this.severity.set(value as Severity | null);
    } else {
      this.ruleCode.set(value as RuleCode | null);
    }
    this.pageIndex.set(0);
  }

  protected resetFilters(): void {
    this.status.set(null);
    this.severity.set(null);
    this.ruleCode.set(null);
    this.pageIndex.set(0);
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  protected changeStatus(alert: Alert, status: AlertStatus): void {
    this.update(alert, { status }, `Alerte passée à « ${ALERT_STATUS_LABEL[status]} ».`);
  }

  protected assign(alert: Alert, technician: UserSummary | null): void {
    this.update(
      alert,
      { assignedTo: technician?.id ?? null },
      technician ? `Alerte assignée à ${technician.fullName}.` : 'Assignation retirée.',
    );
  }

  private update(alert: Alert, update: AlertUpdate, message: string): void {
    this.busyId.set(alert.id);
    this.alertApi.update(alert.id, update).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.page.update((page) =>
          page ? { ...page, content: page.content.map((a) => (a.id === updated.id ? updated : a)) } : page,
        );
        this.snackBar.open(message, 'OK');
      },
      error: () => this.busyId.set(null),
    });
  }
}
