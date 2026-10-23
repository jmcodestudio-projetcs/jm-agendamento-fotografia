-- JM Code Studio | V5 - role minima para bootstrap de recuperacao de senha
--
-- Pedido e confirmacao de recuperacao sao ambos anonimos (sem sessao previa)
-- -- mesmo problema de bootstrap de signup (1.1) e login (1.2). jm_reset nao
-- enxerga nenhuma tabela de negocio alem das listadas aqui.
--
-- Escopo de acesso:
-- - SELECT em photographer_users (achar a conta pelo e-mail normalizado)
-- - UPDATE em photographer_users (gravar a nova senha no confirm)
-- - SELECT em photographers (nome do fotografo, usado no corpo do e-mail)
-- - SELECT + INSERT + UPDATE em password_reset_tokens (criar, buscar por
--   hash, marcar consumido/revogado -- sem RLS, tabela nao esta na lista de
--   isolamento por tenant, ver V1 init_schema)
-- - INSERT em authentication_messages (enfileirar o e-mail -- so o endpoint
--   de request grava aqui; o worker, que le/atualiza, usa role separada --
--   ver V6)
-- - SELECT + INSERT em access_attempts (rate limit scope RESET)
-- - SELECT em platform_settings (reset_ttl_seconds, reset_per_account_hour,
--   reset_per_ip_hour)
--
-- photographer_users e photographers tem RLS com policies permissivas
-- (USING(true)) por role, mesmo padrao de V4. password_reset_tokens,
-- authentication_messages, access_attempts e platform_settings nao estao na
-- lista de RLS do V1 (tabelas tecnicas/pre-tenant), entao sao GRANT direto.
BEGIN;
SET LOCAL search_path = scheduling, public;

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jm_reset') THEN
    CREATE ROLE jm_reset NOLOGIN NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA scheduling TO jm_reset;

GRANT SELECT, UPDATE ON photographer_users TO jm_reset;
CREATE POLICY reset_select ON photographer_users FOR SELECT TO jm_reset USING (true);
CREATE POLICY reset_update ON photographer_users FOR UPDATE TO jm_reset USING (true) WITH CHECK (true);

GRANT SELECT ON photographers TO jm_reset;
CREATE POLICY reset_name_lookup ON photographers FOR SELECT TO jm_reset USING (true);

GRANT SELECT, INSERT, UPDATE ON password_reset_tokens TO jm_reset;
GRANT INSERT ON authentication_messages TO jm_reset;
GRANT SELECT, INSERT ON access_attempts TO jm_reset;
GRANT SELECT ON platform_settings TO jm_reset;

COMMIT;
