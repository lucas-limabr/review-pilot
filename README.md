# ReviewPilot — AI Code Review Agent

Aplicação de apoio à revisão de Pull Requests do GitHub com agente de IA executado em workflows do **n8n** (*AI Agent / Tools Agent*), modelo **Google Gemini** (*Google Gemini Chat Model*), evidências coletadas via **GitHub MCP Server** e **SonarQube MCP Server** (modo somente leitura), backend **Java 21 + Spring Boot 3.5.x**, persistência em **PostgreSQL** e dashboard em **React + TypeScript + Vite**.

---

## Arquitetura e Princípios

- **Runtime do Agente no n8n:** o ciclo de raciocínio e seleção de ferramentas MCP ocorre integralmente no nó `AI Agent` do n8n. Não existe serviço Python separado.
- **Propriedade de Domínio no Backend:** o backend Spring Boot valida assinaturas HMAC-SHA256 de webhooks do GitHub, controla idempotência, aplica allowlist de repositórios, valida a localização dos findings no diff e persiste os dados no PostgreSQL.
- **Aprovação Humana:** revisões aprovadas ou rejeitadas no dashboard são auditadas localmente e não publicam comentários automaticamente no GitHub durante o MVP.

Consulte:
- [Arquitetura e Versões (`docs/ARCHITECTURE.md`)](docs/ARCHITECTURE.md)
- [Segurança e Segredos (`docs/SECURITY.md`)](docs/SECURITY.md)
- [Contratos de API (`docs/API_CONTRACTS.md`)](docs/API_CONTRACTS.md)
- [Status de Implementação (`docs/progress/IMPLEMENTATION_STATUS.md`)](docs/progress/IMPLEMENTATION_STATUS.md)

---

## Portas Padrão no Ambiente Local

| Serviço | URL / Porta Local | Profile Docker Compose | Observações |
|---|---|---|---|
| PostgreSQL 17 | `127.0.0.1:5432` | padrão | Banco de domínio do ReviewPilot (`reviewpilot`) |
| n8n | `http://localhost:5678` | padrão | Editor visual e runtime do `Review Agent` |
| Backend Spring Boot | `http://localhost:8080` | `backend` / `full` | Implementado na Fase 2 |
| Frontend React | `http://localhost:5173` | `frontend` / `full` | Implementado na Fase 9 |
| SonarQube Community Build | `http://localhost:9000` | `quality` | Opcional para preservar memória RAM local |
| SonarQube MCP Server | `http://sonarqube-mcp:8080/mcp` | `quality` | Restrito à rede interna `reviewpilot-net` |

---

## Configuração Inicial (`.env`)

1. Copie o template `.env.example` para `.env`:
   ```bash
   cp .env.example .env
   ```
2. Gere segredos fortes e únicos localmente (nunca versione o `.env` nem compartilhe os valores):
   ```bash
   openssl rand -hex 32
   ```
3. Preencha no arquivo `.env` os quatro valores obrigatórios:
   - `POSTGRES_PASSWORD`
   - `INTERNAL_SERVICE_TOKEN`
   - `GITHUB_WEBHOOK_SECRET`
   - `N8N_ENCRYPTION_KEY` *(mantenha este valor estável após criá-lo para não invalidar credenciais salvas no n8n)*

> **Importante:** As chaves da Gemini API, GitHub Fine-Grained PAT e SonarQube User Token **não** vão no `.env`; elas devem ser cadastradas diretamente no gerenciador de credenciais criptografadas da interface do n8n (`http://localhost:5678`).

---

## Comandos do Ambiente Local

### Validar configuração do Docker Compose
```bash
docker compose config -q
```

### Iniciar infraestrutura base (`postgres` + `n8n`)
```bash
./scripts/dev-up.sh
# ou diretamente:
docker compose up -d
```

### Iniciar com SonarQube Community Build + SonarQube MCP (`profile: quality`)
```bash
./scripts/dev-up.sh --quality
# ou diretamente:
docker compose --profile quality up -d
```

### Parar serviços (preservando volumes persistentes)
```bash
./scripts/dev-down.sh
# ou diretamente:
docker compose --profile quality --profile full down
```

