import { ErrorState, LoadingState } from '../../../shared/components/StateFeedback';
import type { DashboardSummary } from '../../../shared/types/api';
import { CATEGORY_LABELS, SEVERITY_LABELS } from '../../../shared/utils/formatters';

interface DashboardSummarySectionProps {
  summary: DashboardSummary | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}

export function DashboardSummarySection({
  summary,
  loading,
  error,
  onRetry,
}: DashboardSummarySectionProps) {
  if (loading && !summary) {
    return (
      <section className="panel-section" aria-label="Resumo do Dashboard">
        <LoadingState message="Carregando métricas do dashboard..." />
      </section>
    );
  }

  if (error && !summary) {
    return (
      <section className="panel-section" aria-label="Resumo do Dashboard">
        <ErrorState
          title="Falha ao carregar métricas do dashboard"
          message={error}
          onRetry={onRetry}
        />
      </section>
    );
  }

  if (!summary) {
    return null;
  }

  const completedCount =
    (summary.reviewsByStatus.COMPLETED ?? 0) + (summary.reviewsByStatus.PARTIAL ?? 0);
  const failedCount = summary.reviewsByStatus.FAILED ?? 0;
  const runningCount =
    (summary.reviewsByStatus.RUNNING ?? 0) + (summary.reviewsByStatus.PENDING ?? 0);

  return (
    <section className="panel-section" aria-label="Resumo do Dashboard">
      <div className="section-header">
        <h2>Visão Geral de Revisões</h2>
        <span className="muted-text">Métricas consolidadas em tempo real do backend</span>
      </div>

      <div className="kpi-grid">
        <article className="kpi-card">
          <span className="kpi-label">Repositórios Ativos</span>
          <strong className="kpi-value" data-testid="kpi-active-repos">
            {summary.activeRepositoriesCount}
          </strong>
          <span className="kpi-foot">Cadastrados na allowlist</span>
        </article>

        <article className="kpi-card">
          <span className="kpi-label">Total de Revisões</span>
          <strong className="kpi-value" data-testid="kpi-total-reviews">
            {summary.totalReviewsCount}
          </strong>
          <span className="kpi-foot">
            {completedCount} concluídas · {runningCount} em fila/execução · {failedCount} com falha
          </span>
        </article>

        <article className="kpi-card">
          <span className="kpi-label">Findings Detectados</span>
          <strong className="kpi-value" data-testid="kpi-total-findings">
            {summary.totalFindingsCount}
          </strong>
          <span className="kpi-foot">
            Críticos: {summary.findingsBySeverity.CRITICAL ?? 0} · Altos:{' '}
            {summary.findingsBySeverity.HIGH ?? 0}
          </span>
        </article>

        <article className="kpi-card">
          <span className="kpi-label">Decisões Humanas</span>
          <strong className="kpi-value" data-testid="kpi-human-decisions">
            {summary.approvedReviewsCount + summary.rejectedReviewsCount}
          </strong>
          <span className="kpi-foot">
            {summary.approvedReviewsCount} aprovadas · {summary.rejectedReviewsCount} rejeitadas
          </span>
        </article>
      </div>

      <div className="breakdown-row">
        <div className="breakdown-box">
          <h3>Findings por Severidade</h3>
          <div className="pill-group">
            {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((sev) => (
              <span key={sev} className={`badge badge-severity-${sev.toLowerCase()}`}>
                {SEVERITY_LABELS[sev]}: {summary.findingsBySeverity[sev] ?? 0}
              </span>
            ))}
          </div>
        </div>

        <div className="breakdown-box">
          <h3>Findings por Categoria</h3>
          <div className="pill-group">
            {(['BUG', 'SECURITY', 'PERFORMANCE', 'MAINTAINABILITY', 'TEST'] as const).map((cat) => (
              <span key={cat} className="badge badge-category">
                {CATEGORY_LABELS[cat]}: {summary.findingsByCategory[cat] ?? 0}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

