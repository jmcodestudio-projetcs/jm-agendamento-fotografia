package com.jmcodestudio.agendamento.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(min = 1, max = 150) String name,
        String whatsapp,
        @NotBlank String timezone) {
}
