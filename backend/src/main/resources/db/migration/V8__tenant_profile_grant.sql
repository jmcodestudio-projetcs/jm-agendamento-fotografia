-- JM Code Studio | V8 - grant de UPDATE em photographers para jm_tenant
--
-- jm_tenant existe desde o V1, com SELECT amplo em varias tabelas de negocio
-- e INSERT/UPDATE em services/weekly_schedule/calendar_blocks/appointments/
-- blocked_contacts/push_subscriptions -- mas o V1 NAO deu UPDATE em
-- photographers (so SELECT). Ate a 1.4, nenhuma feature tinha motivo pra
-- fazer UPDATE autenticado em photographers (1.1 e 1.2 usam jm_signup/
-- jm_login, roles proprias, anonimas, sem contexto de sessao).
--
-- A policy RLS tenant_scope ON photographers (V1) ja nao tem FOR, entao ja
-- cobre UPDATE (USING + WITH CHECK ja se aplicam) -- faltava so o GRANT.
BEGIN;
SET LOCAL search_path = scheduling, public;

GRANT UPDATE ON photographers TO jm_tenant;

COMMIT;
