package com.jmcodestudio.agendamento.publicpage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leitura pública, sem sessão nem token — ver SecurityConfig (/api/public/** já é permitAll). */
@RestController
@RequestMapping("/api/public/photographers/{slug}")
public class PublicCatalogController {

    private final PublicCatalogService service;

    public PublicCatalogController(PublicCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PublicPhotographerResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(service.findPhotographer(slug));
    }

    @GetMapping("/services")
    public ResponseEntity<List<PublicServiceResponse>> services(@PathVariable String slug) {
        return ResponseEntity.ok(service.listServices(slug));
    }

    @GetMapping("/services/{serviceId}/availability")
    public ResponseEntity<AvailabilityResponse> availability(
            @PathVariable String slug,
            @PathVariable UUID serviceId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(service.availability(slug, serviceId, date));
    }
}
