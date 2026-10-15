import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import type * as Leaflet from 'leaflet';
import { catchError, of, switchMap, timer } from 'rxjs';
import { MeterApi } from '../../core/api/api.services';
import { AuthStore } from '../../core/auth/auth.store';
import { MAP_STATUS_LABEL } from '../../core/labels';
import { MapMarker, MapStatus } from '../../core/models';
import { ThemeService, resolveColor } from '../../core/theme.service';
import { FES_CENTER, baseLayer, loadLeaflet } from '../../shared/map-tiles';
import { EmptyState, MapStatusBadge, MeterTypeLabel, PageHeader } from '../../shared/ui';

type StatusFilter = MapStatus | 'ALL';

const STATUS_COLOR: Record<MapStatus, string> = {
  OK: 'var(--status-ok)',
  ALERT: 'var(--status-alert)',
  SILENT: 'var(--status-silent)',
};

@Component({
  selector: 'app-map-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    PageHeader,
    EmptyState,
    MapStatusBadge,
    MeterTypeLabel,
  ],
  templateUrl: './map.page.html',
  styleUrl: './map.page.scss',
})
export class MapPage {
  private readonly meterApi = inject(MeterApi);
  private readonly theme = inject(ThemeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly auth = inject(AuthStore);
  protected readonly statusLabel = MAP_STATUS_LABEL;
  protected readonly canOpenDetails = computed(() => this.auth.hasAnyRole(['ADMIN', 'OPERATOR']));

  protected readonly failed = signal(false);
  protected readonly filter = signal<StatusFilter>('ALL');
  protected readonly selected = signal<MapMarker | null>(null);

  /** Markers, refreshed every minute. */
  protected readonly markers = toSignal(
    timer(0, 60_000).pipe(
      switchMap(() =>
        this.meterApi.map().pipe(
          catchError(() => {
            this.failed.set(true);
            return of(null);
          }),
        ),
      ),
    ),
  );

  protected readonly counts = computed(() => {
    const markers = this.markers() ?? [];
    const count = (status: MapStatus) => markers.filter((m) => m.status === status).length;
    return { ALL: markers.length, OK: count('OK'), ALERT: count('ALERT'), SILENT: count('SILENT') };
  });

  protected readonly visible = computed(() => {
    const filter = this.filter();
    return (this.markers() ?? []).filter((m) => filter === 'ALL' || m.status === filter);
  });

  private readonly mapElement = viewChild.required<ElementRef<HTMLElement>>('map');
  private readonly leaflet = signal<{ L: typeof Leaflet; map: Leaflet.Map; layer: Leaflet.LayerGroup } | null>(null);
  private tiles?: Leaflet.TileLayer;
  private fitted = false;

  constructor() {
    afterNextRender(async () => {
      const L = await loadLeaflet();
      const map = L.map(this.mapElement().nativeElement, {
        center: FES_CENTER,
        zoom: 13,
        zoomControl: true,
      });
      // Leaflet measures its container once; re-measure when the layout settles or resizes.
      const resize = new ResizeObserver(() => map.invalidateSize());
      resize.observe(this.mapElement().nativeElement);
      map.on('unload', () => resize.disconnect());
      map.on('click', () => this.selected.set(null));
      const layer = L.layerGroup().addTo(map);
      this.leaflet.set({ L, map, layer });
    });

    effect(() => {
      const leaflet = this.leaflet();
      const dark = this.theme.isDark();
      if (leaflet) {
        this.tiles?.remove();
        this.tiles = baseLayer(leaflet.L, dark).addTo(leaflet.map);
      }
    });

    // Redraw markers when data, filter, selection or theme change.
    effect(() => {
      const leaflet = this.leaflet();
      const markers = this.visible();
      const selectedId = this.selected()?.id;
      this.theme.isDark();
      if (leaflet) {
        this.draw(leaflet.L, leaflet.map, leaflet.layer, markers, selectedId);
      }
    });

    inject(DestroyRef).onDestroy(() => this.leaflet()?.map.remove());
  }

  private draw(
    L: typeof Leaflet,
    map: Leaflet.Map,
    layer: Leaflet.LayerGroup,
    markers: MapMarker[],
    selectedId: string | undefined,
  ): void {
    layer.clearLayers();
    const ring = resolveColor('var(--app-panel)', this.host);
    const colors = Object.fromEntries(
      (Object.keys(STATUS_COLOR) as MapStatus[]).map((s) => [s, resolveColor(STATUS_COLOR[s], this.host)]),
    ) as Record<MapStatus, string>;

    // Problems drawn last so they sit on top of normal meters.
    const order: Record<MapStatus, number> = { OK: 0, SILENT: 1, ALERT: 2 };
    for (const marker of [...markers].sort((a, b) => order[a.status] - order[b.status])) {
      const color = colors[marker.status];
      const isSelected = marker.id === selectedId;
      // Shape as well as colour: alerts are larger, silent meters are hollow rings.
      const circle = L.circleMarker([marker.lat, marker.lng], {
        radius: (marker.status === 'ALERT' ? 9 : 7) + (isSelected ? 3 : 0),
        color: marker.status === 'SILENT' ? color : ring,
        weight: marker.status === 'SILENT' ? 3 : 2,
        fillColor: marker.status === 'SILENT' ? ring : color,
        fillOpacity: 1,
      });
      // Leaflet inserts strings as HTML: pass a text node so server data is never parsed as markup.
      const label = document.createElement('span');
      label.textContent = `${marker.serial}, ${MAP_STATUS_LABEL[marker.status].toLowerCase()}`;
      circle.bindTooltip(label, { direction: 'top', offset: [0, -8] });
      circle.on('click', (event) => {
        L.DomEvent.stopPropagation(event);
        this.selected.set(marker);
      });
      circle.addTo(layer);
    }

    if (!this.fitted && markers.length > 0) {
      map.fitBounds(L.latLngBounds(markers.map((m) => [m.lat, m.lng])), { padding: [40, 40] });
      this.fitted = true;
    }
  }
}
