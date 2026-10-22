-- JM Code Studio | V4 - role minima para bootstrap de login do fotografo
--
-- Login precisa localizar o fotografo pelo e-mail ANTES de existir um
-- photographer_id para o contexto RLS de jm_tenant -- mesmo problema de
-- bootstrap do cadastro (1.1, V2/V3). As policies abaixo sao permissivas
-- (USING(true)), nao tenant_scope. jm_login nao enxerga nenhuma outra
-- tabela de negocio alem das listadas aqui.
--
-- Escopo de acesso:
-- - SELECT em photographer_users (achar a credencial pelo e-mail normalizado)
-- - UPDATE em photographer_users (gravar last_login_at no sucesso)
-- - SELECT em photographers (checar security_blocked_at antes de autenticar --
--   RN de bloqueio de seguranca, "B01": impede login enquanto vigente, mesmo
--   sem a ferramenta de admin que o liga ainda existir)
-- - SELECT + INSERT em access_attempts (rate limit; sem RLS, tabela tecnica)
-- - SELECT em platform_settings (limites de login; sem RLS, config global)
--
-- Policies de SELECT e UPDATE juntas nesta mesma migration (ao contrario do
-- V2/V3 do cadastro, que precisaram de duas por termos descoberto o gap de
-- FORCE ROW LEVEL SECURITY só ao testar) -- aqui já sabiamos do problema de
-- antemão.
BEGIN;
SET LOCAL search_path = scheduling, public;

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jm_login') THEN
    CREATE ROLE jm_login NOLOGIN NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA scheduling TO jm_login;

GRANT SELECT, UPDATE ON photographer_users TO jm_login;
CREATE POLICY login_select ON photographer_users FOR SELECT TO jm_login USING (true);
CREATE POLICY login_update ON photographer_users FOR UPDATE TO jm_login USING (true) WITH CHECK (true);

GRANT SELECT ON photographers TO jm_login;
CREATE POLICY login_block_check ON photographers FOR SELECT TO jm_login USING (true);

GRANT SELECT, INSERT ON access_attempts TO jm_login;
GRANT SELECT ON platform_settings TO jm_login;

COMMIT;
