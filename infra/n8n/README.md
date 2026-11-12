# Infraestrutura n8n — ReviewPilot

Este diretório armazena os artefatos versionados do runtime do agente no **n8n Community Edition** (`docker.n8n.io/n8nio/n8n:2.47.4`).

## Estrutura

- `workflows/main/`: exportação JSON sanitizada do workflow principal (`ReviewPilot - Analyze Pull Request`).
- `workflows/tools/`: exportações JSON sanitizadas dos subworkflows de leitura MCP expostos via `Call n8n Workflow Tool`.
- `prompts/review_agent.md`: fonte de verdade versionada do System Message configurado no nó `AI Agent` (*Tools Agent*).

## Regras de Segurança e Versionamento

1. **Zero Segredos nas Exportações:** antes de salvar qualquer JSON em `workflows/`, remova ou sanitize chaves, tokens, cabeçalhos sensíveis, dados de execução (*pinned data* reais) e identificadores sensíveis.
2. **Persistência Isolada:** o n8n utiliza o volume Docker `reviewpilot_n8n_data` (`/home/node/.n8n`) e nunca acessa diretamente o banco PostgreSQL de negócio do ReviewPilot.
3. **Chave de Criptografia Estável:** a variável `N8N_ENCRYPTION_KEY` do `.env` criptografa as credenciais da Gemini API, GitHub PAT, SonarQube Token e `INTERNAL_SERVICE_TOKEN`. Nunca altere `N8N_ENCRYPTION_KEY` após cadastrar credenciais.

