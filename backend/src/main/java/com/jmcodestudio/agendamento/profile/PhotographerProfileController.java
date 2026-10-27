package com.jmcodestudio.agendamento.profile;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/photographers/me")
public class PhotographerProfileController {

    private final PhotographerProfileService profileService;

    public PhotographerProfileController(PhotographerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(profileService.getProfile(photographerId));
    }

    @PutMapping
    public ResponseEntity<Void> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        UUID photographerId = UUID.fromString(authentication.getName());
        profileService.updateProfile(photographerId, request);
        return ResponseEntity.noContent().build();
    }
}
