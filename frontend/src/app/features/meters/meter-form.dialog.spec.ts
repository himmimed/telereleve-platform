import { FormControl, FormGroup } from '@angular/forms';
import { SERIAL_PATTERN, serialMatchesType } from './meter-form.dialog';

describe('meter serial validation', () => {
  it.each(['CEAC-E-000123', 'CEAC-W-000001'])('accepts %s', (serial) => {
    expect(SERIAL_PATTERN.test(serial)).toBe(true);
  });

  it.each(['CEAC-X-000123', 'CEAC-E-12345', 'ceac-e-000123', 'CEAC-E-0001234', ''])('rejects "%s"', (serial) => {
    expect(SERIAL_PATTERN.test(serial)).toBe(false);
  });

  const form = (serial: string, type: string) =>
    new FormGroup({ serial: new FormControl(serial), type: new FormControl(type) });

  it('accepts a prefix matching the energy type', () => {
    expect(serialMatchesType(form('CEAC-E-000001', 'ELECTRICITY'))).toBeNull();
    expect(serialMatchesType(form('CEAC-W-000001', 'WATER'))).toBeNull();
  });

  it('flags a water serial on an electricity meter', () => {
    expect(serialMatchesType(form('CEAC-W-000001', 'ELECTRICITY'))).toEqual({ serialType: true });
  });

  it('leaves malformed serials to the pattern validator', () => {
    expect(serialMatchesType(form('nope', 'WATER'))).toBeNull();
  });
});
