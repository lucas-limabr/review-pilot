# Infraestrutura n8n — Acesso, Segurança, Gemini e Manutenção (Fases 4 e 5)

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

## 2. Primeiro Acesso e Cadastro da Credencial Interna (Fase 4)

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

## 3. Configuração da Credencial Google Gemini e Parâmetros do Modelo (Fase 5 — `PENDING_MANUAL`)

### 3.1. Cadastro da Credencial no Cofre do n8n
1. Gere uma API key no Google AI Studio (`https://aistudio.google.com/app/apikey`). Nunca armazene essa chave no arquivo `.env` nem em arquivos versionados.
2. Na interface do n8n (`http://localhost:5678`), acesse **Credentials** → **Add Credential**.
3. Pesquise e selecione **Google Gemini(PaLM) Api** (identificador interno `googlePalmApi`).
4. Preencha os campos:
   - **Credential Name:** `ReviewPilot Google Gemini API`
   - **Host:** `https://generativelanguage.googleapis.com` (padrão)
   - **API Key:** cole a chave gerada no Google AI Studio.
5. Clique em **Save**.

### 3.2. Parâmetros Configurados no Nó `Google Gemini Chat Model`
| Parâmetro | Valor Configurado | Racional Técnico |
|---|---|---|
| Tipo do Nó | `@n8n/n8n-nodes-langchain.lmChatGoogleGemini` (`v1`) | Subnó nativo LangChain do n8n para a Gemini Developer API |
| Modelo Validado (`modelName`) | `models/gemini-3.5-flash-lite` (alternativa: `models/gemini-2.5-pro`) | Modelo estável GA com suporte a *Function Calling* e janela de contexto de 1M tokens (validado em `2026-11-12`) |
| Temperatura (`options.temperature`) | `0.1` | Minimiza alucinações e mantém consistência determinística na análise técnica de diffs |
| Limite de Saída (`options.maxOutputTokens`) | `4096` | Capacidade suficiente para serializar o JSON estruturado de revisão (`review-result.schema.json`) |
| Timeout do Workflow | `180s` (principal) / `60s` (diagnóstico) | Evita execuções pendentes indefinidamente em caso de lentidão externa |

### 3.3. Como Executar o Teste Simples com Código Fictício (Diagnóstico da Fase 5)
1. Em **Workflows** → **Add Workflow**, clique no menu `...` → **Import from File...** e selecione [`workflows/main/reviewpilot-gemini-diagnostic.workflow.json`](workflows/main/reviewpilot-gemini-diagnostic.workflow.json).
2. Clique no subnó **`Google Gemini Chat Model`** e selecione a credencial `ReviewPilot Google Gemini API`.
3. Clique no botão **Test workflow** (ou **Execute Workflow**).
4. O nó `Prepare Fictional Code Snippet` envia um diff Java 100% fictício (`OrderDiscountCalculator.java` contendo divisão por zero e potencial `NullPointerException`, sem qualquer código privado) ao nó `Gemini Diagnostic Review`.
5. Em caso de sucesso, o nó `Format Diagnostic Success` retorna `status: "GEMINI_CALL_SUCCEEDED"`, `validatedModel: "models/gemini-3.5-flash-lite"` e o JSON gerado pelo modelo em `agentOutput`.
6. Em caso de falha de autenticação (`401`/`403`), modelo indisponível (`404`), quota/rate-limit (`429`) ou timeout (`503`/`504`), a saída de erro encaminha a execução ao nó `Classify Gemini API Failure`, que classifica a categoria do erro e define `retryable` sem vazar a API key.

---

## 4. Importação e Atualização dos Workflows Principais

### 4.1. Workflow de Tratamento Global de Erros
1. Em **Workflows** → **Add Workflow**, importe [`workflows/main/reviewpilot-error-handler.workflow.json`](workflows/main/reviewpilot-error-handler.workflow.json).
2. Salve e ative o workflow.

### 4.2. Workflow Principal (`ReviewPilot - Analyze Pull Request`)
1. Importe (ou reimporte) o arquivo [`workflows/main/reviewpilot-analyze-pr.workflow.json`](workflows/main/reviewpilot-analyze-pr.workflow.json).
2. Confirme que a credencial `ReviewPilot Internal Service Auth` está associada aos nós:
   - `Receive Review Dispatch` (Webhook Trigger)
   - `Submit Review Result` (HTTP Request)
   - `Report Execution Failure` (HTTP Request)
3. Clique no nó **`Google Gemini Chat Model`** (posicionado abaixo de `Stage Placeholder - Review Agent (Phase 7)`) e associe a credencial `ReviewPilot Google Gemini API`. Na Fase 7, quando o placeholder for substituído pelo nó `AI Agent (Tools Agent)`, este subnó será conectado diretamente à entrada `Chat Model` (`ai_languageModel`).
4. Salve o workflow e mantenha a chave **Active** habilitada.

---

## 5. Topologia do Workflow Principal na Fase 5

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
    [Stage Placeholder - Review Agent (Phase 7)]   ◄── (Fase 7: conectará [Google Gemini Chat Model] via ai_languageModel)
       ├── Success ──► [Validate and Normalize Result] ──► [Submit Review Result] (POST /api/internal/v1/reviews/{id}/results)
       └── Error   ──► [Build Failure Payload]         ──► [Report Execution Failure] (POST /api/internal/v1/reviews/{id}/failure)
```

---

## 6. Procedimento de Exportação e Sanitização para Git

Sempre que alterar um workflow na interface do n8n:
1. Exporte via menu `...` → **Download**.
2. Salve em `infra/n8n/workflows/main/` ou `infra/n8n/workflows/tools/`.
3. Antes do commit:
   - Verifique que `pinData` está vazio (`"pinData": {}`).
   - Substitua qualquer ID de credencial gerado pela instância local pelos placeholders `"CREDENTIAL_ID_INTERNAL_SERVICE_AUTH"` e `"CREDENTIAL_ID_GOOGLE_GEMINI"`.
   - Execute `./mvnw test` em `apps/backend` para rodar o teste automatizado `N8nWorkflowExportSanitizationTest`, que valida a estrutura JSON, os parâmetros do modelo Gemini e garante ausência de segredos ou chaves de API nos arquivos exportados.
