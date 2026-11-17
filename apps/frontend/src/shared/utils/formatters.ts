import type {
  ApprovalDecision,
  FindingCategory,
  FindingOrigin,
  FindingSeverity,
  ReviewExecutionStatus,
} from '../types/api';

export const STATUS_LABELS: Record<ReviewExecutionStatus, string> = {
  PENDING: 'Pendente',
  RUNNING: 'Em análise',
  COMPLETED: 'Concluída',
  PARTIAL: 'Concluída (Parcial)',
  FAILED: 'Falhou',
  CANCELLED: 'Obsoleta / Cancelada',
};

export const SEVERITY_LABELS: Record<FindingSeverity, string> = {
  CRITICAL: 'Crítica',
  HIGH: 'Alta',
  MEDIUM: 'Média',
  LOW: 'Baixa',
};

export const CATEGORY_LABELS: Record<FindingCategory, string> = {
  BUG: 'Bug Funcional',
  SECURITY: 'Segurança',
  PERFORMANCE: 'Desempenho',
  MAINTAINABILITY: 'Manutenibilidade',
  TEST: 'Testes',
};

export const ORIGIN_LABELS: Record<FindingOrigin, string> = {
  AI: 'IA (Gemini + GitHub MCP)',
  SONARQUBE: 'SonarQube MCP',
  CORRELATED: 'Correlacionado (IA + SonarQube)',
};

export const APPROVAL_LABELS: Record<ApprovalDecision, string> = {
  APPROVED: 'Aprovada no ReviewPilot',
  REJECTED: 'Rejeitada no ReviewPilot',
};

export function formatShortSha(sha: string | null | undefined): string {
  if (!sha) {
    return '—';
  }
  return sha.slice(0, 8);
}

export function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) {
    return '—';
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return isoString;
  }
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(date);
}

export function formatConfidence(confidence: number | null | undefined): string {
  if (confidence === null || confidence === undefined) {
    return 'Não informada';
  }
  return `${Math.round(confidence * 100)}% (estimativa do modelo)`;
}

