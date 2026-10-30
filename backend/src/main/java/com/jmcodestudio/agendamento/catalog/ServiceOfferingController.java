package com.jmcodestudio.agendamento.catalog;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    public ServiceOfferingController(ServiceOfferingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> list(Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.list(photographerId));
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(
            @Valid @RequestBody ServiceCreateRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(photographerId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceUpdateRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.update(photographerId, id, request));
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<ServiceResponse> updateActive(
            @PathVariable UUID id,
            @Valid @RequestBody ActiveStatusRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(service.updateActive(photographerId, id, request));
    }
}
