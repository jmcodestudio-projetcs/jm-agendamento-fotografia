-- JM Code Studio | V10 - role de leitura publica (catalogo + disponibilidade)
--
-- Fase 4.1 (Solicitacao do cliente -> pagina publica) e a primeira vez que
-- o sistema expoe uma leitura sem sessao nenhuma, nem de fotografo (jm_tenant)
-- nem de cliente ja identificado por token (jm_client, que so autoriza por
-- appointment_id especifico, inexistente ainda nesta etapa). O proprio
-- comentario do V1 ja avisava: "Bootstrap de login, catalogo/disponibilidade
-- publicos, acoes client, sucessora e busca de token: implementar
-- comandos/projecoes restritos antes de abrir endpoints."
--
-- jm_public resolve isso com o mesmo padrao de bootstrap ja usado em
-- jm_login/jm_signup: uma primeira consulta por slug com policy permissiva
-- (USING(true)), SOMENTE nas colunas estritamente necessarias (GRANT por
-- coluna, nao a tabela inteira -- nunca email/whatsapp). Depois disso, a
-- aplicacao faz SET LOCAL app.photographer_id e reaproveita a MESMA funcao
-- request_photographer_id() que jm_tenant ja usa, em policies FOR SELECT
-- (nunca ALL -- esta role nunca escreve; a 4.2 trata de escrita, com sua
-- propria role).
--
-- calendar_occupancy e a UNICA tabela de ocupacao lida aqui: ja e
-- alimentada por trigger a partir de calendar_blocks E de appointments
-- (ver V1), entao jm_public nao precisa de SELECT em calendar_blocks nem em
-- appointments para calcular disponibilidade.
BEGIN;
SET LOCAL search_path = scheduling, public;

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jm_public') THEN
    CREATE ROLE jm_public NOLOGIN NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA scheduling TO jm_public;

-- Bootstrap: localizar o fotografo pelo slug, antes de existir contexto RLS.
GRANT SELECT (id, name, timezone, account_status, public_page_enabled, booking_horizon_date, public_slug)
  ON photographers TO jm_public;
CREATE POLICY public_slug_lookup ON photographers FOR SELECT TO jm_public USING (true);

-- Daqui para baixo, a aplicacao ja fez SET LOCAL app.photographer_id apos o
-- lookup acima -- mesma funcao de jm_tenant, reaproveitada em policies SELECT.
GRANT SELECT ON services TO jm_public;
CREATE POLICY public_active_services ON services FOR SELECT TO jm_public
  USING (photographer_id = request_photographer_id() AND active);

GRANT SELECT ON weekly_schedule TO jm_public;
CREATE POLICY public_schedule_read ON weekly_schedule FOR SELECT TO jm_public
  USING (photographer_id = request_photographer_id());

GRANT SELECT ON calendar_occupancy TO jm_public;
CREATE POLICY public_occupancy_read ON calendar_occupancy FOR SELECT TO jm_public
  USING (photographer_id = request_photographer_id());

COMMIT;
