package com.calisthenics.backend.profile.service.impl;

import com.calisthenics.backend.profile.dto.request.ReorderProfileMediaRequest;
import com.calisthenics.backend.profile.dto.response.ProfileMediaResponse;
import com.calisthenics.backend.profile.entity.ProfileMediaItem;
import com.calisthenics.backend.profile.entity.ProfileMediaType;
import com.calisthenics.backend.profile.entity.ProfileType;
import com.calisthenics.backend.profile.repository.ProfileMediaItemRepository;
import com.calisthenics.backend.profile.service.ProfileMediaService;
import com.calisthenics.backend.storage.MinioExerciseMediaStorageService;
import com.calisthenics.backend.storage.StorageService;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMediaServiceImpl implements ProfileMediaService {

    private final ProfileMediaItemRepository profileMediaItemRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MinioExerciseMediaStorageService exerciseMediaStorageService;

    @Value("${STORAGE_PROVIDER:local}")
    private String storageProvider;

    @Value("${MINIO_EXTERNAL_URL:}")
    private String minioExternalUrl;

    @Override
    @Transactional(readOnly = true)
    public List<ProfileMediaResponse> getMedia(Long userId, ProfileType profileType) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required.");
        }
        if (profileType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile type is required.");
        }
        return profileMediaItemRepository.findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(userId, profileType)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProfileMediaResponse uploadMedia(Long currentUserId, ProfileType profileType, MultipartFile file, MultipartFile thumbnail) {
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        if (profileType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile type is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File cannot be empty.");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        String contentType = file.getContentType();
        ProfileMediaType mediaType;
        if (contentType != null && contentType.startsWith("video/")) {
            mediaType = ProfileMediaType.VIDEO;
        } else if (contentType != null && contentType.startsWith("image/")) {
            mediaType = ProfileMediaType.IMAGE;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image and video files are supported.");
        }

        String mediaUrl = storeFileAndBuildUrl(file);

        String thumbnailUrl = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            thumbnailUrl = storeFileAndBuildUrl(thumbnail);
        } else if (mediaType == ProfileMediaType.IMAGE) {
            thumbnailUrl = mediaUrl;
        }

        int nextDisplayOrder = profileMediaItemRepository.countByUserIdAndProfileType(currentUserId, profileType);

        ProfileMediaItem item = ProfileMediaItem.builder()
                .user(user)
                .profileType(profileType)
                .mediaType(mediaType)
                .url(mediaUrl)
                .thumbnailUrl(thumbnailUrl)
                .displayOrder(nextDisplayOrder)
                .build();

        ProfileMediaItem saved = profileMediaItemRepository.save(item);
        log.info("Uploaded profile media: id={}, userId={}, profileType={}, mediaType={}, displayOrder={}",
                saved.getId(), currentUserId, profileType, mediaType, nextDisplayOrder);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteMedia(Long currentUserId, Long mediaId) {
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        ProfileMediaItem item = profileMediaItemRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media item not found."));

        if (!item.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this media item.");
        }

        ProfileType profileType = item.getProfileType();
        profileMediaItemRepository.delete(item);

        // Re-index remaining items so order is continuous 0, 1, 2...
        List<ProfileMediaItem> remaining = profileMediaItemRepository
                .findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(currentUserId, profileType);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i);
        }
        profileMediaItemRepository.saveAll(remaining);

        log.info("Deleted profile media: id={}, userId={}, profileType={}, remainingCount={}",
                mediaId, currentUserId, profileType, remaining.size());
    }

    @Override
    @Transactional
    public List<ProfileMediaResponse> reorderMedia(Long currentUserId, ReorderProfileMediaRequest request) {
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        if (request == null || request.getProfileType() == null || request.getMediaIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reorder request.");
        }

        ProfileType profileType = request.getProfileType();
        List<Long> mediaIds = request.getMediaIds();

        List<ProfileMediaItem> existingItems = profileMediaItemRepository
                .findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(currentUserId, profileType);

        if (existingItems.size() != mediaIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media IDs count mismatch with existing media.");
        }

        Map<Long, ProfileMediaItem> itemMap = existingItems.stream()
                .collect(Collectors.toMap(ProfileMediaItem::getId, Function.identity()));

        for (Long id : mediaIds) {
            if (!itemMap.containsKey(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media ID does not belong to this profile: " + id);
            }
        }

        for (int i = 0; i < mediaIds.size(); i++) {
            ProfileMediaItem item = itemMap.get(mediaIds.get(i));
            item.setDisplayOrder(i);
        }

        List<ProfileMediaItem> saved = profileMediaItemRepository.saveAll(existingItems);
        saved.sort(Comparator.comparingInt(ProfileMediaItem::getDisplayOrder));

        return saved.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<ProfileMediaResponse> setPrimaryCover(Long currentUserId, Long mediaId) {
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        ProfileMediaItem targetItem = profileMediaItemRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media item not found."));

        if (!targetItem.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this media item.");
        }

        ProfileType profileType = targetItem.getProfileType();
        List<ProfileMediaItem> items = profileMediaItemRepository
                .findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(currentUserId, profileType);

        targetItem.setDisplayOrder(0);
        int order = 1;
        for (ProfileMediaItem item : items) {
            if (!item.getId().equals(mediaId)) {
                item.setDisplayOrder(order++);
            }
        }

        List<ProfileMediaItem> saved = profileMediaItemRepository.saveAll(items);
        saved.sort(Comparator.comparingInt(ProfileMediaItem::getDisplayOrder));

        log.info("Set media item {} as primary cover for user {} ({})", mediaId, currentUserId, profileType);
        return saved.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private String storeFileAndBuildUrl(MultipartFile file) {
        boolean useMinio = "minio".equalsIgnoreCase(storageProvider) && exerciseMediaStorageService != null;
        String fileName = useMinio
                ? exerciseMediaStorageService.store(file)
                : storageService.store(file);

        if (useMinio && minioExternalUrl != null && !minioExternalUrl.isBlank()) {
            String baseUrl = minioExternalUrl.replaceAll("/+$", "");
            return baseUrl + "/exercise-media/" + fileName;
        }

        String mediaPath = useMinio
                ? "/api/v1/storage/exercise-media/"
                : "/api/v1/storage/files/";

        String builtUrl = null;
        if (RequestContextHolder.getRequestAttributes() != null) {
            try {
                builtUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path(mediaPath)
                        .path(fileName)
                        .toUriString();
            } catch (Exception e) {
                log.warn("Could not determine context path for media URL: {}", e.getMessage());
            }
        }
        if (builtUrl == null) {
            builtUrl = mediaPath + fileName;
        }
        return normalizeUrl(builtUrl);
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return rawUrl;
        if (rawUrl.startsWith("http://") && rawUrl.contains("sky-enterprise.id.vn")) {
            return "https://" + rawUrl.substring("http://".length());
        }
        return rawUrl;
    }

    private ProfileMediaResponse mapToResponse(ProfileMediaItem item) {
        return ProfileMediaResponse.builder()
                .id(item.getId())
                .userId(item.getUser().getId())
                .profileType(item.getProfileType())
                .mediaType(item.getMediaType())
                .url(normalizeUrl(item.getUrl()))
                .thumbnailUrl(normalizeUrl(item.getThumbnailUrl()))
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
