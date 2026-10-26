-- JM Code Studio | V7 - role minima para o Spring Session JDBC
--
-- O V1 (init_schema) criou authentication.spring_session/spring_session_attributes
-- de proposito sem GRANT a nenhum papel: "Autenticacao/sessoes e access_attempts:
-- grants especificos so ao provisionar servico autenticador; jamais expor as
-- tabelas aos papeis tenant/client." jm_session e esse provisionamento -- um
-- papel so para a leitura/escrita de sessoes HTTP, sem nenhum acesso a tabelas
-- de negocio, mesmo padrao minimo-privilegio de jm_signup/jm_login/jm_reset/jm_worker.
--
-- Spring Session JDBC (JdbcIndexedSessionRepository) faz SELECT/INSERT/UPDATE/DELETE
-- nas duas tabelas, inclusive DELETE no expurgo periodico de sessoes expiradas.
-- Sem RLS: tabelas tecnicas de autenticacao, fora da lista de isolamento por
-- tenant do V1.
BEGIN;
SET LOCAL search_path = authentication, public;

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jm_session') THEN
    CREATE ROLE jm_session NOLOGIN NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA authentication TO jm_session;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes TO jm_session;

COMMIT;
