# Infraestrutura SonarQube Community Build — ReviewPilot

Os serviços `sonarqube` (`sonarqube:25.1.0.102122-community`) e `sonarqube-mcp` (`sonarsource/sonarqube-mcp:1.21.0.2975`) pertencem ao profile opcional `quality` do Docker Compose para preservar memória RAM no ambiente de desenvolvimento (~11 GiB RAM).

## Como Iniciar e Parar

- **Iniciar:** `docker compose --profile quality up -d` (ou `./scripts/dev-up.sh --quality`)
- **Parar:** `docker compose --profile quality stop sonarqube sonarqube-mcp`

## Limitação Arquitetural da Edição Community Build

O SonarQube Community Build analisa apenas a branch principal do repositório e não oferece análise nativa de Pull Request. Portanto:
- Os achados consultados via SonarQube MCP são tratados como contexto complementar (`origin: SONARQUBE` ou `CORRELATED`).
- A ausência de alertas no SonarQube não significa que o Pull Request está isento de defeitos.
- Se o profile `quality` estiver desligado, o `Review Agent` prossegue normalmente com a revisão usando GitHub MCP e Gemini, registrando a indisponibilidade em `limitations[]`.

