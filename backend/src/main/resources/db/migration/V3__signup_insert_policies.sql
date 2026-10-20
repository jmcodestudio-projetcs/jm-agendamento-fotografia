-- JM Code Studio | V3 - policies RLS de INSERT para o role do cadastro publico
--
-- photographers, photographer_users e subscriptions tem FORCE ROW LEVEL SECURITY
-- (V1) e o V2 concedeu INSERT a jm_signup sem policy correspondente, entao todo
-- INSERT era recusado por RLS. As policies abaixo sao exclusivamente de INSERT:
-- jm_signup continua sem SELECT/UPDATE/DELETE nessas tabelas.
BEGIN;
SET LOCAL search_path = scheduling, public;

CREATE POLICY signup_insert ON photographers FOR INSERT TO jm_signup WITH CHECK (true);
CREATE POLICY signup_insert ON photographer_users FOR INSERT TO jm_signup WITH CHECK (true);
CREATE POLICY signup_insert ON subscriptions FOR INSERT TO jm_signup WITH CHECK (true);

COMMIT;
