package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSharePrRequest {

    @JsonAlias({"prId", "pr_ids"})
    private List<Long> prIds;

    public List<Long> getEffectivePrIds() {
        return prIds != null ? prIds : Collections.emptyList();
    }
}

