package com.bcoworks.codeanalyzer.controller;

import com.bcoworks.codeanalyzer.dto.*;
import com.bcoworks.codeanalyzer.service.AsyncAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analyzer")
public class CodeAnalysisController {

    private final AsyncAnalysisService asyncAnalysisService;

    public CodeAnalysisController(AsyncAnalysisService asyncAnalysisService) {
        this.asyncAnalysisService = asyncAnalysisService;
    }

    @PostMapping("/project/async")
    public ResponseEntity<Map<String, String>> analyzeProjectAsync(@RequestBody ProjectAnalysisRequest request) {
        String jobId = asyncAnalysisService.createJob();
        asyncAnalysisService.processProjectAnalysisAsync(jobId, request.projectPath());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("jobId", jobId, "status", "ACCEPTED", "message", "Analiz arka planda başlatıldı."));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AnalysisJobResponse> getJobStatus(@PathVariable String jobId) {
        AnalysisJobResponse job = asyncAnalysisService.getJobStatus(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}