package com.bcoworks.codeanalyzer.dto;

import java.util.List;

public record AnalysisJobResponse(
        String jobId,
        JobStatus status,
        List<FileAnalysisResult> results,
        String errorMessage
) {
}