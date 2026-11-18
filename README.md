# ReviewPilot — AI Code Review Agent

Aplicação full-stack de revisão assistida de Pull Requests do GitHub com **Agente de IA executado integralmente dentro do n8n** (*AI Agent / Tools Agent*), modelo **Google Gemini** (`models/gemini-3.5-flash-lite`), coleta de evidências em modo **estritamente somente leitura** via **GitHub MCP Server** e **SonarQube MCP Server**, backend **Java 21 LTS + Spring Boot 3.5.11**, persistência em **PostgreSQL 17** e dashboard em **React 19 + TypeScript (strict) + Vite 6**.

---

## 1. Visão Geral da Arquitetura

```text
[GitHub Pull Request (opened / synchronize / reopened)]
                     │
                     ▼ (Webhook assinado com HMAC-SHA256)
     [Backend Spring Boot :8080] ──► [PostgreSQL 17 :5432]
      - Verifica HMAC em tempo constante
      - Aplica allowlist de repositórios
      - Garante idempotência (X-GitHub-Delivery e repo+PR+SHA)
      - Cancela execuções obsoletas se chegar novo commit SHA
                     │
                     ▼ (POST /webhook/reviewpilot-dispatch + X-Internal-Service-Token)
        [n8n 2.47.4 — Review Agent (:5678)]
         ├─ Modelo LLM: Google Gemini Chat Model (models/gemini-3.5-flash-lite, temp=0.1)
         ├─ GitHub MCP (HTTP Streamable, Read-Only):
         │   ├─ github_pr_read_tool (pull_request_read: get, get_files, get_diff, get_commits)
         │   ├─ github_get_file_contents_tool (get_file_contents)
         │   └─ github_list_commits_tool (list_commits)
         ├─ SonarQube MCP (HTTP Streamable, Read-Only, Profile quality):
         │   ├─ sonarqube_search_issues_tool (search_sonar_issues_in_projects)
         │   ├─ sonarqube_quality_gate_tool (get_project_quality_gate_status)
         │   └─ sonarqube_show_rule_tool (show_rule)
         └─ Validação Determinística:
             ├─ Validate and Normalize Result (contracts/review-result.schema.json)
             └─ Verify Contract and Head SHA (compara analyzedHeadSha == expectedHeadSha)
                     │
         ┌───────────┴───────────┐
         ▼ (Sucesso)             ▼ (Falha ou SHA divergente)
POST /api/internal/v1/        POST /api/internal/v1/
reviews/{id}/results          reviews/{id}/failure
         │                       │
         └───────────┬───────────┘
                     ▼
        [Dashboard React :5173]
         - KPIs consolidados e distribuição de findings
         - Filtros combinados por Severidade e Categoria
         - Aprovação / Rejeição humana auditada localmente
```

### Decisões Arquiteturais Inegociáveis
1. **n8n como Runtime Exclusivo do Agente (`ADR-0001`):** todo o ciclo de raciocínio, *Function Calling* e invocação de ferramentas MCP ocorre dentro do workflow `ReviewPilot - Analyze Pull Request` no n8n (`@n8n/n8n-nodes-langchain.agent` `v1.7`). Não há serviço Python/FastAPI separado.
2. **Permissões Somente Leitura (Zero Escrita Externa no MVP):** o `Review Agent` opera exclusivamente com ferramentas de consulta (`X-MCP-Readonly: true` no GitHub MCP e `SONARQUBE_READ_ONLY=true` no SonarQube MCP). Nenhuma ferramenta de escrita, comentário ou merge é conectada ao agente.
3. **Limitação Explícita do SonarQube Community Build:** a edição Community analisa a branch principal (`main`), não o branch do Pull Request diretamente. Por isso, o `Review Agent` trata os alertas do SonarQube como contexto complementar (`origin: SONARQUBE` ou `CORRELATED` quando corroborados no diff) e degrada graciosamente (`status: PARTIAL` com registro em `limitations[]`) caso o SonarQube esteja desligado.
4. **Superação Automática de Commits Obsoletos:** quando um PR recebe um novo commit (`synchronize`), o backend marca qualquer execução anterior em andamento (`PENDING`/`RUNNING`) como `CANCELLED` (`SUPERSEDED_BY_NEWER_SHA`) e rejeita callbacks atrasados com `409 Conflict`.

---

## 2. Estrutura do Monorepo

