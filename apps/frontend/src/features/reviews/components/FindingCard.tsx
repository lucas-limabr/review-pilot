import type { FindingItem } from '../../../shared/types/api';
import {
  CATEGORY_LABELS,
  ORIGIN_LABELS,
  SEVERITY_LABELS,
  formatConfidence,
} from '../../../shared/utils/formatters';

interface FindingCardProps {
  finding: FindingItem;
}

export function FindingCard({ finding }: FindingCardProps) {
  const hasLocation = Boolean(finding.filePath);
  const lineRange =
    finding.startLine && finding.endLine && finding.endLine !== finding.startLine
      ? `Linhas ${finding.startLine}–${finding.endLine}`
      : finding.startLine
        ? `Linha ${finding.startLine}`
        : null;

  return (
    <article className="finding-card" aria-label={`Finding: ${finding.title}`}>
      <header className="finding-header">
        <div className="finding-badges">
          <span className={`badge badge-severity-${finding.severity.toLowerCase()}`}>
            {SEVERITY_LABELS[finding.severity]}
          </span>
          <span className="badge badge-category">{CATEGORY_LABELS[finding.category]}</span>
          <span className="badge badge-origin">{ORIGIN_LABELS[finding.origin]}</span>
          {finding.externalReference && (
            <span className="badge badge-rule" title="Regra ou referência estática">
              Regra: <code>{finding.externalReference}</code>
            </span>
          )}
        </div>
        <span className="finding-confidence">{formatConfidence(finding.confidence)}</span>
      </header>

      <h4 className="finding-title">{finding.title}</h4>

      <div className="finding-location">
        <strong>Localização: </strong>
        {hasLocation ? (
          <code>
            {finding.filePath}
            {lineRange ? ` (${lineRange})` : ''}
          </code>
        ) : (
          <span>Observação geral do Pull Request (sem linha inline específica)</span>
        )}
      </div>

      <p className="finding-description">{finding.description}</p>

      <div className="finding-block">
        <strong>Evidência verificada:</strong>
        <pre className="finding-code">{finding.evidence}</pre>
      </div>

      <div className="finding-block finding-recommendation">
        <strong>Recomendação técnica:</strong>
        <p>{finding.recommendation}</p>
      </div>
    </article>
  );
}

