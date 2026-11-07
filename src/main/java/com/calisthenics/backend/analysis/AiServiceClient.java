package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.AiImageAnalysisResponseDto;
import com.calisthenics.backend.analysis.dto.AiVideoAnalysisResponseDto;
import com.calisthenics.backend.videoanalysis.dto.AiVideoScoringResponseDto;
import com.calisthenics.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    public AiImageAnalysisResponseDto analyzeImage(MultipartFile file, boolean drawOverlay) {
        String url = appProperties.getAiService().getUrl() + "/api/v1/analysis/image?draw_overlay=" + drawOverlay;
        return sendMultipartRequest(url, file, AiImageAnalysisResponseDto.class);
    }

    public AiVideoAnalysisResponseDto analyzeVideo(MultipartFile file, int sampleFps) {
        String url = appProperties.getAiService().getUrl() + "/api/v1/analysis/video?sample_fps=" + sampleFps;
        return sendMultipartRequest(url, file, AiVideoAnalysisResponseDto.class);
    }

    public AiVideoScoringResponseDto scoreVideo(MultipartFile file, String exercise, int sampleFps, String analysisId) {
        String baseUrl = appProperties.getAiService().getUrl() + "/api/v1/analysis/video/score";
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("sample_fps", sampleFps);
        if (exercise != null && !exercise.isBlank()) {
            builder.queryParam("exercise", exercise);
        }
        if (analysisId != null && !analysisId.isBlank()) {
            builder.queryParam("analysis_id", analysisId);
        }
        String url = builder.toUriString();
        return sendMultipartRequest(url, file, AiVideoScoringResponseDto.class);
    }

    public AiVideoScoringResponseDto scoreVideo(byte[] fileBytes, String filename, String exercise, int sampleFps, String analysisId) {
        String baseUrl = appProperties.getAiService().getUrl() + "/api/v1/analysis/video/score";
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("sample_fps", sampleFps);
        if (exercise != null && !exercise.isBlank()) {
            builder.queryParam("exercise", exercise);
        }
        if (analysisId != null && !analysisId.isBlank()) {
            builder.queryParam("analysis_id", analysisId);
        }
        String url = builder.toUriString();
        return sendBytesMultipartRequest(url, fileBytes, filename, AiVideoScoringResponseDto.class);
    }

    public byte[] downloadAnnotatedVideo(String analysisId) {
        String url = appProperties.getAiService().getUrl() + "/api/v1/analysis/video/" + analysisId + "/annotated";
        try {
            log.info("Downloading annotated video from AI service at {}", url);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to download annotated video from AI service at {}", url, e);
            return null;
        }
    }

    private <T> T sendMultipartRequest(String url, MultipartFile file, Class<T> responseType) {
        try {
            return sendBytesMultipartRequest(url, file.getBytes(), file.getOriginalFilename(), responseType);
        } catch (IOException e) {
            log.error("Failed to read file bytes for AI Service call", e);
            throw new RuntimeException("Failed to read media file for AI analysis", e);
        }
    }

    private <T> T sendBytesMultipartRequest(String url, byte[] bytes, String filename, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "video.mp4";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("Posting media file {} ({} bytes) to AI service at {}", filename, bytes.length, url);
            ResponseEntity<T> response = restTemplate.postForEntity(url, requestEntity, responseType);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error communicating with AI service at {}", url, e);
            throw new RuntimeException("AI Pose Analysis service unavailable: " + e.getMessage(), e);
        }
    }
}
