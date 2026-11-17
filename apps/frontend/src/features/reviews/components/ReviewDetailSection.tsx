import { useMemo, useState } from 'react';
import { EmptyState, ErrorState, LoadingState } from '../../../shared/components/StateFeedback';
import type { FindingCategory, FindingSeverity, ReviewDetail } from '../../../shared/types/api';
import {
  CATEGORY_LABELS,
  SEVERITY_LABELS,
  STATUS_LABELS,
  formatDateTime,
} from '../../../shared/utils/formatters';
import { ApprovalDecisionForm } from './ApprovalDecisionForm';
import { FindingCard } from './FindingCard';

interface ReviewDetailSectionProps {
  review: ReviewDetail | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  onDecisionRecorded: () => void;
}

export function ReviewDetailSection({
  review,
  loading,
  error,
  onRetry,
  onDecisionRecorded,
}: ReviewDetailSectionProps) {
  const [selectedSeverity, setSelectedSeverity] = useState<FindingSeverity | 'ALL'>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<FindingCategory | 'ALL'>('ALL');

  const filteredFindings = useMemo(() => {
    if (!review) {
      return [];
    }
    return review.findings.filter((item) => {
      const matchesSeverity = selectedSeverity === 'ALL' || item.severity === selectedSeverity;
      const matchesCategory = selectedCategory === 'ALL' || item.category === selectedCategory;
      return matchesSeverity && matchesCategory;
    });
  }, [review, selectedSeverity, selectedCategory]);

  if (loading && !review) {
    return (
      <section className="panel-section" aria-label="Detalhe da Revisão">
        <LoadingState message="Carregando detalhes do Pull Request e findings..." />
      </section>
    );
  }

  if (error && !review) {
    return (
      <section className="panel-section" aria-label="Detalhe da Revisão">
        <ErrorState
          title="Erro ao carregar detalhes da revisão"
          message={error}
          onRetry={onRetry}
        />
      </section>
    );
  }

  if (!review) {
    return (
      <section className="panel-section" aria-label="Detalhe da Revisão">
        <h2>Detalhe do Pull Request</h2>
        <EmptyState
          title="Nenhuma revisão selecionada"
          description="Selecione uma execução de revisão na lista para inspecionar o resumo técnico, limitações, findings e registrar sua decisão."
        />
      </section>
    );
  }

  return (
    <section className="panel-section" aria-label="Detalhe da Revisão">
      <header className="detail-header">
        <div>
          <div className="detail-title-row">
            <h2>
              PR #{review.pullRequestNumber} — {review.pullRequestTitle}
            </h2>
            <span className={`badge badge-status-${review.status.toLowerCase()}`}>
              {STATUS_LABELS[review.status]}
            </span>
          </div>
          <p className="muted-text">
            Repositório: <strong>{review.repositoryFullName}</strong> · Autor:{' '}
            <strong>{review.authorLogin}</strong> · Branch: <code>{review.headRef}</code> →{' '}
            <code>{review.baseRef}</code>
          </p>
        </div>

        <a
          href={review.pullRequestHtmlUrl}
          target="_blank"
          rel="noreferrer"
          className="btn btn-secondary"
        >
          Abrir PR no GitHub ↗
        </a>
      </header>

      <div className="metadata-grid">
        <div>
          <span className="meta-label">Commit SHA Analisado</span>
          <code>{review.headSha}</code>
        </div>
        <div>
          <span className="meta-label">Modelo / Prompt</span>
          <span>
            {review.modelName ?? '—'} (v{review.promptVersion ?? '—'})
          </span>
        </div>
        <div>
          <span className="meta-label">Tokens (Entrada / Saída)</span>
          <span>
            {review.inputTokens ?? 'N/D'} / {review.outputTokens ?? 'N/D'}
          </span>
        </div>
        <div>
          <span className="meta-label">Concluída em</span>
          <span>{formatDateTime(review.completedAt ?? review.createdAt)}</span>
        </div>
      </div>

      {review.failureMessage && (
        <div className="inline-alert inline-alert-error" role="alert">
          <strong>Falha registrada ({review.failureCode ?? 'ERROR'}):</strong>{' '}
          {review.failureMessage}
        </div>
      )}

      {review.summary && (
        <div className="summary-box">
          <h3>Resumo Técnico da Revisão</h3>
          <p>{review.summary}</p>
        </div>
      )}

      {review.limitations.length > 0 && (
        <div className="limitations-box" aria-label="Limitações declaradas pelo agente">
          <h3>Limitações e Escopo Parcial</h3>
          <ul>
            {review.limitations.map((limitation, idx) => (
              <li key={`${idx}-${limitation}`}>{limitation}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="findings-section">
        <div className="findings-toolbar">
          <h3>
            Findings ({filteredFindings.length} de {review.findings.length})
          </h3>

          <div className="filter-controls">
            <div className="filter-field">
              <label htmlFor="filter-severity">Severidade:</label>
              <select
                id="filter-severity"
                value={selectedSeverity}
                onChange={(e) =>
                  setSelectedSeverity(e.target.value as FindingSeverity | 'ALL')
                }
              >
                <option value="ALL">Todas as severidades</option>
                {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((sev) => (
                  <option key={sev} value={sev}>
                    {SEVERITY_LABELS[sev]}
                  </option>
                ))}
              </select>
            </div>

            <div className="filter-field">
              <label htmlFor="filter-category">Categoria:</label>
              <select
                id="filter-category"
                value={selectedCategory}
                onChange={(e) =>
                  setSelectedCategory(e.target.value as FindingCategory | 'ALL')
                }
              >
                <option value="ALL">Todas as categorias</option>
                {(['BUG', 'SECURITY', 'PERFORMANCE', 'MAINTAINABILITY', 'TEST'] as const).map(
                  (cat) => (
                    <option key={cat} value={cat}>
                      {CATEGORY_LABELS[cat]}
                    </option>
                  ),
                )}
              </select>
            </div>
          </div>
        </div>

        {review.findings.length === 0 ? (
          <EmptyState
            title="Nenhum finding apontado nesta revisão"
            description="O Review Agent analisou o Pull Request e não identificou defeitos funcionais, vulnerabilidades ou riscos técnicos verificáveis."
          />
        ) : filteredFindings.length === 0 ? (
          <EmptyState
            title="Nenhum finding corresponde aos filtros selecionados"
            description="Altere os filtros de severidade ou categoria acima para exibir os demais findings desta revisão."
          />
        ) : (
          <div className="findings-list">
            {filteredFindings.map((finding) => (
              <FindingCard key={finding.id} finding={finding} />
            ))}
          </div>
        )}
      </div>

      <ApprovalDecisionForm
        reviewExecutionId={review.id}
        reviewStatus={review.status}
        latestApprovalDecision={review.latestApprovalDecision}
        onDecisionRecorded={onDecisionRecorded}
      />
    </section>
  );
}

