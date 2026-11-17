# Infraestrutura n8n — Acesso, Segurança, Gemini, MCP e Review Agent (Fases 4 a 7)

Este diretório contém os workflows exportados, o prompt versionado (`prompts/review_agent.md`) e as instruções operacionais da instância self-hosted do **n8n Community Edition** (`docker.n8n.io/n8nio/n8n:2.47.4`), que atua como orquestrador e runtime exclusivo do `Review Agent`.

---

## 1. Configuração de Segurança e Persistência (`compose.yaml`)

- **Imagem fixada:** `docker.n8n.io/n8nio/n8n:2.47.4` (sem uso de tag `latest`).
- **Volume persistente isolado:** `reviewpilot_n8n_data` montado em `/home/node/.n8n`. O armazenamento interno do n8n é completamente separado do banco PostgreSQL de negócio do ReviewPilot.
- **Criptografia de credenciais:** a variável obrigatória `N8N_ENCRYPTION_KEY` do `.env` criptografa todas as credenciais salvas no cofre interno do n8n. **Nunca altere `N8N_ENCRYPTION_KEY` após criar credenciais**.
- **Hardening de ambiente:** `N8N_BLOCK_ENV_ACCESS_IN_NODE="true"`, `N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS="true"`, `N8N_DIAGNOSTICS_ENABLED="false"`, `N8N_PERSONALIZATION_ENABLED="false"` e `extra_hosts: ["host.docker.internal:host-gateway"]`.

---

## 2. Credenciais Criptografadas no Cofre do n8n

| Nome da Credencial no n8n | Tipo de Credencial no n8n | Placeholder no JSON Exportado | Finalidade |
|---|---|---|---|
| `ReviewPilot Internal Service Auth` | `Header Auth` (`httpHeaderAuth`) | `CREDENTIAL_ID_INTERNAL_SERVICE_AUTH` | Autentica o webhook `/webhook/reviewpilot-dispatch` e os callbacks HTTP ao backend (`X-Internal-Service-Token`). |
| `ReviewPilot Google Gemini API` | `Google Gemini(PaLM) Api` (`googlePalmApi`) | `CREDENTIAL_ID_GOOGLE_GEMINI` | Autentica o subnó `Google Gemini Chat Model` (`models/gemini-3.5-flash-lite`). |
| `ReviewPilot GitHub MCP Read-Only` | `Multiple Headers Auth` (`httpMultipleHeadersAuth`) | `CREDENTIAL_ID_GITHUB_MCP_HEADERS` | Autentica os 3 subworkflows GitHub MCP (`Authorization: Bearer <PAT>`, `X-MCP-Readonly: true`, `X-MCP-Toolsets: repos,pull_requests`). |
| `ReviewPilot SonarQube MCP Read-Only` | `Bearer Auth` (`httpBearerAuth`) | `CREDENTIAL_ID_SONARQUBE_MCP_BEARER` | *(Opcional — profile `quality`)* Autentica os 3 subworkflows SonarQube MCP (`http://sonarqube-mcp:8080/mcp`). |

---

## 3. Catálogo Completo de Ferramentas MCP Conectadas ao `Review Agent` (Fases 6 e 7)

| Subnó `Call n8n Workflow Tool` no Workflow Principal | Arquivo do Subworkflow (`infra/n8n/workflows/tools/`) | Servidor MCP | Ferramenta Remota | Obrigatório / Opcional |
|---|---|---|---|---|
| `github_pr_read_tool` | [`workflows/tools/github-mcp-pull-request-read.workflow.json`](workflows/tools/github-mcp-pull-request-read.workflow.json) | GitHub MCP (`https://api.githubcopilot.com/mcp/`) | `pull_request_read` (`get`, `get_files`, `get_diff`, `get_commits`) | Obrigatório |
| `github_get_file_contents_tool` | [`workflows/tools/github-mcp-get-file-contents.workflow.json`](workflows/tools/github-mcp-get-file-contents.workflow.json) | GitHub MCP (`https://api.githubcopilot.com/mcp/`) | `get_file_contents` | Obrigatório |
| `github_list_commits_tool` | [`workflows/tools/github-mcp-list-commits.workflow.json`](workflows/tools/github-mcp-list-commits.workflow.json) | GitHub MCP (`https://api.githubcopilot.com/mcp/`) | `list_commits` | Obrigatório |
| `sonarqube_search_issues_tool` | [`workflows/tools/sonarqube-mcp-search-issues.workflow.json`](workflows/tools/sonarqube-mcp-search-issues.workflow.json) | SonarQube MCP (`http://sonarqube-mcp:8080/mcp`) | `search_sonar_issues_in_projects` | Opcional (`UNAVAILABLE_OPTIONAL` quando desligado) |
| `sonarqube_quality_gate_tool` | [`workflows/tools/sonarqube-mcp-quality-gate.workflow.json`](workflows/tools/sonarqube-mcp-quality-gate.workflow.json) | SonarQube MCP (`http://sonarqube-mcp:8080/mcp`) | `get_project_quality_gate_status` | Opcional (`UNAVAILABLE_OPTIONAL` quando desligado) |
| `sonarqube_show_rule_tool` | [`workflows/tools/sonarqube-mcp-show-rule.workflow.json`](workflows/tools/sonarqube-mcp-show-rule.workflow.json) | SonarQube MCP (`http://sonarqube-mcp:8080/mcp`) | `show_rule` | Opcional (`UNAVAILABLE_OPTIONAL` quando desligado) |

