package com.jmcodestudio.agendamento.publicpage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção mínima de photographers exposta à página pública (Fase 4.1) — só
 * as colunas concedidas a jm_public na V10. Nunca inclui e-mail, WhatsApp ou
 * qualquer outro dado de contato.
 */
public record PublicPhotographer(
        UUID id,
        String name,
        String timezone,
        String accountStatus,
        boolean publicPageEnabled,
        LocalDate bookingHorizonDate) {

    /**
     * CA-030: conta SUSPENSA ou CANCELADA (ou página desligada manualmente)
     * não deve mostrar a agenda — só "agenda temporariamente indisponível".
     * A 1.7 (restrições completas por estado da conta) continua adiada; isto
     * é só a checagem mínima do lado da leitura pública.
     */
    public boolean isPageAvailable() {
        return publicPageEnabled && ("EM_TESTE".equals(accountStatus) || "ATIVA".equals(accountStatus));
    }
}
