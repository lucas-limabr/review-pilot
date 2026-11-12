# Servidores MCP (Model Context Protocol) — ReviewPilot

Documentação de transporte, autenticação e restrições de segurança dos servidores MCP consumidos pelo `Review Agent` no n8n.

## 1. GitHub MCP Server (Remoto Oficial)

- **Endpoint:** `https://api.githubcopilot.com/mcp/`
- **Transporte:** `Streamable HTTP`
- **Cabeçalhos Obrigatórios (Credencial no n8n):**
  - `Authorization: Bearer <GITHUB_FINE_GRAINED_PAT>`
  - `X-MCP-Readonly: true`
  - `X-MCP-Toolsets: repos,pull_requests`
- **Escopo do Token:** fine-grained PAT restrito ao repositório de demonstração, somente permissões `Read-only` (`Metadata`, `Contents`, `Pull requests`).

## 2. SonarQube MCP Server (Container Interno)

- **Imagem:** `sonarsource/sonarqube-mcp:1.21.0.2975` (profile `quality`)
- **Endpoint na rede interna (`reviewpilot-net`):** `http://sonarqube-mcp:8080/mcp`
- **Transporte:** `Streamable HTTP` (`SONARQUBE_TRANSPORT=http`, `SONARQUBE_READ_ONLY=true`)
- **Exposição:** acessível apenas dentro da rede Docker `reviewpilot-net` (`expose: ["8080"]`), sem porta publicada no host.

## 3. Regra de Compatibilidade no n8n

- Nunca preencher um endpoint `Streamable HTTP` em um campo rotulado exclusivamente como `SSE Endpoint`.
- Ordem de preferência no n8n:
  1. Registro MCP integrado ao `AI Agent` (ou subnó com suporte validado a `Streamable HTTP` e múltiplos headers).
  2. Subworkflows dedicados por operação de leitura usando o nó regular `MCP Client` (`Server Transport = Streamable HTTP`), expostos ao `AI Agent` via `Call n8n Workflow Tool` com input schema estritamente tipado.

