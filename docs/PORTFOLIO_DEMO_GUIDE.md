# Roteiro de Demonstração para Portfólio — ReviewPilot

Este guia estrutura uma apresentação técnica de 5 a 8 minutos do **ReviewPilot — AI Code Review Agent** para entrevistas, demonstrações em vídeo ou avaliação de portfólio de Engenharia de Software e IA Aplicada.

---

## 1. Pitch Técnico (1 minuto)

- **Problema resolvido:** revisões manuais de Pull Requests frequentemente deixam escapar bugs sutis de borda (`NullPointerException`, `ArithmeticException`), falhas de segurança e regressões por falta de testes, enquanto ferramentas puramente estáticas geram ruído sem contexto semântico do diff.
- **Solução arquitetural:** o **ReviewPilot** combina um backend transacional em **Java 21 + Spring Boot 3.5** (responsável por segurança HMAC-SHA256, idempotência, allowlist, controle de concorrência/commits obsoletos e persistência em **PostgreSQL 17**) com um **Agente de IA executado dentro do n8n** (`Tools Agent` + **Google Gemini** + **GitHub MCP** e **SonarQube MCP** via *Streamable HTTP* em modo somente leitura) e um dashboard de auditoria em **React 19 + TypeScript Strict**.

---

## 2. Demonstração dos 5 Cenários Controlados de Pull Request (3 minutos)

Os cenários de avaliação estão catalogados em [`infra/n8n/fixtures/demo-pr-scenarios.json`](../infra/n8n/fixtures/demo-pr-scenarios.json):

| Cenário | Categoria / Severidade Esperada | Defeito Introduzido no Diff | O que o `Review Agent` + Backend validam |
|---|---|---|---|
| **PR #101 (`DiscountCalculator.java`)** | `BUG` / `HIGH` | `couponCode.trim()` sem verificação de nulidade | Detecção de `NullPointerException`, citação da linha exata e recomendação de guarda nula |
| **PR #102 (`InstallmentService.java`)** | `BUG` / `HIGH` | Validação `months < 0` permite `months == 0` e `BigDecimal.divide` sem `RoundingMode` | Detecção de `ArithmeticException` (divisão por zero e dízima periódica) |
| **PR #103 (`AuditTrailWriter.java`)** | `MAINTAINABILITY` / `MEDIUM` | Bloco `catch (Exception ignored)` vazio ao persistir auditoria financeira | Identificação de falha operacional silenciosa |
| **PR #104 (`VehicleSearchDao.java`)** | `SECURITY` / `CRITICAL` | Concatenação direta de `rawPlate` em query SQL | Alerta crítico de SQL Injection com recomendação de query parametrizada |
| **PR #105 (`ReservationExpirationJob.java`)** | `TEST` / `MEDIUM` | Novo job de cancelamento em lote sem testes unitários de fronteira | Alerta de ausência de testes cobrindo a transição de estado |

---

## 3. Demonstração de Resiliência e Governança (2 minutos)

1. **Idempotência de Webhook:** reenviar o mesmo `X-GitHub-Delivery` e mostrar que o backend retorna `200 OK` (`Duplicate webhook delivery ignored idempotently`) sem disparar nova chamada paga ao Gemini.
2. **Superação de Commit Obsoleto (`SUPERSEDED_BY_NEWER_SHA`):** enviar um novo commit para o mesmo PR enquanto a revisão anterior está em andamento; mostrar que a revisão antiga vai para `CANCELLED` e qualquer callback atrasado dela recebe `409 Conflict`.
3. **Degradação Graciosa do SonarQube:** desligar o container `sonarqube-mcp` e mostrar que os subworkflows retornam `UNAVAILABLE_OPTIONAL`, permitindo que a revisão termine com status `PARTIAL` e limitação explícita documentada no dashboard.
4. **Aprovação Humana Auditada (Zero Escrita Automática no GitHub):** aprovar ou rejeitar a revisão no dashboard React e mostrar que a decisão é gravada em `review_actions` sem publicar comentários não autorizados no GitHub.

