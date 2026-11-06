package com.calisthenics.backend.storage.dto;

import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadResponse {

    private String fileName;
    private String fileDownloadUri;
    private String fileType;
    private long size;
    private ExerciseMediaResponse mediaItem;
}
