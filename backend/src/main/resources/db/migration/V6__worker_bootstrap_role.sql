-- JM Code Studio | V6 - grant em authentication_messages para o role de worker
--
-- jm_worker ja existe desde o V1 (init_schema), com SELECT amplo e
-- INSERT/UPDATE nas tabelas de negocio -- mas o proprio V1 deixou as tabelas
-- de autenticacao de fora de proposito: "Autenticacao/sessoes e
-- access_attempts: grants especificos so ao provisionar servico
-- autenticador; jamais expor as tabelas aos papeis tenant/client."
--
-- O worker assincrono da 1.3 (AuthenticationMessageWorker) precisa ler e
-- atualizar authentication_messages para enviar os e-mails de recuperacao
-- de senha via Resend. Ele NAO precisa de acesso a password_reset_tokens:
-- o link completo (com o token em claro) ja foi cifrado no
-- payload_ciphertext no momento em que a mensagem foi criada pelo endpoint
-- de request (role jm_reset); o worker so descriptografa, envia, e
-- atualiza o status da propria authentication_messages -- menor privilegio
-- possivel.
--
-- authentication_messages nao esta na lista de RLS do V1 (tabela tecnica,
-- pre-tenant), entao e GRANT direto, sem policy.
BEGIN;
SET LOCAL search_path = scheduling, public;

GRANT SELECT, UPDATE ON authentication_messages TO jm_worker;

COMMIT;
