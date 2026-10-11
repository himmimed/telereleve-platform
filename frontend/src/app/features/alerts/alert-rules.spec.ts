import { allowedTransitions } from './alert-rules';

describe('allowedTransitions', () => {
  it('lets an operator take charge of or resolve a new alert', () => {
    expect(allowedTransitions('NEW', 'OPERATOR', false)).toEqual(['IN_PROGRESS', 'RESOLVED']);
  });

  it('lets an operator reopen a resolved alert', () => {
    expect(allowedTransitions('RESOLVED', 'OPERATOR', false)).toEqual(['NEW']);
  });

  it('lets a technician resolve an alert assigned to them', () => {
    expect(allowedTransitions('IN_PROGRESS', 'TECHNICIAN', true)).toEqual(['RESOLVED']);
  });

  it('gives a technician no action on an alert assigned to someone else', () => {
    expect(allowedTransitions('NEW', 'TECHNICIAN', false)).toEqual([]);
  });

  it('does not let a technician reopen a resolved alert', () => {
    expect(allowedTransitions('RESOLVED', 'TECHNICIAN', true)).toEqual([]);
  });

  it('keeps alerts read-only for administrators, as in the API permissions', () => {
    expect(allowedTransitions('NEW', 'ADMIN', false)).toEqual([]);
  });
});
