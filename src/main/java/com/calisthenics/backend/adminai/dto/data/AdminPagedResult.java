package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPagedResult<T> {
    @Builder.Default
    private List<T> items = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private AdminTimeRangeMetadata timeRange;
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();
}
