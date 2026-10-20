package com.jmcodestudio.agendamento.signup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void removesAccentsAndLowercases() {
        assertEquals("joao-da-conceicao", SlugGenerator.slugify("João da Conceição"));
    }

    @Test
    void keepsNumbersAndCollapsesSeparators() {
        assertEquals("studio-21-foto", SlugGenerator.slugify("  Studio 21 -- Foto!  "));
    }

    @Test
    void fallsBackWhenOnlySpacesOrSymbols() {
        assertEquals("fotografo", SlugGenerator.slugify("   "));
        assertEquals("fotografo", SlugGenerator.slugify("@#$%!"));
        assertEquals("fotografo", SlugGenerator.slugify(null));
    }

    @Test
    void suffixIsOmittedForAttemptZeroOrOne() {
        assertEquals("ana", SlugGenerator.withSuffix("ana", 0));
        assertEquals("ana", SlugGenerator.withSuffix("ana", 1));
    }

    @Test
    void suffixIsAppendedFromAttemptTwo() {
        assertEquals("ana-2", SlugGenerator.withSuffix("ana", 2));
        assertEquals("ana-10", SlugGenerator.withSuffix("ana", 10));
    }
}
