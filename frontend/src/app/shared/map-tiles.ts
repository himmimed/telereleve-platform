import type * as Leaflet from 'leaflet';

/**
 * Leaflet's functions from its module object. Leaflet is a CommonJS (UMD) package: the development
 * server exposes `map`, `tileLayer`... on the module itself, the production bundle only under `default`.
 * Found when the Docker image showed no map while `ng serve` did (docs/decisions.md D35).
 */
export function leafletApi(module: object): typeof Leaflet {
  const candidate = module as typeof Leaflet & { default?: typeof Leaflet };
  return typeof candidate.map === 'function' ? candidate : (candidate.default as typeof Leaflet);
}

/** Loads Leaflet on demand, after the page is on screen. */
export async function loadLeaflet(): Promise<typeof Leaflet> {
  return leafletApi(await import('leaflet'));
}

/** Centre of Fès, where the simulated meter park is located. */
export const FES_CENTER: [number, number] = [34.0333, -5.0];

/**
 * Standard OpenStreetMap tiles (no API key, attribution required). The calmer light look and the
 * dark variant are produced with a CSS filter on the tile layer (see styles.scss, `.map-tiles`),
 * so the map follows the app theme without a second tile provider.
 */
export function baseLayer(L: typeof Leaflet, dark: boolean): Leaflet.TileLayer {
  return L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    className: dark ? 'map-tiles map-tiles-dark' : 'map-tiles',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  });
}
