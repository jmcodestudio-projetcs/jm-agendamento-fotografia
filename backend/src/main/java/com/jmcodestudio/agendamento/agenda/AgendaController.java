package com.jmcodestudio.agendamento.agenda;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    private final AgendaSettingsService service;

    public AgendaController(AgendaSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AgendaSettingsResponse> settings(Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.getSettings(photographerId));
    }

    @PutMapping("/weekly-schedule")
    public ResponseEntity<List<WeeklyScheduleEntry>> replaceWeeklySchedule(
            @Valid @RequestBody WeeklyScheduleRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.replaceWeeklySchedule(photographerId, request));
    }

    @GetMapping("/blocks")
    public ResponseEntity<List<CalendarBlock>> listBlocks(Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.listBlocks(photographerId));
    }

    @PostMapping("/blocks")
    public ResponseEntity<CalendarBlock> createBlock(
            @Valid @RequestBody CalendarBlockCreateRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBlock(photographerId, request));
    }

    @DeleteMapping("/blocks/{id}")
    public ResponseEntity<Void> deleteBlock(@PathVariable UUID id, Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        service.deleteBlock(photographerId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/booking-horizon")
    public ResponseEntity<LocalDate> updateBookingHorizon(
            @RequestBody BookingHorizonRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.updateBookingHorizon(photographerId, request));
    }
}
