package com.calisthenics.backend.profile.controller;

import com.calisthenics.backend.profile.dto.request.ReorderProfileMediaRequest;
import com.calisthenics.backend.profile.dto.response.ProfileMediaResponse;
import com.calisthenics.backend.profile.entity.ProfileType;
import com.calisthenics.backend.profile.service.ProfileMediaService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/profile-media", "/api/v1/profile/media"})
@RequiredArgsConstructor
@Tag(name = "Profile Media", description = "Endpoints for managing coach and athlete profile media gallery")
public class ProfileMediaController {

    private final ProfileMediaService profileMediaService;

    @GetMapping
    @Operation(summary = "Get media items for a specific profile (Coach or Athlete)")
    public ResponseEntity<List<ProfileMediaResponse>> getProfileMedia(
            @RequestParam("userId") Long userId,
            @RequestParam("profileType") String profileTypeStr
    ) {
        ProfileType profileType = ProfileType.fromString(profileTypeStr);
        if (profileType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid profileType: " + profileTypeStr);
        }
        return ResponseEntity.ok(profileMediaService.getMedia(userId, profileType));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Upload image or video to user profile media gallery")
    public ResponseEntity<ProfileMediaResponse> uploadMedia(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam("profileType") String profileTypeStr,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        ProfileType profileType = ProfileType.fromString(profileTypeStr);
        if (profileType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid profileType: " + profileTypeStr);
        }
        ProfileMediaResponse response = profileMediaService.uploadMedia(currentUser.getId(), profileType, file, thumbnail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{mediaId}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Delete a media item from profile gallery")
    public ResponseEntity<Void> deleteMedia(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long mediaId
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        profileMediaService.deleteMedia(currentUser.getId(), mediaId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Reorder media items in profile gallery")
    public ResponseEntity<List<ProfileMediaResponse>> reorderMedia(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ReorderProfileMediaRequest request
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        return ResponseEntity.ok(profileMediaService.reorderMedia(currentUser.getId(), request));
    }

    @PatchMapping("/{mediaId}/cover")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Set a media item as the primary cover (display_order = 0)")
    public ResponseEntity<List<ProfileMediaResponse>> setPrimaryCover(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long mediaId
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        return ResponseEntity.ok(profileMediaService.setPrimaryCover(currentUser.getId(), mediaId));
    }
}
