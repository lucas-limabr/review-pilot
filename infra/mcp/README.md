# Servidores MCP (Model Context Protocol) — ReviewPilot (Fases 6 e 7)

Documentação de transporte, autenticação, ferramentas autorizadas e restrições de segurança dos servidores MCP consumidos pelo `Review Agent` no n8n (`2.47.4`).

---

## 1. GitHub MCP Server (Remoto Oficial — Fase 6)

- **Endpoint:** `https://api.githubcopilot.com/mcp/`
- **Transporte Validado no n8n `2.47.4`:** `HTTP Streamable` (`serverTransport: "httpStreamable"`) no nó `@n8n/n8n-nodes-langchain.mcpClient` (`typeVersion: 1.1`).
- **Tipo de Credencial no n8n:** `Multiple Headers Auth` (`httpMultipleHeadersAuth`), nomeada `ReviewPilot GitHub MCP Read-Only`.
- **Cabeçalhos Obrigatórios (armazenados exclusivamente no cofre criptografado do n8n):**
  - `Authorization`: `Bearer <GITHUB_FINE_GRAINED_PAT>`
  - `X-MCP-Readonly`: `true`
  - `X-MCP-Toolsets`: `repos,pull_requests`
- **Escopo do Token:** Fine-Grained Personal Access Token restrito exclusivamente ao repositório `auto-elite`, somente permissões `Read-only` (`Metadata: Read-only`, `Contents: Read-only`, `Pull requests: Read-only`).

### 1.1. Subworkflows e Ferramentas de Leitura Autorizadas (`infra/n8n/workflows/tools/`)

| Ferramenta Exposta ao Agente (`Call n8n Workflow Tool`) | Subworkflow (`infra/n8n/workflows/tools/`) | Ferramenta Remota (`github-mcp-server`) | Parâmetros Aceitos e Validação Determinística |
|---|---|---|---|
| `github_pr_read_tool` | [`github-mcp-pull-request-read.workflow.json`](../n8n/workflows/tools/github-mcp-pull-request-read.workflow.json) (`ReviewPilot Tool - GitHub PR Read`) | `pull_request_read` | `owner`, `repo`, `pullNumber >= 1`, `method` restrito por regex exclusivamente a `get`, `get_files`, `get_diff` ou `get_commits`. |
| `github_get_file_contents_tool` | [`github-mcp-get-file-contents.workflow.json`](../n8n/workflows/tools/github-mcp-get-file-contents.workflow.json) (`ReviewPilot Tool - GitHub Get File Contents`) | `get_file_contents` | `owner`, `repo`, `path` (bloqueia `..` path traversal) e `ref` (SHA ou branch). |
| `github_list_commits_tool` | [`github-mcp-list-commits.workflow.json`](../n8n/workflows/tools/github-mcp-list-commits.workflow.json) (`ReviewPilot Tool - GitHub List Commits`) | `list_commits` | `owner`, `repo`, `sha` e `perPage` (limitado de `1` a `50`). |

---

## 2. SonarQube MCP Server (Container Interno Opcional — Fase 7)

- **Imagem:** `sonarsource/sonarqube-mcp:1.21.0.2975` (profile `quality`)
- **Endpoint na rede interna (`reviewpilot-net`):** `http://sonarqube-mcp:8080/mcp`
- **Transporte:** `HTTP Streamable` (`SONARQUBE_TRANSPORT=http`, `SONARQUBE_READ_ONLY=true`, `SONARQUBE_TOOLSETS=issues,quality-gates,rules,projects`)
- **Tipo de Credencial no n8n:** `Bearer Auth` (`httpBearerAuth`), nomeada `ReviewPilot SonarQube MCP Read-Only` (`Authorization: Bearer <SONARQUBE_USER_TOKEN>`).
- **Exposição:** acessível apenas dentro da rede Docker `reviewpilot-net` (`expose: ["8080"]`), sem porta publicada no host.

### 2.1. Subworkflows e Ferramentas de Leitura Autorizadas (`infra/n8n/workflows/tools/`)

| Ferramenta Exposta ao Agente (`Call n8n Workflow Tool`) | Subworkflow (`infra/n8n/workflows/tools/`) | Ferramenta Remota (`sonarqube-mcp-server`) | Parâmetros Aceitos e Degradação Graciosa |
|---|---|---|---|
| `sonarqube_search_issues_tool` | [`sonarqube-mcp-search-issues.workflow.json`](../n8n/workflows/tools/sonarqube-mcp-search-issues.workflow.json) (`ReviewPilot Tool - SonarQube Search Issues`) | `search_sonar_issues_in_projects` | `projectKey`, `pageIndex`. Retorna `UNAVAILABLE_OPTIONAL` se o SonarQube estiver desligado. |
| `sonarqube_quality_gate_tool` | [`sonarqube-mcp-quality-gate.workflow.json`](../n8n/workflows/tools/sonarqube-mcp-quality-gate.workflow.json) (`ReviewPilot Tool - SonarQube Quality Gate Status`) | `get_project_quality_gate_status` | `projectKey`. Retorna `UNAVAILABLE_OPTIONAL` se o SonarQube estiver desligado. |
| `sonarqube_show_rule_tool` | [`sonarqube-mcp-show-rule.workflow.json`](../n8n/workflows/tools/sonarqube-mcp-show-rule.workflow.json) (`ReviewPilot Tool - SonarQube Show Rule`) | `show_rule` | `ruleKey` (ex.: `java:S2259`). Retorna `UNAVAILABLE_OPTIONAL` se o SonarQube estiver desligado. |

**Ferramentas SonarQube explicitamente bloqueadas/excluídas:** `change_sonar_issue_status`, `analyze_code_snippet`, `architecture_set`.

---

## 3. Regra de Compatibilidade no n8n (`2.47.4`)

- O nó interno de Registro MCP (`@n8n/n8n-nodes-langchain.mcpRegistryClientTool`) no n8n `2.47.4` aceita apenas `mcpOAuth2Api`.
- Tanto para o GitHub MCP quanto para o SonarQube MCP, utiliza-se o padrão oficial das Seções 11.1, 11.2 e 11.3: **subworkflows dedicados com o nó `MCP Client` (`serverTransport: "httpStreamable"`) expostos ao `Review Agent` via `Call n8n Workflow Tool` (`@n8n/n8n-nodes-langchain.toolWorkflow` v2.2)**.
- Nunca preencher um endpoint `Streamable HTTP` em um campo rotulado exclusivamente como `SSE Endpoint`.
