# Agendamento Fotografia — JM Code Studio

Plataforma SaaS de agendamento de ensaios para fotógrafos e filmadores.

Documentação de requisitos, arquitetura, modelo de dados e regras de negócio vive no projeto Claude "JM Code Studio - Agendamento - Fotógrafos" (baseline aprovada, consolidação v1.3). Este repositório é a implementação dessa baseline.

## Estrutura

- `backend/` — Java 25 + Spring Boot 4.1 (monólito modular)
- `frontend/` — React 19 + TypeScript + Vite
- `docker-compose.yml` — PostgreSQL local de desenvolvimento
- `AGENTS.md` — convenções de código e invariantes de negócio para quem for gerar código aqui (inclusive GitHub Copilot)

## Como rodar

Ver `AGENTS.md`.
