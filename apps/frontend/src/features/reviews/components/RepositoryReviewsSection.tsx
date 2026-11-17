import { EmptyState, ErrorState, LoadingState } from '../../../shared/components/StateFeedback';
import type { RepositoryItem, ReviewSummaryItem } from '../../../shared/types/api';
import {
  APPROVAL_LABELS,
  STATUS_LABELS,
  formatDateTime,
  formatShortSha,
} from '../../../shared/utils/formatters';

interface RepositoryReviewsSectionProps {
  repository: RepositoryItem | null;
  reviews: ReviewSummaryItem[];
  selectedReviewId: string | null;
  loading: boolean;
  error: string | null;
  onSelectReview: (reviewId: string) => void;
  onRetry: () => void;
}

export function RepositoryReviewsSection({
  repository,
  reviews,
  selectedReviewId,
  loading,
  error,
  onSelectReview,
  onRetry,
}: RepositoryReviewsSectionProps) {
  if (!repository) {
    return (
      <section className="panel-section" aria-label="Revisões do Repositório">
        <h2>Execuções de Revisão</h2>
        <EmptyState
          title="Nenhum repositório selecionado"
          description="Selecione um repositório na coluna à esquerda para visualizar as execuções de revisão."
        />
      </section>
    );
  }

  return (
    <section className="panel-section" aria-label="Revisões do Repositório">
      <div className="section-header">
        <div>
          <h2>Revisões — {repository.fullName}</h2>
          <span className="muted-text">
            Histórico de execuções de Pull Request ordenadas da mais recente para a mais antiga
          </span>
        </div>
      </div>

      {loading && reviews.length === 0 ? (
        <LoadingState message={`Carregando revisões de ${repository.fullName}...`} />
      ) : error && reviews.length === 0 ? (
        <ErrorState
          title="Falha ao carregar revisões do repositório"
          message={error}
          onRetry={onRetry}
        />
      ) : reviews.length === 0 ? (
        <EmptyState
          title="Nenhuma revisão encontrada para este repositório"
          description="Abra ou atualize um Pull Request no GitHub para disparar o webhook e iniciar uma análise pelo Review Agent."
        />
      ) : (
        <ul className="review-list" aria-label={`Revisões de ${repository.fullName}`}>
          {reviews.map((review) => {
            const isSelected = review.id === selectedReviewId;
            return (
              <li key={review.id}>
                <button
                  type="button"
                  className={`review-item-btn ${isSelected ? 'review-item-selected' : ''}`}
                  onClick={() => onSelectReview(review.id)}
                  aria-pressed={isSelected}
                >
                  <div className="review-item-top">
                    <span className="review-pr-title">
                      {review.pullRequestNumber ? `PR #${review.pullRequestNumber}` : 'PR'} —{' '}
                      {review.pullRequestTitle ?? 'Sem título'}
                    </span>
                    <span className={`badge badge-status-${review.status.toLowerCase()}`}>
                      {STATUS_LABELS[review.status]}
                    </span>
                  </div>

                  <div className="review-item-meta">
                    <span>
                      SHA: <code>{formatShortSha(review.headSha)}</code>
                    </span>
                    <span>Findings: {review.findingsCount}</span>
                    <span>Criada em: {formatDateTime(review.createdAt)}</span>
                  </div>

                  {review.latestApprovalDecision && (
                    <div className="review-item-approval">
                      <span
                        className={`badge badge-approval-${review.latestApprovalDecision.toLowerCase()}`}
                      >
                        {APPROVAL_LABELS[review.latestApprovalDecision]}
                      </span>
                    </div>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

