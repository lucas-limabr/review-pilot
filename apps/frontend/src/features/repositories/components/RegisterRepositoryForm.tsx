import { type FormEvent, useState } from 'react';
import { ApiClientError } from '../../../shared/api/httpClient';
import type { RegisterRepositoryPayload, RepositoryItem } from '../../../shared/types/api';
import { repositoryService } from '../api/repositoryService';

interface RegisterRepositoryFormProps {
  onRegistered: (repository: RepositoryItem) => void;
  onCancel: () => void;
}

export function RegisterRepositoryForm({ onRegistered, onCancel }: RegisterRepositoryFormProps) {
  const [externalRepositoryId, setExternalRepositoryId] = useState('');
  const [owner, setOwner] = useState('');
  const [name, setName] = useState('auto-elite');
  const [defaultBranch, setDefaultBranch] = useState('main');
  const [sonarProjectKey, setSonarProjectKey] = useState('auto-elite');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);

    const parsedExternalId = Number(externalRepositoryId);
    if (!Number.isInteger(parsedExternalId) || parsedExternalId < 1) {
      setErrorMessage('Informe um ID numérico positivo do repositório no GitHub.');
      return;
    }

    const payload: RegisterRepositoryPayload = {
      externalRepositoryId: parsedExternalId,
      owner: owner.trim(),
      name: name.trim(),
      defaultBranch: defaultBranch.trim() || 'main',
      sonarProjectKey: sonarProjectKey.trim() ? sonarProjectKey.trim() : null,
    };

    try {
      setSubmitting(true);
      const created = await repositoryService.registerRepository(payload);
      onRegistered(created);
    } catch (err) {
      if (err instanceof ApiClientError) {
        setErrorMessage(err.detail);
      } else {
        setErrorMessage('Erro inesperado ao cadastrar repositório.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card-form" onSubmit={handleSubmit} aria-label="Cadastrar repositório permitido">
      <h3>Cadastrar Repositório na Allowlist</h3>
      <p className="muted-text">
        Apenas repositórios cadastrados e ativos são aceitos pelo webhook do ReviewPilot.
      </p>

      {errorMessage && (
        <div className="inline-alert inline-alert-error" role="alert">
          {errorMessage}
        </div>
      )}

      <div className="form-grid">
        <div className="form-field">
          <label htmlFor="repo-external-id">GitHub Repository ID *</label>
          <input
            id="repo-external-id"
            type="number"
            min={1}
            required
            placeholder="Ex.: 123456789"
            value={externalRepositoryId}
            onChange={(e) => setExternalRepositoryId(e.target.value)}
          />
        </div>

        <div className="form-field">
          <label htmlFor="repo-owner">Owner / Organização *</label>
          <input
            id="repo-owner"
            type="text"
            required
            placeholder="Ex.: lucaslima"
            value={owner}
            onChange={(e) => setOwner(e.target.value)}
          />
        </div>

        <div className="form-field">
          <label htmlFor="repo-name">Nome do Repositório *</label>
          <input
            id="repo-name"
            type="text"
            required
            placeholder="Ex.: auto-elite"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        <div className="form-field">
          <label htmlFor="repo-branch">Branch Padrão *</label>
          <input
            id="repo-branch"
            type="text"
            required
            value={defaultBranch}
            onChange={(e) => setDefaultBranch(e.target.value)}
          />
        </div>

        <div className="form-field form-field-full">
          <label htmlFor="repo-sonar-key">SonarQube Project Key (opcional — profile quality)</label>
          <input
            id="repo-sonar-key"
            type="text"
            placeholder="Ex.: auto-elite"
            value={sonarProjectKey}
            onChange={(e) => setSonarProjectKey(e.target.value)}
          />
        </div>
      </div>

      <div className="form-actions">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={submitting}>
          Cancelar
        </button>
        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? 'Salvando...' : 'Salvar Repositório'}
        </button>
      </div>
    </form>
  );
}

