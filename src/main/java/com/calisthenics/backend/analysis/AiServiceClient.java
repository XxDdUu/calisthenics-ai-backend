package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.AiImageAnalysisResponseDto;
import com.calisthenics.backend.analysis.dto.AiVideoAnalysisResponseDto;
import com.calisthenics.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

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

    private <T> T sendMultipartRequest(String url, MultipartFile file, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("Posting media file {} to AI service at {}", file.getOriginalFilename(), url);
            ResponseEntity<T> response = restTemplate.postForEntity(url, requestEntity, responseType);
            return response.getBody();
        } catch (IOException e) {
            log.error("Failed to read file bytes for AI Service call", e);
            throw new RuntimeException("Failed to read media file for AI analysis", e);
        } catch (Exception e) {
            log.error("Error communicating with AI service at {}", url, e);
            throw new RuntimeException("AI Pose Analysis service unavailable: " + e.getMessage(), e);
        }
    }
}