```text
.
├── .github/workflows/ci.yml              # Pipeline CI (Backend Java 21 + Frontend TS Strict + Sanitização)
├── compose.yaml                          # Orquestração Docker Compose com profiles (backend, frontend, quality, full)
├── .env.example                          # Template seguro de variáveis de ambiente (sem segredos reais)
├── contracts/
│   ├── review-request.schema.json        # Contrato JSON do dispatch (Backend -> n8n)
│   └── review-result.schema.json         # Contrato JSON do resultado (n8n -> Backend)
├── apps/
│   ├── backend/                          # API Java 21 + Spring Boot 3.5.11 + Flyway + PostgreSQL 17
│   └── frontend/                         # Dashboard React 19 + TypeScript Strict + Vite 6 + Vitest
├── infra/
│   ├── n8n/
│   │   ├── prompts/review_agent.md       # System Prompt versionado (v1.0.0) sincronizado com o workflow
│   │   ├── workflows/main/               # Workflow principal, Error Handler e workflows de diagnóstico
│   │   ├── workflows/tools/              # 6 subworkflows MCP somente leitura (GitHub + SonarQube)
│   │   ├── fixtures/                     # Payloads fictícios e cenários de PR controlados sem segredos
│   │   └── README.md                     # Guia operacional completo do n8n, exportação/importação e credenciais
│   ├── mcp/README.md                     # Documentação de transporte Streamable HTTP e allowlist de tools MCP
│   └── sonar/README.md                   # Configuração do SonarQube Community Build e SonarScanner
└── scripts/
    ├── dev-up.sh                         # Script de inicialização com validação de .env
    └── dev-down.sh                       # Script de parada preservando volumes
```

---

## 3. Pré-Requisitos e Configuração Inicial

### 3.1. Pré-requisitos no Fedora / Linux
- **Docker Engine** e **Docker Compose Plugin** (`docker compose version`)
- **Java 21 LTS** (Temurin 21 em `/usr/lib/jvm/java-21-temurin-jdk` — detectado automaticamente pelo `./apps/backend/mvnw`)
- **Node.js 22+** e **npm 10+**

### 3.2. Criação do `.env` Local
1. Copie o arquivo de exemplo:
   ```bash
   cp .env.example .env
   ```
2. Gere quatro segredos independentes com `openssl rand -hex 32` e preencha no `.env`:
   - `POSTGRES_PASSWORD`: senha do PostgreSQL de negócio.
   - `INTERNAL_SERVICE_TOKEN`: token compartilhado entre o backend e a credencial `ReviewPilot Internal Service Auth` do n8n.
   - `GITHUB_WEBHOOK_SECRET`: segredo HMAC-SHA256 configurado no webhook do repositório GitHub.
   - `N8N_ENCRYPTION_KEY`: chave de criptografia do cofre do n8n (**não altere após cadastrar credenciais no n8n**).

> **Regra de Segurança:** `GEMINI_API_KEY`, `GITHUB_PERSONAL_ACCESS_TOKEN` e `SONARQUBE_USER_TOKEN` **nunca** são armazenados no `.env` nem em arquivos versionados; eles residem exclusivamente no cofre criptografado de credenciais do n8n (`http://localhost:5678`).

---

## 4. Como Executar o Ambiente Local

### 4.1. Subir Infraestrutura e Serviços
- **Modo Padrão (`postgres` + `n8n` + `backend`):**
  ```bash
  docker compose --profile backend up -d --build
  ```
- **Modo Completo com SonarQube (`postgres` + `n8n` + `backend` + `sonarqube` + `sonarqube-mcp`):**
  ```bash
  docker compose --profile backend --profile quality up -d --build
  ```
- **Iniciar o Frontend React em modo desenvolvimento (`http://localhost:5173`):**
  ```bash
  npm --prefix apps/frontend install
  npm --prefix apps/frontend run dev
  ```

### 4.2. Portas e Endpoints Locais
| Serviço | Endereço Local | Descrição |
|---|---|---|
| **Frontend Dashboard** | `http://localhost:5173` | Interface React de acompanhamento, filtros e aprovação humana |
| **Backend API & Swagger** | `http://localhost:8080/swagger-ui.html` | API REST pública (`/api/v1`) e callbacks internos (`/api/internal/v1`) |
| **Health Check Backend** | `http://localhost:8080/api/v1/health` | Status da aplicação Spring Boot |
| **n8n Editor UI** | `http://localhost:5678` | Configuração de credenciais e visualização do `Review Agent` |
| **SonarQube UI** | `http://localhost:9000` | Painel de análise estática da branch principal (`profile: quality`) |

---

## 5. Importação, Exportação e Reconfiguração de Workflows no n8n

