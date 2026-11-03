package com.jmcodestudio.agendamento.signup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Antes deste teste, o componente não tinha cobertura em lugar nenhum do
// projeto (nem aqui, nem em PhotographerSignupServiceIT, que só o importa
// como dependência) -- achado ao escrever o teste de regressão do A-4
// (auditoria de 30/10/2026) para PasswordResetService.
class CommonPasswordCheckerTest {

    private final CommonPasswordChecker checker = new CommonPasswordChecker();

    @Test
    void rejectsPasswordFromTheList() {
        assertTrue(checker.isCommon("password123"));
    }

    @Test
    void isCaseInsensitiveAndIgnoresSurroundingWhitespace() {
        assertTrue(checker.isCommon("  PaSsWoRd123  "));
    }

    @Test
    void acceptsPasswordNotInTheList() {
        assertFalse(checker.isCommon("Correct-Horse-Battery-42"));
    }

    @Test
    void doesNotThrowOnNull() {
        assertFalse(checker.isCommon(null));
    }
}
