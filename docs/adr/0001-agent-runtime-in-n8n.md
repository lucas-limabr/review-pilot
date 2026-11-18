# ADR-0001: Execução do Agente de Revisão Integralmente no n8n e Estratégia de Transporte MCP

- **Status:** Aceito
- **Data:** 2026-11-11
- **Contexto:** Fase 0 — Inspeção e planejamento do repositório ReviewPilot

## 1. Contexto e Problema

O ReviewPilot analisa Pull Requests do GitHub combinando raciocínio de um modelo de linguagem (Google Gemini) com evidências obtidas por servidores Model Context Protocol (MCP) do GitHub e do SonarQube Community Build.

Precisamos garantir:
1. Separação clara entre regras de negócio/persistência (Backend Spring Boot + PostgreSQL) e orquestração/execução do ciclo de agente (reasoning + tool calling).
2. Adoção integral do n8n como runtime do agente de IA, evitando a manutenção de múltiplos runtimes (como um microsserviço Python/FastAPI/LangGraph separado).
3. Compatibilidade de transporte MCP entre os servidores oficiais (GitHub MCP remoto e SonarQube MCP Server em container) e os nós disponíveis na edição self-hosted do n8n Community Edition.

## 2. Decisões

### 2.1. n8n como Runtime Exclusivo do Agente de IA
- O agente de revisão (`Review Agent`) será construído e executado **exclusivamente dentro de um workflow do n8n** (`ReviewPilot - Analyze Pull Request`), utilizando o nó nativo **AI Agent** (`@n8n/n8n-nodes-langchain.agent` operando como *Tools Agent*) conectado ao subnó **Google Gemini Chat Model** (`@n8n/n8n-nodes-langchain.lmChatGoogleGemini`).
- **Não existirá diretório `apps/agent` nem serviço Python, FastAPI, LangChain externo, LangGraph ou uso direto do SDK `google-genai` fora do n8n.**
- O backend Java 21 / Spring Boot 3.5.x permanece como único proprietário das regras de domínio, validação de webhooks GitHub (HMAC-SHA256), idempotência, allowlist de repositórios, validação final de localização dos findings e persistência no PostgreSQL.
- O n8n não acessará o banco PostgreSQL de domínio do ReviewPilot; toda comunicação entre backend e n8n ocorrerá por contratos HTTP internos autenticados (`INTERNAL_SERVICE_TOKEN`).

### 2.2. Estratégia de Integração e Transporte MCP (Streamable HTTP vs. SSE)
A consulta à documentação oficial dos servidores MCP e dos nós do n8n identificou os seguintes requisitos técnicos de transporte:
- **GitHub MCP Server oficial remoto (`https://api.githubcopilot.com/mcp/`):** opera sobre **Streamable HTTP**, autenticado via header `Authorization: Bearer <token>` e restrito a leitura com `X-MCP-Readonly: true` e `X-MCP-Toolsets: repos,pull_requests`.
- **SonarQube MCP Server oficial (`SonarSource/sonarqube-mcp-server`):** opera em container na rede interna do Docker Compose sobre **Streamable HTTP** (`SONARQUBE_TRANSPORT=http`, endpoint `/mcp`, modo `SONARQUBE_READ_ONLY=true`) e não suporta o transporte SSE legado.
- **Nós MCP no n8n:**
  - Dependendo da versão exata do pacote `@n8n/n8n-nodes-langchain`, o subnó `MCP Client Tool` (`toolmcp`) originalmente suportava apenas `SSE Endpoint`, tendo recebido suporte a `Streamable HTTP` em versões mais recentes, enquanto o registro MCP integrado e o nó de workflow `MCP Client` (`mcpClient`) permitem configurar `Server Transport = HTTP Streamable` e `MCP Endpoint URL`.
  - Conforme a seção 3.3 e 11.1 do guia de implementação, **nunca** será configurado um endpoint Streamable HTTP em um campo rotulado `SSE Endpoint`.
  - Ordem estrita de preferência para exposição de ferramentas MCP ao AI Agent:
    1. **Registro MCP integrado ao AI Agent do n8n** (ou `MCP Client Tool` caso a versão fixada do n8n apresente explicitamente seleção de transporte `Streamable HTTP` e suporte múltiplos headers com filtro `Selected` de ferramentas somente leitura).
    2. **Subworkflows dedicados por operação de leitura** utilizando o nó regular `MCP Client` configurado em `Streamable HTTP`, expostos ao AI Agent no workflow principal por subnós **Call n8n Workflow Tool** (`toolworkflow`) com nome, descrição e schema de entrada estritamente tipados e restritos (ex.: `github_get_pull_request`, `github_get_pull_request_files`, `github_get_file_content`, `sonar_search_issues`, `sonar_get_quality_gate`).
    3. Caso alguma incompatibilidade de protocolo ocorra na versão fixada do n8n, a tarefa será marcada como `BLOCKED_COMPATIBILITY` com evidências técnicas antes de qualquer decisão.

### 2.3. Isolamento de Recursos no Ambiente Local
- Como o ambiente local dispõe de ~11 GiB de RAM total (~3,7 GiB livres durante a inspeção), os serviços `sonarqube` e `sonarqube-mcp` serão alocados exclusivamente no profile opcional `quality` do Docker Compose, e a execução de revisões terá concorrência inicial limitada a 1 revisão por vez.

## 3. Consequências

- **Positivas:** menor complexidade operacional (sem serviço Python extra), credenciais de IA e MCP centralizadas e criptografadas no cofre do n8n (`N8N_ENCRYPTION_KEY`), visibilidade completa das etapas do workflow e preservação das garantias de domínio e auditoria no backend Spring Boot.
- **Cuidados necessários:** versionar sempre o prompt-fonte em `infra/n8n/prompts/review_agent.md` em sincronia com o System Message do nó AI Agent e sanitizar todas as exportações JSON em `infra/n8n/workflows/` para não incluir segredos ou IDs sensíveis.

