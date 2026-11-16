package com.calisthenics.backend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveAnalysisUsageDto {

    private boolean available;
    private boolean unlimited;
}
