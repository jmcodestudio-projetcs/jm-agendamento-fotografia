-- JM Code Studio | V2 - role minima para o endpoint publico de cadastro
--
-- O cadastro cria um tenant novo (ainda sem contexto RLS de jm_tenant, que
-- depende de um photographer_id ja existente), entao nao pode usar jm_tenant
-- (sem INSERT em photographers) nem jm_admin/jm_worker (privilegio amplo
-- demais para expor atras de um endpoint publico e anonimo, alvo natural de
-- abuso na internet).
--
-- jm_signup só pode inserir nas tres tabelas que o cadastro precisa, e ler
-- platform_settings (configuracao global, nao sensivel por tenant) para
-- validar o tamanho da senha configurado.
--
-- Unicidade de e-mail (photographer_users.email_normalized) e de slug
-- (photographers.public_slug) são garantidas pelas constraints UNIQUE do
-- proprio schema (ja existentes desde o V1); a aplicacao trata a violacao
-- em vez de pre-checar com SELECT, entao jm_signup nao precisa de SELECT
-- nessas tres tabelas.
BEGIN;
SET LOCAL search_path = scheduling, public;

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jm_signup') THEN
    CREATE ROLE jm_signup NOLOGIN NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA scheduling TO jm_signup;
GRANT SELECT ON platform_settings TO jm_signup;
GRANT INSERT ON photographers, photographer_users, subscriptions TO jm_signup;

COMMIT;
