package com.calisthenics.backend.videoanalysis.dto;

import com.calisthenics.backend.user.MembershipTier;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaEntitlementResponseDto {

    private Long userId;
    private MembershipTier membershipTier;
    private Integer dailyLimit;
    private Integer usedToday;
    private Integer remainingToday;
    private Boolean liveAnalysisUnlocked;
    private Boolean unlimited;
    private String grantSource;
    private Boolean adminGrantedPro;
    private String timezone;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate quotaDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime resetsAt;
}
