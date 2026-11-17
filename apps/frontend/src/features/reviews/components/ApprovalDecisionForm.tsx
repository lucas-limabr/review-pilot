import { type FormEvent, useState } from 'react';
import { ApiClientError } from '../../../shared/api/httpClient';
import type { ApprovalDecision, ReviewExecutionStatus } from '../../../shared/types/api';
import { APPROVAL_LABELS } from '../../../shared/utils/formatters';
import { reviewService } from '../api/reviewService';

interface ApprovalDecisionFormProps {
  reviewExecutionId: string;
  reviewStatus: ReviewExecutionStatus;
  latestApprovalDecision: ApprovalDecision | null;
  onDecisionRecorded: () => void;
}

export function ApprovalDecisionForm({
  reviewExecutionId,
  reviewStatus,
  latestApprovalDecision,
  onDecisionRecorded,
}: ApprovalDecisionFormProps) {
  const [actor, setActor] = useState('lucaslima');
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const canDecide = reviewStatus === 'COMPLETED' || reviewStatus === 'PARTIAL';

  async function handleDecision(decision: ApprovalDecision, event?: FormEvent) {
    if (event) {
      event.preventDefault();
    }
    if (!canDecide) {
      return;
    }

    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      setSubmitting(true);
      await reviewService.submitApproval(reviewExecutionId, {
        decision,
        actor: actor.trim() || 'local-reviewer',
        comment: comment.trim() || undefined,
      });
      setComment('');
      setSuccessMessage(
        `Decisão '${APPROVAL_LABELS[decision]}' registrada localmente no ReviewPilot (sem publicação externa no GitHub).`,
      );
      onDecisionRecorded();
    } catch (err) {
      if (err instanceof ApiClientError) {
        setErrorMessage(err.detail);
      } else {
        setErrorMessage('Falha inesperada ao registrar decisão humana.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="approval-box" aria-label="Decisão humana sobre a revisão">
      <div className="approval-header">
        <div>
          <h3>Decisão Humana (Auditoria Interna ReviewPilot)</h3>
          <p className="muted-text">
            Registra sua aprovação ou rejeição da revisão no banco de dados do ReviewPilot. Nenhuma
            ação de escrita ou comentário é enviada ao GitHub no MVP.
          </p>
        </div>
        {latestApprovalDecision && (
          <span className={`badge badge-approval-${latestApprovalDecision.toLowerCase()}`}>
            Estado atual: {APPROVAL_LABELS[latestApprovalDecision]}
          </span>
        )}
      </div>

      {!canDecide ? (
        <p className="muted-text">
          A decisão humana só pode ser registrada quando a revisão está no status{' '}
          <strong>Concluída</strong> ou <strong>Concluída (Parcial)</strong>.
        </p>
      ) : (
        <form onSubmit={(e) => e.preventDefault()} className="approval-form">
          {errorMessage && (
            <div className="inline-alert inline-alert-error" role="alert">
              {errorMessage}
            </div>
          )}
          {successMessage && (
            <div className="inline-alert inline-alert-success" role="status">
              {successMessage}
            </div>
          )}

          <div className="form-grid">
            <div className="form-field">
              <label htmlFor="approval-actor">Revisor Responsável</label>
              <input
                id="approval-actor"
                type="text"
                value={actor}
                onChange={(e) => setActor(e.target.value)}
                placeholder="Ex.: lucaslima"
              />
            </div>

            <div className="form-field form-field-full">
              <label htmlFor="approval-comment">Observação da Decisão (opcional)</label>
              <textarea
                id="approval-comment"
                rows={2}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Justificativa ou observação técnica sobre os findings..."
              />
            </div>
          </div>

          <div className="form-actions">
            <button
              type="button"
              className="btn btn-danger"
              disabled={submitting}
              onClick={() => void handleDecision('REJECTED')}
            >
              Rejeitar Revisão
            </button>
            <button
              type="button"
              className="btn btn-success"
              disabled={submitting}
              onClick={() => void handleDecision('APPROVED')}
            >
              Aprovar Revisão
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

