import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { MeterApi } from '../../core/api/api.services';
import { AuthStore } from '../../core/auth/auth.store';
import { METER_STATUS_LABEL } from '../../core/labels';
import { Meter, MeterStatus, MeterType, Page } from '../../core/models';
import { DateTimePipe, RelativeTimePipe } from '../../shared/format';
import { Badge, EmptyState, MeterTypeLabel, PageHeader, Skeleton } from '../../shared/ui';
import { MeterFormDialog } from './meter-form.dialog';

type LoadState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; page: Page<Meter> };

@Component({
  selector: 'app-meter-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PageHeader,
    Skeleton,
    EmptyState,
    Badge,
    MeterTypeLabel,
    RelativeTimePipe,
    DateTimePipe,
  ],
  templateUrl: './meter-list.page.html',
  styleUrl: './meter-list.page.scss',
})
export class MeterListPage {
  private readonly meterApi = inject(MeterApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthStore);
  protected readonly statusLabel = METER_STATUS_LABEL;
  protected readonly columns = ['serial', 'type', 'client', 'address', 'lastReading', 'status'];

  protected readonly search = signal('');
  protected readonly type = signal<MeterType | null>(null);
  protected readonly status = signal<MeterStatus | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  private readonly reload = signal(0);

  private readonly debouncedSearch = toSignal(
    toObservable(this.search).pipe(debounceTime(300), map((s) => s.trim()), distinctUntilChanged()),
    { initialValue: '' },
  );

  private readonly query = computed(() => ({
    search: this.debouncedSearch(),
    type: this.type(),
    status: this.status(),
    page: this.pageIndex(),
    size: this.pageSize(),
    reload: this.reload(),
  }));

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap(({ reload: _reload, ...query }) =>
        this.meterApi.search(query).pipe(
          map((page): LoadState => ({ status: 'ready', page })),
          catchError(() => of<LoadState>({ status: 'error' })),
          startWith<LoadState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as LoadState },
  );

  /** Keeps the previous rows on screen while the next page loads (no flashing table). */
  protected readonly page = linkedSignal<LoadState, Page<Meter> | null>({
    source: this.state,
    computation: (state, previous) => (state.status === 'ready' ? state.page : (previous?.value ?? null)),
  });

  protected readonly subtitle = computed(() => {
    const page = this.page();
    if (!page) {
      return 'Chargement';
    }
    const noun = page.totalElements > 1 ? 'compteurs' : 'compteur';
    return `${page.totalElements} ${noun} ${this.hasFilters() ? 'correspondant aux filtres' : 'installés'}`;
  });

  protected readonly hasFilters = computed(() => !!this.search().trim() || !!this.type() || !!this.status());

  protected statusName(meter: Meter): string {
    return METER_STATUS_LABEL[meter.status];
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.pageIndex.set(0);
  }

  protected setType(value: MeterType | null): void {
    this.type.set(value);
    this.pageIndex.set(0);
  }

  protected setStatus(value: MeterStatus | null): void {
    this.status.set(value);
    this.pageIndex.set(0);
  }

  protected resetFilters(): void {
    this.search.set('');
    this.type.set(null);
    this.status.set(null);
    this.pageIndex.set(0);
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  protected open(meter: Meter): void {
    void this.router.navigate(['/compteurs', meter.id]);
  }

  protected create(): void {
    this.dialog
      .open(MeterFormDialog, { data: null, width: '620px', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((meter?: Meter) => {
        if (meter) {
          this.snackBar.open(`Compteur ${meter.serial} créé.`, 'OK');
          this.reload.update((n) => n + 1);
        }
      });
  }
}