Consulte o guia detalhado em [`infra/n8n/README.md`](infra/n8n/README.md). Resumo rápido para uma instância limpa:
1. Crie as 4 credenciais no cofre do n8n (`http://localhost:5678` → *Credentials*):
   - `ReviewPilot Internal Service Auth` (`Header Auth`: `X-Internal-Service-Token` = valor de `INTERNAL_SERVICE_TOKEN` do `.env`)
   - `ReviewPilot Google Gemini API` (`Google Gemini(PaLM) Api`: chave gerada no Google AI Studio)
   - `ReviewPilot GitHub MCP Read-Only` (`Multiple Headers Auth`: `Authorization: Bearer <PAT>`, `X-MCP-Readonly: true`, `X-MCP-Toolsets: repos,pull_requests`)
   - `ReviewPilot SonarQube MCP Read-Only` (`Bearer Auth`: User Token `squ_...` gerado no SonarQube)
2. Importe os **6 subworkflows de ferramentas** em `infra/n8n/workflows/tools/` e associe a credencial correspondente no nó `MCP Client`.
3. Importe o workflow **`ReviewPilot - Global Error Handler`** (`infra/n8n/workflows/main/reviewpilot-error-handler.workflow.json`).
4. Importe o workflow principal **`ReviewPilot - Analyze Pull Request`** (`infra/n8n/workflows/main/reviewpilot-analyze-pr.workflow.json`), selecione os 6 subworkflows nos subnós `Call n8n Workflow Tool` conectados ao `Review Agent`, salve e marque como **`Active`**.

---

## 6. Executando os Testes Automatizados e Análise Estática

### 6.1. Backend (JUnit 5 + MockMvc + Sanitização n8n + Contratos JSON Schema)
```bash
./apps/backend/mvnw -B test -f apps/backend/pom.xml
```

### 6.2. Frontend (TypeScript Strict Lint + Vitest + Testing Library + Build)
```bash
npm --prefix apps/frontend run lint
npm --prefix apps/frontend test
npm --prefix apps/frontend run build
```

### 6.3. Análise Estática do Próprio ReviewPilot no SonarQube (`profile: quality` ativo)
```bash
./apps/backend/mvnw clean verify sonar:sonar \
  -Dsonar.token=<SEU_USER_TOKEN_SONARQUBE> \
  -f apps/backend/pom.xml
```

---

## 7. Roteiro de Demonstração para Portfólio (Passo a Passo)

1. **Demonstrar a Arquitetura e Segurança:**
   - Mostre o `compose.yaml` com isolamento de rede (`reviewpilot-net`) e `N8N_BLOCK_ENV_ACCESS_IN_NODE="true"`.
   - Abra o n8n (`http://localhost:5678`) e exiba o workflow **`ReviewPilot - Analyze Pull Request`**, destacando o nó `Review Agent` conectado ao `Google Gemini Chat Model` (`models/gemini-3.5-flash-lite`) e às **6 ferramentas MCP estritamente de leitura** (3 GitHub MCP + 3 SonarQube MCP).
2. **Cadastrar o Repositório `auto-elite` no Dashboard (`http://localhost:5173`):**
   - Clique em **`+ Novo Repositório`**, informe o owner, nome `auto-elite`, branch `main` e `sonarProjectKey: auto-elite`.
3. **Disparar uma Revisão Real ou Controlada:**
   - **Via GitHub Webhook Real:** abra ou atualize um Pull Request no repositório `auto-elite` contendo um dos 5 cenários documentados em [`infra/n8n/fixtures/demo-pr-scenarios.json`](infra/n8n/fixtures/demo-pr-scenarios.json) (`NullPointerException`, divisão por zero sem validação, `catch` silencioso, SQL Injection ou ausência de testes).
   - **Acompanhar no n8n:** mostre na aba *Executions* do n8n o `Review Agent` invocando `github_pr_read_tool` (`get` e `get_diff`), consultando `sonarqube_search_issues_tool` e `sonarqube_quality_gate_tool`, normalizando o JSON e passando pelo nó `Verify Contract and Head SHA`.
4. **Inspecionar Resultados e Registrar Decisão Humana no Dashboard:**
   - No dashboard (`http://localhost:5173`), clique em **Atualizar painel**, selecione a revisão concluída, filtre os findings por **Severidade** (`Crítica`, `Alta`) e **Categoria** (`Segurança`, `Bug Funcional`), inspecione a localização exata (`filePath` e linhas), evidência e recomendação, e clique em **Aprovar Revisão** (ou **Rejeitar Revisão**), demonstrando a trilha de auditoria sem escrita automática no GitHub.