> **Nota sobre ativação (`Active` vs `Inactive`):**
> - Os 6 subworkflows em `workflows/tools/` e os 3 workflows de diagnóstico em `workflows/main/*-diagnostic.workflow.json` **podem permanecer `Inactive`**.
> - Apenas o workflow principal **`ReviewPilot - Analyze Pull Request`** (e opcionalmente o `ReviewPilot - Global Error Handler`) deve estar **`Active`** para expor o endpoint de produção `/webhook/reviewpilot-dispatch`.

---

## 4. Construção do `Review Agent` e Versionamento de Prompt (Fase 7)

- **Arquivo-fonte do System Prompt:** [`prompts/review_agent.md`](prompts/review_agent.md) (`promptVersion: 1.0.0`).
- **Sincronização garantida por teste automatizado:** o teste `N8nWorkflowExportSanitizationTest` verifica que o parâmetro `options.systemMessage` do nó `Review Agent` em [`workflows/main/reviewpilot-analyze-pr.workflow.json`](workflows/main/reviewpilot-analyze-pr.workflow.json) é idêntico byte a byte ao conteúdo de [`prompts/review_agent.md`](prompts/review_agent.md).
- **Configuração do nó `Review Agent`:**
  - Tipo: `@n8n/n8n-nodes-langchain.agent` (`typeVersion: 1.7`, *Tools Agent*)
  - `maxIterations`: `10`
  - `onError`: `continueErrorOutput` (encaminha falhas para `Build Failure Payload` → `Report Execution Failure`).
- **Topologia completa do workflow principal (`ReviewPilot - Analyze Pull Request`):**

```text
[Receive Review Dispatch] (POST /webhook/reviewpilot-dispatch, Header Auth)
           │
           ▼
[Validate Dispatch Payload] (IF: reviewExecutionId, repositoryFullName, pullRequestNumber, expectedHeadSha, baseBranch)
     ├── False ──► [Reject Invalid Dispatch Payload] (HTTP 422)
     └── True  ──► [Acknowledge Dispatch Accepted] (HTTP 202)
                         │
                         ▼
               [Prepare Agent Context] (Extrai owner, repo, PR, SHA, sonarProjectKey, schemaVersion=1.0, promptVersion=1.0.0)
                         │
                         ▼
                   [Review Agent] (@n8n/n8n-nodes-langchain.agent, maxIterations=10)
                     ▲   ▲   ▲
                     │   │   └── ai_tool: sonarqube_search_issues_tool, sonarqube_quality_gate_tool, sonarqube_show_rule_tool
                     │   └────── ai_tool: github_pr_read_tool, github_get_file_contents_tool, github_list_commits_tool
                     └────────── ai_languageModel: [Google Gemini Chat Model] (models/gemini-3.5-flash-lite, temp=0.1, maxTokens=4096)
                     │
       ├── Success ──► [Validate and Normalize Result] ──► [Submit Review Result] (POST /api/internal/v1/reviews/{id}/results)
       └── Error   ──► [Build Failure Payload]         ──► [Report Execution Failure] (POST /api/internal/v1/reviews/{id}/failure)
```

---

## 5. Passo a Passo de Importação e Associação na Fase 7

1. **Importe os 3 subworkflows SonarQube MCP** (`Workflows` → `Add Workflow` → `Import from File...`):
   - [`workflows/tools/sonarqube-mcp-search-issues.workflow.json`](workflows/tools/sonarqube-mcp-search-issues.workflow.json)
   - [`workflows/tools/sonarqube-mcp-quality-gate.workflow.json`](workflows/tools/sonarqube-mcp-quality-gate.workflow.json)
   - [`workflows/tools/sonarqube-mcp-show-rule.workflow.json`](workflows/tools/sonarqube-mcp-show-rule.workflow.json)
   - *(Se estiver usando o profile `quality` com SonarQube ativo, selecione a credencial `ReviewPilot SonarQube MCP Read-Only` no nó `SonarQube MCP - ...` de cada um; se o SonarQube não estiver ativo, basta salvá-los — eles retornarão `UNAVAILABLE_OPTIONAL` automaticamente em caso de chamada).*
2. **Reimporte o workflow principal [`workflows/main/reviewpilot-analyze-pr.workflow.json`](workflows/main/reviewpilot-analyze-pr.workflow.json):**
   - Confirme as credenciais nos nós:
     - `Receive Review Dispatch`, `Submit Review Result`, `Report Execution Failure` → `ReviewPilot Internal Service Auth`
     - `Google Gemini Chat Model` → `ReviewPilot Google Gemini API` (`models/gemini-3.5-flash-lite`)
   - Nos 6 subnós `Call n8n Workflow Tool` conectados ao `Review Agent`, selecione no dropdown **Workflow** os respectivos subworkflows importados na sua instância:
     - `github_pr_read_tool` → `ReviewPilot Tool - GitHub PR Read`
     - `github_get_file_contents_tool` → `ReviewPilot Tool - GitHub Get File Contents`
     - `github_list_commits_tool` → `ReviewPilot Tool - GitHub List Commits`
     - `sonarqube_search_issues_tool` → `ReviewPilot Tool - SonarQube Search Issues`
     - `sonarqube_quality_gate_tool` → `ReviewPilot Tool - SonarQube Quality Gate Status`
     - `sonarqube_show_rule_tool` → `ReviewPilot Tool - SonarQube Show Rule`
   - Salve e mantenha o workflow **`ReviewPilot - Analyze Pull Request`** marcado como **`Active`**.
