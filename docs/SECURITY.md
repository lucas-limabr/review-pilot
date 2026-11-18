# ReviewPilot — Política de Segurança

## 1. Gestão de Segredos

1. **Arquivo `.env` local (nunca versionado):**
   - Armazena exclusivamente parâmetros de infraestrutura local: `POSTGRES_PASSWORD`, `INTERNAL_SERVICE_TOKEN`, `GITHUB_WEBHOOK_SECRET` e `N8N_ENCRYPTION_KEY`.
   - O arquivo `.env.example` contém apenas placeholders `CHANGE_ME_LOCALLY`.
   - Os scripts de inicialização (`scripts/dev-up.sh`) e o backend rejeitam inicialização caso qualquer variável obrigatória permaneça com o valor `CHANGE_ME_LOCALLY`.
2. **Cofre criptografado do n8n (`N8N_ENCRYPTION_KEY`):**
   - Chaves de provedores externos utilizados pelo agente (**Gemini API Key**, **GitHub Fine-Grained PAT** e **SonarQube User Token**) são cadastradas exclusivamente no gerenciador de credenciais do n8n, nunca em `.env`, código-fonte, prompts ou workflows exportados.
   - `N8N_ENCRYPTION_KEY` deve ser gerada com `openssl rand -hex 32` antes do primeiro uso e mantida estável para preservar a leitura das credenciais persistidas no volume `reviewpilot_n8n_data`.

## 2. Segurança do Webhook GitHub e Endpoints Internos

- **Webhook GitHub (`POST /api/v1/webhooks/github`):**
  - Validação obrigatória de `X-Hub-Signature-256` (HMAC-SHA256 sobre o corpo bruto da requisição) com comparação em tempo constante.
  - Validação de `X-GitHub-Event`, `X-GitHub-Delivery` (idempotência com restrição única) e verificação do repositório contra a allowlist cadastrada.
- **Comunicação Backend ↔ n8n:**
  - Protegida por token de serviço (`INTERNAL_SERVICE_TOKEN`) enviado em header autenticado tanto no dispatch (`POST http://n8n:5678/webhook/reviewpilot-dispatch`) quanto nos callbacks (`POST /api/internal/v1/reviews/{id}/results` e `/failure`).

## 3. Restrições do Agente e Servidores MCP (Modo Somente Leitura)

- **GitHub Fine-Grained PAT:** escopo restrito apenas ao repositório de demonstração (`reviewpilot-sandbox`), com permissões mínimas de leitura (`Metadata: Read-only`, `Contents: Read-only`, `Pull requests: Read-only`).
- **GitHub MCP Server:** cabeçalhos obrigatórios `X-MCP-Readonly: true` e `X-MCP-Toolsets: repos,pull_requests`.
- **SonarQube MCP Server:** executado com `SONARQUBE_READ_ONLY=true` e acessível apenas na rede interna `reviewpilot-net` (sem porta publicada no host).
- **Allowlist de Ferramentas:** o nó `AI Agent` recebe apenas ferramentas de leitura explicitamente selecionadas. Nenhuma ferramenta de escrita (comentários, criação de branch, push, merge) é conectada ao workflow de análise no MVP.

## 4. Defesa contra Prompt Injection

Todo conteúdo proveniente do repositório analisado (código, diffs, títulos/descrições de PR, mensagens de commit, arquivos Markdown e issues do SonarQube) é tratado como **entrada não confiável**:
- O System Message do `Review Agent` proíbe seguir instruções contidas nos artefatos analisados.
- Credenciais jamais são injetadas no prompt ou no contexto do modelo.
- O backend valida novamente o JSON retornado pelo workflow (schema, enums, tamanhos máximos, caminhos de arquivo e linhas do diff) antes de persistir qualquer finding.

