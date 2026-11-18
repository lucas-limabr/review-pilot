# ReviewPilot — Contratos de API e Eventos

Os schemas JSON canônicos para integração entre o backend Spring Boot e o workflow do n8n estão versionados em:
- [`contracts/review-request.schema.json`](../contracts/review-request.schema.json)
- [`contracts/review-result.schema.json`](../contracts/review-result.schema.json)

## 1. Endpoints Públicos do Backend (`/api/v1`)

| Método | Endpoint | Finalidade | Status Esperados |
|---|---|---|---|
| `GET` | `/api/v1/health` | Health check da API | `200` |
| `GET` | `/api/v1/repositories` | Listar repositórios permitidos na allowlist | `200` |
| `POST` | `/api/v1/repositories` | Cadastrar repositório permitido | `201`, `400`, `409` |
| `GET` | `/api/v1/repositories/{id}/reviews` | Listar execuções de revisão de um repositório (paginado) | `200`, `404` |
| `GET` | `/api/v1/reviews/{id}` | Consultar detalhes, findings e ações de uma revisão | `200`, `404` |
| `GET` | `/api/v1/dashboard/summary` | Métricas agregadas para o dashboard | `200` |
| `POST` | `/api/v1/reviews/{id}/approval` | Registrar aprovação ou rejeição humana no ReviewPilot | `200`, `400`, `404`, `409` |
| `POST` | `/api/v1/webhooks/github` | Receber eventos `pull_request` assinados do GitHub | `202`, `200`, `400`, `401`, `403`, `422` |

## 2. Endpoints Internos Backend ↔ n8n (`/api/internal/v1`)

Exigem header de autenticação com `INTERNAL_SERVICE_TOKEN` e comunicação restrita à rede interna do Docker Compose:

| Direção | Método e Endpoint | Contrato | Finalidade |
|---|---|---|---|
| Backend → n8n | `POST http://n8n:5678/webhook/reviewpilot-dispatch` | `contracts/review-request.schema.json` | Disparar análise assíncrona de um Pull Request |
| Interno / Admin | `POST /api/internal/v1/reviews/{id}/dispatch` | Sem corpo (usa estado `PENDING` persistido) | Reprocessar de forma controlada o dispatch de uma revisão `PENDING` quando o n8n esteve temporariamente indisponível |
| n8n → Backend | `POST /api/internal/v1/reviews/{id}/results` | `contracts/review-result.schema.json` | Persistir findings e resumo validados da revisão |
| n8n → Backend | `POST /api/internal/v1/reviews/{id}/failure` | Payload JSON de falha controlada | Registrar erro técnico sem mascarar como revisão concluída |

## 3. Padrão de Erros HTTP

Todas as respostas de erro da API utilizam `application/problem+json` (RFC 9457 / Problem Details) sem expor stack traces internos, tokens ou segredos.

