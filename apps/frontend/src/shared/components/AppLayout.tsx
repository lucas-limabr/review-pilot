import type { ReactNode } from 'react';
import { resolveApiBaseUrl } from '../api/httpClient';

interface AppLayoutProps {
  onRefreshAll: () => void;
  isRefreshing: boolean;
  children: ReactNode;
}

export function AppLayout({ onRefreshAll, isRefreshing, children }: AppLayoutProps) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header-brand">
          <div className="brand-badge" aria-hidden="true">
            RP
          </div>
          <div>
            <h1>ReviewPilot</h1>
            <p className="subtitle">
              Revisão Assistida de Pull Requests via n8n AI Agent, Gemini e Servidores MCP Read-Only
            </p>
          </div>
        </div>
        <div className="app-header-actions">
          <span className="env-pill" title="Endpoint base configurado para a API do backend">
            API: <code>{resolveApiBaseUrl()}</code>
          </span>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={onRefreshAll}
            disabled={isRefreshing}
          >
            {isRefreshing ? 'Atualizando...' : 'Atualizar painel'}
          </button>
        </div>
      </header>

      <main className="app-main">{children}</main>

      <footer className="app-footer">
        <span>
          <strong>Modo somente leitura no GitHub/SonarQube:</strong> aprovações registradas neste
          painel são auditadas no banco do ReviewPilot e não publicam comentários nem realizam merge
          no GitHub.
        </span>
      </footer>
    </div>
  );
}

