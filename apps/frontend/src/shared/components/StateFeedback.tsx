interface LoadingStateProps {
  message?: string;
}

export function LoadingState({ message = 'Carregando dados...' }: LoadingStateProps) {
  return (
    <div className="state-box state-loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = 'Não foi possível carregar os dados',
  message,
  onRetry,
}: ErrorStateProps) {
  return (
    <div className="state-box state-error" role="alert">
      <div className="state-error-content">
        <strong>{title}</strong>
        <p>{message}</p>
      </div>
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Tentar novamente
        </button>
      )}
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  description: string;
}

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="state-box state-empty">
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
}

