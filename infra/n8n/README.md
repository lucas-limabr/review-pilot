# Infraestrutura n8n — Acesso, Segurança e Manutenção (Fase 4)

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

## 2. Primeiro Acesso e Cadastro da Credencial Interna (`PENDING_MANUAL`)

1. Certifique-se de que os containers estão em execução:
   ```bash
   ./scripts/dev-up.sh
   ```
2. Acesse `http://localhost:5678` no navegador e conclua o cadastro inicial da conta **Owner** local.
3. No menu lateral esquerdo, clique em **Credentials** → **Add Credential** e selecione **Header Auth** (`httpHeaderAuth`).
4. Preencha os campos da credencial:
   - **Credential Name:** `ReviewPilot Internal Service Auth`
   - **Name:** `X-Internal-Service-Token`
   - **Value:** cole o valor exato de `INTERNAL_SERVICE_TOKEN` definido no seu arquivo `.env` local.
5. Clique em **Save**.

---

## 3. Importação e Ativação dos Workflows da Fase 4

### 3.1. Workflow de Tratamento Global de Erros
1. Em **Workflows** → **Add Workflow**, clique no menu `...` (canto superior direito) → **Import from File...**.
2. Selecione [`workflows/main/reviewpilot-error-handler.workflow.json`](workflows/main/reviewpilot-error-handler.workflow.json).
3. Salve e ative o workflow.

### 3.2. Workflow Principal (`ReviewPilot - Analyze Pull Request`)
1. Crie um novo workflow e importe o arquivo [`workflows/main/reviewpilot-analyze-pr.workflow.json`](workflows/main/reviewpilot-analyze-pr.workflow.json).
2. Associe a credencial `ReviewPilot Internal Service Auth` aos 3 nós HTTP/Webhook que exigem autenticação interna:
   - `Receive Review Dispatch` (Webhook Trigger)
   - `Submit Review Result` (HTTP Request)
   - `Report Execution Failure` (HTTP Request)
3. *(Opcional)* Em **Workflow Settings** (`...` → **Settings**), selecione `ReviewPilot - Global Error Handler` no campo **Error Workflow** e confirme **Timeout Workflow** em `180` segundos (3 minutos).
4. Salve o workflow e alterne a chave **Inactive / Active** para **Active** para habilitar a URL de produção `/webhook/reviewpilot-dispatch`.

---

## 4. Topologia do Workflow na Fase 4 (Esqueleto Determinístico)

```text
[Receive Review Dispatch] (POST /webhook/reviewpilot-dispatch, Header Auth)
           │
           ▼
[Validate Dispatch Payload] (IF: reviewExecutionId, repositoryFullName, pullRequestNumber, expectedHeadSha, baseBranch)
     ├── False ──► [Reject Invalid Dispatch Payload] (HTTP 422)
     └── True  ──► [Acknowledge Dispatch Accepted] (HTTP 202)
                         │
                         ▼
               [Prepare Agent Context] (Set traceability metadata)
                         │
                         ▼
    [Stage Placeholder - Review Agent (Phase 7)]
       ├── Success ──► [Validate and Normalize Result] ──► [Submit Review Result] (POST /api/internal/v1/reviews/{id}/results)
       └── Error   ──► [Build Failure Payload]         ──► [Report Execution Failure] (POST /api/internal/v1/reviews/{id}/failure)
```

> **Nota sobre o endereço do Backend no callback:** por padrão no desenvolvimento local (quando o backend é executado no host via `./mvnw spring-boot:run`), os nós `Submit Review Result` e `Report Execution Failure` apontam para `http://host.docker.internal:8080`. Quando o backend for executado via container no profile `full`/`backend`, pode-se utilizar `http://backend:8080` ou manter `http://host.docker.internal:8080` (ambos acessam a porta 8080 do backend).

---

## 5. Procedimento de Exportação e Sanitização para Git

Sempre que alterar um workflow na interface do n8n:
1. Exporte via menu `...` → **Download**.
2. Salve em `infra/n8n/workflows/main/` ou `infra/n8n/workflows/tools/`.
3. Antes do commit:
   - Verifique que `pinData` está vazio (`"pinData": {}`).
   - Substitua qualquer ID de credencial gerado pela instância local pelo placeholder `"CREDENTIAL_ID_INTERNAL_SERVICE_AUTH"` (ou equivalente da credencial).
   - Execute `./mvnw test` em `apps/backend` para rodar o teste automatizado `N8nWorkflowExportSanitizationTest`, que valida a estrutura JSON e garante ausência de segredos ou tokens nos arquivos exportados.
