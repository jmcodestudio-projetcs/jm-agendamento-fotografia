package com.jmcodestudio.agendamento.signup;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugGenerator {

    private static final String FALLBACK = "fotografo";

    private SlugGenerator() {
    }

    public static String slugify(String name) {
        String base = name == null ? "" : name;
        String slug = Normalizer.normalize(base.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? FALLBACK : slug;
    }

    public static String withSuffix(String baseSlug, int attempt) {
        return attempt <= 1 ? baseSlug : baseSlug + "-" + attempt;
    }
}
