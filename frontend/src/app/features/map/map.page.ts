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
  untracked,
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
import { ThemeService } from '../../core/theme.service';
import { FES_CENTER, baseLayer, loadLeaflet } from '../../shared/map-tiles';
import { prefersReducedMotion } from '../../shared/motion';
import { EmptyState, MapStatusBadge, MeterTypeLabel, PageHeader } from '../../shared/ui';

type StatusFilter = MapStatus | 'ALL';

/** Stacking of pins: problems sit above normal meters. */
const PIN_LAYER: Record<MapStatus, number> = { OK: 0, SILENT: 100, ALERT: 200 };

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
  /** Pins on the map by meter id, so a refresh or a filter only touches the pins that changed. */
  private readonly pins = new Map<string, { marker: Leaflet.Marker; element: HTMLElement; status: MapStatus }>();

  constructor() {
    afterNextRender(async () => {
      const L = await loadLeaflet();
      const map = L.map(this.mapElement().nativeElement, {
        center: FES_CENTER,
        // One step back from the final view: the first display flies in to the meters (see `sync`).
        zoom: 11,
        zoomControl: true,
        // Gentler wheel zoom: the default feels abrupt with a free-spinning wheel.
        wheelPxPerZoomLevel: 100,
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
        const previous = this.tiles;
        this.tiles = baseLayer(leaflet.L, dark).addTo(leaflet.map);
        // The old tiles stay under the new ones for a moment: the theme change cross-fades, no white flash.
        if (previous) {
          setTimeout(() => previous.remove(), 450);
        }
      }
    });

    // Pins follow the data and the filter.
    effect(() => {
      const leaflet = this.leaflet();
      const markers = this.visible();
      if (leaflet) {
        this.sync(leaflet.L, leaflet.map, leaflet.layer, markers);
      }
    });

    // Selection: highlight the pin and glide the map to it, without redrawing anything.
    effect(() => {
      const selected = this.selected();
      const leaflet = this.leaflet();
      for (const [id, pin] of this.pins) {
        pin.element.classList.toggle('selected', id === selected?.id);
      }
      if (selected && leaflet) {
        leaflet.map.panTo([selected.lat, selected.lng], {
          animate: !prefersReducedMotion(),
          duration: 0.8,
          easeLinearity: 0.2,
        });
      }
    });

    inject(DestroyRef).onDestroy(() => this.leaflet()?.map.remove());
  }

  /**
   * Brings the pins in line with the visible meters. Pins that stay are left alone (no flicker on the
   * 60 s refresh or when selecting), new ones pop in one after the other, removed ones shrink away.
   */
  private sync(L: typeof Leaflet, map: Leaflet.Map, layer: Leaflet.LayerGroup, markers: MapMarker[]): void {
    const wanted = new Set(markers.map((m) => m.id));
    for (const [id, pin] of this.pins) {
      if (!wanted.has(id)) {
        this.pins.delete(id);
        pin.element.classList.add('leaving');
        setTimeout(() => layer.removeLayer(pin.marker), 260);
      }
    }

    const selectedId = untracked(() => this.selected()?.id);
    const firstDisplay = !this.fitted;
    let arrival = 0;
    for (const marker of markers) {
      const existing = this.pins.get(marker.id);
      if (existing?.status === marker.status) {
        continue;
      }
      if (existing) {
        layer.removeLayer(existing.marker);
      }

      const element = document.createElement('span');
      element.className = `pin pin-${marker.status}`;
      // The very first pins wait for the fly-in to be under way; later arrivals pop in after a short stagger.
      element.style.setProperty('--d', `${(firstDisplay ? 600 : 0) + Math.min(arrival++, 40) * 24}ms`);
      element.classList.toggle('selected', marker.id === selectedId);

      const description = `${marker.serial}, ${MAP_STATUS_LABEL[marker.status].toLowerCase()}`;
      const pin = L.marker([marker.lat, marker.lng], {
        icon: L.divIcon({ className: 'pin-wrap', html: element, iconSize: [30, 30], iconAnchor: [15, 15] }),
        zIndexOffset: PIN_LAYER[marker.status],
        alt: description,
        keyboard: true,
      });
      // Leaflet inserts strings as HTML: pass a text node so server data is never parsed as markup.
      const label = document.createElement('span');
      label.textContent = description;
      pin.bindTooltip(label, { direction: 'top', offset: [0, -12] });
      pin.on('click', (event) => {
        L.DomEvent.stopPropagation(event);
        this.selected.set(marker);
      });
      pin.addTo(layer);
      this.pins.set(marker.id, { marker: pin, element, status: marker.status });
    }

    if (firstDisplay && markers.length > 0) {
      this.fitted = true;
      const bounds = L.latLngBounds(markers.map((m) => [m.lat, m.lng]));
      if (prefersReducedMotion()) {
        map.fitBounds(bounds, { padding: [40, 40], animate: false });
      } else {
        map.flyToBounds(bounds, { padding: [40, 40], duration: 1.8, easeLinearity: 0.2 });
      }
    }
  }
}
