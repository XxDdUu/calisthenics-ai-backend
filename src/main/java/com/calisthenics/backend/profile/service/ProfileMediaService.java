package com.calisthenics.backend.profile.service;

import com.calisthenics.backend.profile.dto.request.ReorderProfileMediaRequest;
import com.calisthenics.backend.profile.dto.response.ProfileMediaResponse;
import com.calisthenics.backend.profile.entity.ProfileType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProfileMediaService {

    List<ProfileMediaResponse> getMedia(Long userId, ProfileType profileType);

    ProfileMediaResponse uploadMedia(Long currentUserId, ProfileType profileType, MultipartFile file, MultipartFile thumbnail);

    void deleteMedia(Long currentUserId, Long mediaId);

    List<ProfileMediaResponse> reorderMedia(Long currentUserId, ReorderProfileMediaRequest request);

    List<ProfileMediaResponse> setPrimaryCover(Long currentUserId, Long mediaId);
}
