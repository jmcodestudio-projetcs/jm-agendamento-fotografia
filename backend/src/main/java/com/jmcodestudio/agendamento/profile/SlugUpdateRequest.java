package com.jmcodestudio.agendamento.profile;

import jakarta.validation.constraints.NotBlank;

public record SlugUpdateRequest(@NotBlank String slug) {
}
