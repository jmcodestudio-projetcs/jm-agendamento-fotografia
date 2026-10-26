package com.jmcodestudio.agendamento.notification;

public record SendResult(boolean success, String providerId, String errorMessage) {
}
