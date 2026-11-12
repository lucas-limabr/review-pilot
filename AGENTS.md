# ReviewPilot — Instruções para Agentes de Código (`AGENTS.md`)

Este repositório implementa o **ReviewPilot — AI Code Review Agent**.

## Fonte Principal de Especificação
Antes de criar ou alterar qualquer arquivo, leia integralmente o guia oficial:
- `.agents/docs/REVIEWPILOT_IMPLEMENTATION_GUIDE.md` (documentação complementar em `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/API_CONTRACTS.md` e `docs/progress/IMPLEMENTATION_STATUS.md`).

## Regras Arquiteturais Inegociáveis
1. **O n8n é o único runtime do agente de IA:** o agente de revisão (`Review Agent`) é construído e executado dentro de um workflow do n8n usando o nó `AI Agent` (*Tools Agent*), o subnó `Google Gemini Chat Model` e ferramentas MCP (via registro MCP do n8n ou subworkflows com `MCP Client` + `Call n8n Workflow Tool`).
2. **Proibição de runtime externo de agente:** NUNCA crie diretório `apps/agent`, serviço Python, FastAPI, LangChain externo, LangGraph ou chamadas diretas ao SDK `google-genai` fora do n8n.
3. **Propriedade de domínio e banco de dados:** o backend Java 21 / Spring Boot 3.5.x (`apps/backend`) é o único proprietário das regras de negócio e do PostgreSQL. O n8n nunca acessa diretamente o banco de domínio.
4. **Segurança e Segredos:**
   - Nunca versione `.env`, chaves de API, tokens GitHub/SonarQube ou credenciais em workflows exportados (`infra/n8n/workflows/`).
   - As chaves da Gemini API, GitHub PAT e SonarQube Token devem residir exclusivamente no cofre criptografado de credenciais do n8n (`N8N_ENCRYPTION_KEY`).
   - Apenas ferramentas MCP de **leitura** podem ser expostas ao AI Agent no MVP.
5. **Execução Incremental:** execute exclusivamente a fase solicitada pelo usuário, atualize `docs/progress/IMPLEMENTATION_STATUS.md` e apresente o relatório padrão da seção 1.4.

