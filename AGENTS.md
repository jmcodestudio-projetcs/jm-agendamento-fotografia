# AGENTS.md — Convenções do projeto Agendamento Fotografia

Referência para quem (ou o que) gerar código aqui, incluindo o GitHub Copilot. Baseado na baseline aprovada (ver Consolidacao_Final_v1_3.md e Linha_do_Tempo_Desenvolvimento_v1_2.md no projeto Claude).

## Stack fixada

- Backend: Java 25, Spring Boot 4.1.x, Spring Data JPA/Hibernate, Spring Security, Spring Session JDBC.
- Banco: PostgreSQL 17, migrations via Flyway (`backend/src/main/resources/db/migration`), Hibernate em `ddl-auto=validate` — NUNCA gerar schema pelo Hibernate.
- Frontend: React 19 + TypeScript + Vite.
- Build backend: Maven (Wrapper quando disponível).
- Monólito modular por domínio de negócio (não microsserviços). Worker de notificações roda separado, mesma base de código.

## Camadas (backend)

`Controller` → `UseCase/Service` → `Repository`.

- Controller: recebe requisição, valida formato, delega ao caso de uso. Não contém regra de negócio.
- Caso de uso (service): concentra regra de negócio e autorização. É aqui que entram os invariantes da baseline (ver abaixo).
- Repository: acesso a dados. Usar SQL nativo quando necessário; evitar abstração sem necessidade concreta.
- DTOs delimitam a API — nunca expor entidades JPA diretamente nos controllers.

## Invariantes de negócio que todo código deve respeitar

(extraídos da Consolidação Final v1.3, seção 4 — não inventar exceção sem decisão da Jéssica)

1. **Isolamento por fotógrafo**: toda consulta/alteração precisa confirmar que o recurso pertence ao fotógrafo autenticado. ID vindo da tela não prova propriedade — sempre validar no backend. RLS no Postgres é camada adicional, não substitui a checagem na aplicação.
2. **Appointment = pedido + compromisso**: aprovação/aceite mantém o mesmo registro; propor outra data cria registro vinculado e marca o original como `SUBSTITUIDO_POR_NOVA_SOLICITACAO` (terminal).
3. **Snapshots**: dados do cliente e condições do serviço são copiados no momento do pedido. Mudar o catálogo depois NÃO altera pedidos já criados.
4. **Disponibilidade é calculada, não armazenada isolada**: pendentes + confirmados + bloqueios disputam intervalos (duração + buffer). Propor data não reserva; aceitar exige revalidação e controle de concorrência (lock/transação).
5. **Transação única**: mudança de estado + ocupação + histórico + outbox devem commitar juntos. Ordem de lock: fotógrafo antes do agendamento.
6. Qualquer dúvida sobre regra que não está clara aqui ou nos documentos — perguntar antes de implementar, não presumir.

## Testes

- Testes unitários "normais" (lógica isolada, sem banco) para a maioria dos casos.
- Para os pontos sensíveis a concorrência/estado (ex.: duas solicitações no mesmo horário, troca de estado de agendamento, RLS/isolamento entre fotógrafos): usar **Testcontainers com PostgreSQL real**, nunca mock de banco.
- Uma entrega só é considerada validada quando testada também pela tela (frontend), não só por teste automatizado — ver backlog-orquestracao.md no projeto Claude para a Definição de Pronto completa.

## O que NÃO fazer sem perguntar antes

- Não implementar exclusão automática de dados (depende de decisão jurídica pendente — BT-001).
- Não inventar transição de estado que não está na máquina de estados (`JM_Code_Studio_Maquinas_de_Estado_v1_3.md`).
- Não mudar regra de negócio para "destravar" uma implementação mais simples — regra de negócio muda só com decisão explícita da Jéssica.

## Como rodar localmente

```bash
# 1. Banco local
cp .env.example .env
docker compose up -d

# 2. Backend (a partir de /backend)
./mvnw spring-boot:run

# 3. Frontend (a partir de /frontend)
npm install
npm run dev
```
