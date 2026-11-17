# Infraestrutura SonarQube Community Build & MCP Server — ReviewPilot (Fase 7)

Os serviços `sonarqube` (`sonarqube:25.1.0.102122-community`) e `sonarqube-mcp` (`sonarsource/sonarqube-mcp:1.21.0.2975`) pertencem ao profile opcional `quality` do Docker Compose para preservar memória RAM no ambiente de desenvolvimento (~11 GiB RAM).

---

## 1. Como Iniciar e Parar o Profile `quality`

- **Iniciar:** `docker compose --profile quality up -d` (ou `./scripts/dev-up.sh --quality`)
- **Parar:** `docker compose --profile quality stop sonarqube sonarqube-mcp`
- **Interface Web do SonarQube (Host):** `http://localhost:9000`
- **Endpoint Interno do SonarQube MCP Server:** `http://sonarqube-mcp:8080/mcp` (acessível exclusivamente na rede Docker `reviewpilot-net`, sem porta publicada no host).

---

## 2. Configuração do Container `sonarqube-mcp` (`compose.yaml`)

- `SONARQUBE_URL=http://sonarqube:9000`
- `SONARQUBE_TRANSPORT=http` (`HTTP Streamable` em `/mcp`)
- `SONARQUBE_HTTP_HOST=0.0.0.0`
- `SONARQUBE_HTTP_PORT=8080`
- `SONARQUBE_MCP_IN_CONTAINER=true`
- `SONARQUBE_READ_ONLY=true`
- `SONARQUBE_TOOLSETS=issues,quality-gates,rules,projects`

---

## 3. Configuração Manual do Projeto e Token de Usuário (`PENDING_MANUAL` — Opcional)

1. Com o profile `quality` iniciado, acesse `http://localhost:9000`, realize o login inicial (`admin` / `admin`) e defina uma nova senha administrativa.
2. Crie um projeto manual para o repositório `auto-elite` (ex.: `projectKey = auto-elite`).
3. Acesse **My Account** → **Security** e gere um **User Token** (`squ_...`) somente leitura.
4. No n8n (`http://localhost:5678`), abra **Credentials** → **Add Credential** → **Bearer Auth** (`httpBearerAuth`):
   - **Credential Name:** `ReviewPilot SonarQube MCP Read-Only`
   - **Bearer Token:** cole o User Token gerado no SonarQube.
5. Associe essa credencial aos 3 subworkflows SonarQube em `infra/n8n/workflows/tools/`.

---

## 4. Limitação Arquitetural da Edição Community Build e Resiliência

O SonarQube Community Build analisa apenas a branch principal do repositório e não oferece análise nativa de Pull Request. Portanto:
- Os achados consultados via SonarQube MCP (`search_sonar_issues_in_projects`, `get_project_quality_gate_status`, `show_rule`) são tratados pelo `Review Agent` como contexto complementar (`origin: SONARQUBE` ou `CORRELATED`).
- A ausência de alertas no SonarQube não significa que o Pull Request está isento de defeitos.
- **Degradação Graciosa:** quando o profile `quality` estiver desligado ou `sonarProjectKey` for `null`, os subworkflows SonarQube MCP retornam `status: "UNAVAILABLE_OPTIONAL"` sem lançar erro fatal, permitindo que o `Review Agent` conclua a revisão normalmente via GitHub MCP + Gemini e registre a limitação em `limitations[]`.
