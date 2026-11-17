import { useState } from 'react';
import { EmptyState, ErrorState, LoadingState } from '../../../shared/components/StateFeedback';
import type { RepositoryItem } from '../../../shared/types/api';
import { RegisterRepositoryForm } from './RegisterRepositoryForm';

interface RepositoryListSectionProps {
  repositories: RepositoryItem[];
  selectedRepositoryId: string | null;
  loading: boolean;
  error: string | null;
  onSelectRepository: (repository: RepositoryItem) => void;
  onRepositoryRegistered: (repository: RepositoryItem) => void;
  onRetry: () => void;
}

export function RepositoryListSection({
  repositories,
  selectedRepositoryId,
  loading,
  error,
  onSelectRepository,
  onRepositoryRegistered,
  onRetry,
}: RepositoryListSectionProps) {
  const [showRegisterForm, setShowRegisterForm] = useState(false);

  return (
    <section className="panel-section" aria-label="Repositórios Monitorados">
      <div className="section-header">
        <div>
          <h2>Repositórios Monitorados</h2>
          <span className="muted-text">Selecione um repositório para listar as revisões</span>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setShowRegisterForm((prev) => !prev)}
        >
          {showRegisterForm ? 'Fechar formulário' : '+ Novo Repositório'}
        </button>
      </div>

      {showRegisterForm && (
        <RegisterRepositoryForm
          onRegistered={(created) => {
            setShowRegisterForm(false);
            onRepositoryRegistered(created);
          }}
          onCancel={() => setShowRegisterForm(false)}
        />
      )}

      {loading && repositories.length === 0 ? (
        <LoadingState message="Carregando repositórios..." />
      ) : error && repositories.length === 0 ? (
        <ErrorState
          title="Erro ao carregar repositórios"
          message={error}
          onRetry={onRetry}
        />
      ) : repositories.length === 0 ? (
        <EmptyState
          title="Nenhum repositório cadastrado"
          description="Clique em '+ Novo Repositório' para adicionar o repositório auto-elite à allowlist do ReviewPilot."
        />
      ) : (
        <ul className="repo-list" aria-label="Lista de repositórios">
          {repositories.map((repo) => {
            const isSelected = repo.id === selectedRepositoryId;
            return (
              <li key={repo.id}>
                <button
                  type="button"
                  className={`repo-item-btn ${isSelected ? 'repo-item-selected' : ''}`}
                  onClick={() => onSelectRepository(repo)}
                  aria-pressed={isSelected}
                >
                  <div className="repo-item-top">
                    <strong>{repo.fullName}</strong>
                    <span className={`badge ${repo.active ? 'badge-active' : 'badge-inactive'}`}>
                      {repo.active ? 'Ativo' : 'Inativo'}
                    </span>
                  </div>
                  <div className="repo-item-meta">
                    <span>Branch: {repo.defaultBranch}</span>
                    <span>
                      SonarQube:{' '}
                      {repo.sonarProjectKey ? <code>{repo.sonarProjectKey}</code> : 'Não configurado'}
                    </span>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

