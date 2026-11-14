# Infraestrutura n8n — Acesso, Segurança, Gemini, MCP e Manutenção (Fases 4 a 6)

Este diretório contém os workflows exportados, prompts e instruções operacionais da instância self-hosted do **n8n Community Edition** (`docker.n8n.io/n8nio/n8n:2.47.4`), que atua como orquestrador e runtime exclusivo do `Review Agent`.

---

## 1. Configuração de Segurança e Persistência (`compose.yaml`)

- **Imagem fixada:** `docker.n8n.io/n8nio/n8n:2.47.4` (sem uso de tag `latest`).
- **Volume persistente isolado:** `reviewpilot_n8n_data` montado em `/home/node/.n8n`. O armazenamento interno do n8n é completamente separado do banco PostgreSQL de negócio do ReviewPilot.
- **Criptografia de credenciais:** a variável obrigatória `N8N_ENCRYPTION_KEY` do `.env` criptografa todas as credenciais salvas no cofre interno do n8n. **Nunca altere `N8N_ENCRYPTION_KEY` após criar credenciais**, caso contrário o n8n não conseguirá descriptografá-las ao reiniciar.
- **Hardening de ambiente:**
  - `N8N_BLOCK_ENV_ACCESS_IN_NODE="true"`: bloqueia acesso a variáveis de ambiente do processo dentro de expressões e nós `Code`, prevenindo exfiltração de `N8N_ENCRYPTION_KEY` via prompt injection.
  - `N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS="true"`, `N8N_DIAGNOSTICS_ENABLED="false"` e `N8N_PERSONALIZATION_ENABLED="false"`.
  - `extra_hosts: ["host.docker.internal:host-gateway"]`: permite que o container do n8n chame o backend tanto quando o backend roda diretamente no host (`http://host.docker.internal:8080`) quanto quando roda em container (`http://backend:8080`).

---

## 2. Primeiro Acesso e Cadastro da Credencial Interna (Fase 4 — Concluída)

1. Certifique-se de que os containers estão em execução:
   ```bash
   ./scripts/dev-up.sh
   ```
2. Acesse `http://localhost:5678` no navegador e conclua o cadastro inicial da conta **Owner** local.
3. Em **Credentials** → **Add Credential**, selecione **Header Auth** (`httpHeaderAuth`):
   - **Credential Name:** `ReviewPilot Internal Service Auth`
   - **Name:** `X-Internal-Service-Token`
   - **Value:** valor exato de `INTERNAL_SERVICE_TOKEN` definido no `.env` local.

---

## 3. Configuração da Credencial Google Gemini e Parâmetros do Modelo (Fase 5 — Concluída)

| Parâmetro | Valor Configurado | Racional Técnico |
|---|---|---|
| Tipo do Nó | `@n8n/n8n-nodes-langchain.lmChatGoogleGemini` (`v1`) | Subnó nativo LangChain do n8n para a Gemini Developer API |
| Credencial no n8n | `ReviewPilot Google Gemini API` (`googlePalmApi`) | Armazenada criptografada no cofre do n8n |
| Modelo Validado (`modelName`) | `models/gemini-3.5-flash-lite` | Modelo estável validado em `2026-11-14` (~1s de resposta, suporte a *Function Calling* e JSON estruturado) |
| Temperatura (`options.temperature`) | `0.1` | Minimiza alucinações e mantém consistência determinística na análise técnica de diffs |
| Limite de Saída (`options.maxOutputTokens`) | `4096` | Capacidade suficiente para serializar o JSON estruturado de revisão (`review-result.schema.json`) |
| Timeout do Workflow | `180s` (principal) / `60s` (diagnóstico) | Evita execuções pendentes indefinidamente em caso de lentidão externa |

---

## 4. Integração GitHub MCP Read-Only (Fase 6 — `PENDING_MANUAL`)

### 4.1. Verificação de Transporte no n8n `2.47.4`
- O nó interno de Registro MCP (`@n8n/n8n-nodes-langchain.mcpRegistryClientTool`) no n8n `2.47.4` exige `mcpOAuth2Api` e não permite enviar cabeçalhos customizados (`X-MCP-Readonly: true` e `X-MCP-Toolsets: repos,pull_requests`) com Fine-Grained PAT.
- O nó regular **`MCP Client`** (`@n8n/n8n-nodes-langchain.mcpClient`, `typeVersion: 1.1`) suporta nativamente **`Server Transport = HTTP Streamable` (`httpStreamable`)** e **`Authentication = Multiple Headers Auth` (`multipleHeadersAuth`)**.
- Conforme as Seções 11.1, 11.2 e 16.3 do guia, cada operação de leitura autorizada do GitHub MCP foi encapsulada em um **subworkflow dedicado** em `infra/n8n/workflows/tools/` e exposta ao workflow principal por subnós **`Call n8n Workflow Tool`** (`@n8n/n8n-nodes-langchain.toolWorkflow`, `typeVersion: 2.2`).

### 4.2. Catálogo de Ferramentas e Subworkflows GitHub MCP Read-Only

