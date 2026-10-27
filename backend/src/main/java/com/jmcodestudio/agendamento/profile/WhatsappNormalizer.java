package com.jmcodestudio.agendamento.profile;

import com.jmcodestudio.agendamento.profile.exception.InvalidWhatsappException;

/**
 * Produces only the canonical comparison key (RN-030A), not a display format.
 * The caller stores the photographer's input, trimmed, in a separate column.
 */
public final class WhatsappNormalizer {

    private WhatsappNormalizer() {
    }

    public static String normalize(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return null;
        }

        String digits = rawInput.replaceAll("[^0-9]", "");
        String normalized = digits;

        if (!(digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13))) {
            if (digits.length() == 10 || digits.length() == 11) {
                normalized = "55" + digits;
            }
        }

        if (normalized.length() != 12 && normalized.length() != 13) {
            throw new InvalidWhatsappException();
        }

        return normalized;
    }
}
