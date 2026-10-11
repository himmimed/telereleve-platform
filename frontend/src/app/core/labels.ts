// French labels for every enumeration shown in the UI, in one place so wording stays consistent.
import {
  AlertStatus,
  MapStatus,
  MeterStatus,
  MeterType,
  Role,
  RuleCode,
  Severity,
} from './models';

export const METER_TYPE_LABEL: Record<MeterType, string> = {
  ELECTRICITY: 'Électricité',
  WATER: 'Eau',
};

export const METER_TYPE_ICON: Record<MeterType, string> = {
  ELECTRICITY: 'ph-lightning',
  WATER: 'ph-drop',
};

export const METER_STATUS_LABEL: Record<MeterStatus, string> = {
  ACTIVE: 'Actif',
  INACTIVE: 'Inactif',
};

export const MAP_STATUS_LABEL: Record<MapStatus, string> = {
  OK: 'Normal',
  ALERT: 'En alerte',
  SILENT: 'Silencieux',
};

export const RULE_LABEL: Record<RuleCode, string> = {
  A1: 'Consommation nulle',
  A2: 'Pic de consommation',
  A3: 'Compteur silencieux',
  A4: 'Index décroissant',
  A5: 'Tension hors plage',
};

export const RULE_DESCRIPTION: Record<RuleCode, string> = {
  A1: 'Aucune consommation sur la fenêtre observée pour un compteur actif.',
  A2: 'Consommation de la dernière heure supérieure au seuil multiplié par la moyenne de la même heure.',
  A3: 'Aucune relève reçue depuis plus de N fois l’intervalle de relève du compteur.',
  A4: 'Nouvel index inférieur au précédent : fraude possible ou remplacement du compteur.',
  A5: 'Tension mesurée hors de 230 V ± seuil (compteurs électriques uniquement).',
};

/** Severity of the alerts raised by each rule (specification, "ANOMALY RULES"). */
export const RULE_SEVERITY: Record<RuleCode, Severity> = {
  A1: 'MEDIUM',
  A2: 'MEDIUM',
  A3: 'HIGH',
  A4: 'CRITICAL',
  A5: 'MEDIUM',
};

export const SEVERITY_LABEL: Record<Severity, string> = {
  CRITICAL: 'Critique',
  HIGH: 'Haute',
  MEDIUM: 'Moyenne',
};

export const ALERT_STATUS_LABEL: Record<AlertStatus, string> = {
  NEW: 'Nouvelle',
  IN_PROGRESS: 'En cours',
  RESOLVED: 'Résolue',
};

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Administrateur',
  OPERATOR: 'Opérateur',
  TECHNICIAN: 'Technicien',
};

export const UNIT_LABEL = {
  kWh: 'kWh',
  m3: 'm³',
} as const;
