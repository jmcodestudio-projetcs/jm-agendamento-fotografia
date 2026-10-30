package com.jmcodestudio.agendamento.catalog;

import jakarta.validation.constraints.NotNull;

public record ActiveStatusRequest(@NotNull Boolean active) {
}
