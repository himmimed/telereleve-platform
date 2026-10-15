import { leafletApi, loadLeaflet } from './map-tiles';

describe('leafletApi', () => {
  const api = { map: () => 'carte', tileLayer: () => 'tuiles' };

  it('uses the module itself when it exposes Leaflet (development server)', () => {
    expect(leafletApi(api).map).toBe(api.map);
  });

  it('uses the default export when only it exposes Leaflet (production bundle)', () => {
    expect(leafletApi({ default: api }).map).toBe(api.map);
  });

  it('loads the real Leaflet with a callable map function', async () => {
    const L = await loadLeaflet();
    expect(typeof L.map).toBe('function');
    expect(typeof L.tileLayer).toBe('function');
  });
});