| Nome da Tool no Workflow Principal | Arquivo do Subworkflow (`infra/n8n/workflows/tools/`) | Nome do Subworkflow no n8n | Ferramenta Remota (`github-mcp-server`) | Descrição e Escopo de Leitura |
|---|---|---|---|---|
| `github_pr_read_tool` | [`workflows/tools/github-mcp-pull-request-read.workflow.json`](workflows/tools/github-mcp-pull-request-read.workflow.json) | `ReviewPilot Tool - GitHub PR Read` | `pull_request_read` | Consulta metadados do PR (`method="get"`), arquivos modificados (`method="get_files"`), diff unificado (`method="get_diff"`) ou commits do PR (`method="get_commits"`). Valida `owner`, `repo`, `pullNumber >= 1` e bloqueia qualquer `method` fora da allowlist. |
| `github_get_file_contents_tool` | [`workflows/tools/github-mcp-get-file-contents.workflow.json`](workflows/tools/github-mcp-get-file-contents.workflow.json) | `ReviewPilot Tool - GitHub Get File Contents` | `get_file_contents` | Lê o conteúdo de um arquivo ou diretório em um commit SHA ou branch específica (`owner`, `repo`, `path`, `ref`). Bloqueia path traversal (`..`). |
| `github_list_commits_tool` | [`workflows/tools/github-mcp-list-commits.workflow.json`](workflows/tools/github-mcp-list-commits.workflow.json) | `ReviewPilot Tool - GitHub List Commits` | `list_commits` | Lista commits recentes de uma branch ou SHA (`owner`, `repo`, `sha`, `perPage` limitado de 1 a 50). |

**Ferramentas de escrita explicitamente bloqueadas/excluídas:** `create_pull_request`, `merge_pull_request`, `update_pull_request`, `create_pull_request_review`, `add_issue_comment`, `create_or_update_file`, `push_files`, `create_branch`.

### 4.3. Cadastro da Credencial `ReviewPilot GitHub MCP Read-Only` no n8n
1. No GitHub (`Settings` → `Developer settings` → `Personal access tokens` → `Fine-grained tokens`), gere um token restrito **exclusivamente** ao repositório `auto-elite` com permissões somente leitura:
   - `Metadata: Read-only`
   - `Contents: Read-only`
   - `Pull requests: Read-only`
2. Na interface do n8n (`http://localhost:5678`), abra **Credentials** → **Add Credential** e selecione **Multiple Headers Auth** (`httpMultipleHeadersAuth`).
3. Preencha o nome da credencial como:
   - **Credential Name:** `ReviewPilot GitHub MCP Read-Only`
4. Adicione os **3 headers obrigatórios**:
   - Header 1 — **Name:** `Authorization` | **Value:** `Bearer <SEU_GITHUB_FINE_GRAINED_PAT>`
   - Header 2 — **Name:** `X-MCP-Readonly` | **Value:** `true`
   - Header 3 — **Name:** `X-MCP-Toolsets` | **Value:** `repos,pull_requests`
5. Clique em **Save**.

### 4.4. Importação dos Subworkflows e Execução do Diagnóstico GitHub MCP
1. Importe os 3 subworkflows de ferramentas em `Workflows` → `Add Workflow` → `Import from File...`:
   - [`workflows/tools/github-mcp-pull-request-read.workflow.json`](workflows/tools/github-mcp-pull-request-read.workflow.json)
   - [`workflows/tools/github-mcp-get-file-contents.workflow.json`](workflows/tools/github-mcp-get-file-contents.workflow.json)
   - [`workflows/tools/github-mcp-list-commits.workflow.json`](workflows/tools/github-mcp-list-commits.workflow.json)
   - Em cada um dos 3 subworkflows, abra o nó `GitHub MCP - ...`, selecione a credencial `ReviewPilot GitHub MCP Read-Only` e salve.
2. Para testar imediatamente uma consulta real ao repositório sandbox:
   - Importe [`workflows/main/reviewpilot-github-mcp-diagnostic.workflow.json`](workflows/main/reviewpilot-github-mcp-diagnostic.workflow.json) (`ReviewPilot - GitHub MCP Diagnostic (Phase 6)`).
   - No nó `Configure Sandbox Repository Target`, substitua `YOUR_GITHUB_USERNAME` pelo seu usuário do GitHub (proprietário do repositório `auto-elite`).
   - No nó `Query Sandbox Commits via GitHub MCP`, selecione a credencial `ReviewPilot GitHub MCP Read-Only`.
   - Clique em **Test workflow** e confirme a saída `status: "GITHUB_MCP_READONLY_SUCCEEDED"` no nó `Format GitHub MCP Success`.
3. Reimporte o workflow principal [`workflows/main/reviewpilot-analyze-pr.workflow.json`](workflows/main/reviewpilot-analyze-pr.workflow.json) e, nos 3 subnós `github_pr_read_tool`, `github_get_file_contents_tool` e `github_list_commits_tool`, selecione os respectivos subworkflows importados no passo 1.

---

## 5. Procedimento de Exportação e Sanitização para Git

Sempre que alterar um workflow na interface do n8n:
1. Exporte via menu `...` → **Download**.
2. Salve em `infra/n8n/workflows/main/` ou `infra/n8n/workflows/tools/`.
3. Antes do commit:
   - Verifique que `pinData` está vazio (`"pinData": {}`).
   - Substitua qualquer ID de credencial local pelos placeholders `"CREDENTIAL_ID_INTERNAL_SERVICE_AUTH"`, `"CREDENTIAL_ID_GOOGLE_GEMINI"` e `"CREDENTIAL_ID_GITHUB_MCP_HEADERS"`.
   - Execute `./mvnw test` em `apps/backend` para rodar o teste automatizado `N8nWorkflowExportSanitizationTest`.
