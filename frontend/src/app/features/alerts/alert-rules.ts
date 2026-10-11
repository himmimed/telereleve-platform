import { AlertStatus, Role } from '../../core/models';

/**
 * Status changes offered in the UI, per role (the backend enforces the same rules):
 * - OPERATOR: any change on any alert (take charge, resolve, reopen);
 * - TECHNICIAN: only on alerts assigned to them, and never reopen a resolved alert;
 * - ADMIN: read-only on alerts (PATCH /api/alerts is OPERATOR / TECHNICIAN in the specification).
 */
export function allowedTransitions(current: AlertStatus, role: Role, assignedToMe: boolean): AlertStatus[] {
  if (role === 'ADMIN' || (role === 'TECHNICIAN' && !assignedToMe)) {
    return [];
  }
  switch (current) {
    case 'NEW':
      return ['IN_PROGRESS', 'RESOLVED'];
    case 'IN_PROGRESS':
      return ['RESOLVED'];
    case 'RESOLVED':
      return role === 'OPERATOR' ? ['NEW'] : [];
  }
}
