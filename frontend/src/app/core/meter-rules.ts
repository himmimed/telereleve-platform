// Display rules for a meter, kept as pure functions so they are unit-tested.
// They mirror the backend rules (A3 silent meter, A5 voltage range) for display only.
import { MapStatus, Meter } from './models';

/** A3: silent when no reading for more than 3 x the reading interval. */
export const SILENT_FACTOR = 3;

/** A5: 230 V ± 10 %. */
export const VOLTAGE_MIN = 207;
export const VOLTAGE_MAX = 253;

export function isSilent(meter: Pick<Meter, 'status' | 'lastReadingAt' | 'readingIntervalMin'>, now = Date.now()): boolean {
  if (meter.status !== 'ACTIVE') {
    return false;
  }
  if (!meter.lastReadingAt) {
    return true;
  }
  return now - Date.parse(meter.lastReadingAt) > SILENT_FACTOR * meter.readingIntervalMin * 60_000;
}

/** Same classification as the map: SILENT first, then ALERT if an alert is open, else OK. */
export function meterHealth(
  meter: Pick<Meter, 'status' | 'lastReadingAt' | 'readingIntervalMin'>,
  openAlertCount: number,
  now = Date.now(),
): MapStatus {
  if (isSilent(meter, now)) {
    return 'SILENT';
  }
  return openAlertCount > 0 ? 'ALERT' : 'OK';
}

export function isVoltageInRange(voltage: number): boolean {
  return voltage >= VOLTAGE_MIN && voltage <= VOLTAGE_MAX;
}

export type SignalQuality = 'Excellent' | 'Bon' | 'Moyen' | 'Faible';

/** Received signal strength (dBm) of the meter's radio module. */
export function signalQuality(dbm: number): SignalQuality {
  if (dbm >= -65) {
    return 'Excellent';
  }
  if (dbm >= -75) {
    return 'Bon';
  }
  if (dbm >= -85) {
    return 'Moyen';
  }
  return 'Faible';
}
