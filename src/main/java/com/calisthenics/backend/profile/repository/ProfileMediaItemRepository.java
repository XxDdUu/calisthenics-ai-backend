package com.calisthenics.backend.profile.repository;

import com.calisthenics.backend.profile.entity.ProfileMediaItem;
import com.calisthenics.backend.profile.entity.ProfileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileMediaItemRepository extends JpaRepository<ProfileMediaItem, Long> {

    List<ProfileMediaItem> findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(Long userId, ProfileType profileType);

    int countByUserIdAndProfileType(Long userId, ProfileType profileType);
}
