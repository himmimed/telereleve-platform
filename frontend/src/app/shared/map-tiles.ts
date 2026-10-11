import type * as Leaflet from 'leaflet';

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
