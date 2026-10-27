package com.jmcodestudio.agendamento.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jmcodestudio.agendamento.profile.exception.InvalidWhatsappException;
import org.junit.jupiter.api.Test;

class WhatsappNormalizerTest {

    @Test
    void returnsNullForNullOrBlankInput() {
        assertEquals(null, WhatsappNormalizer.normalize(null));
        assertEquals(null, WhatsappNormalizer.normalize(" \t "));
    }

    @Test
    void removesFormattingAndAddsBrazilCountryCodeWhenMissing() {
        assertEquals("5511998765432", WhatsappNormalizer.normalize(" (11) 99876-5432 "));
        assertEquals("551134567890", WhatsappNormalizer.normalize("11.3456.7890"));
    }

    @Test
    void preservesValidBrazilianNumbersWithCountryCode() {
        assertEquals("551134567890", WhatsappNormalizer.normalize("+55 (11) 3456-7890"));
        assertEquals("5511998765432", WhatsappNormalizer.normalize("+55 (11) 99876-5432"));
    }

    @Test
    void rejectsNumbersWithInvalidLength() {
        assertThrows(InvalidWhatsappException.class, () -> WhatsappNormalizer.normalize("123456789"));
        assertThrows(InvalidWhatsappException.class, () -> WhatsappNormalizer.normalize("12345678901234"));
    }
}
