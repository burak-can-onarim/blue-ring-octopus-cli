package com.bcoworks.codeanalyzer.service;

import com.bcoworks.codeanalyzer.dto.AnalysisJobResponse;
import com.bcoworks.codeanalyzer.dto.FileAnalysisResult;
import com.bcoworks.codeanalyzer.dto.JobStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsyncAnalysisService {

    private final ICodeAnalyzerService analyzerService;
    private final SourceCodeScanner scanner;

    private final Map<String, AnalysisJobResponse> jobs = new ConcurrentHashMap<>();

    public AsyncAnalysisService(ICodeAnalyzerService analyzerService, SourceCodeScanner scanner) {
        this.analyzerService = analyzerService;
        this.scanner = scanner;
    }

    public String createJob() {
        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.PENDING, List.of(), null));
        return jobId;
    }

    @Async
    public void processProjectAnalysisAsync(String jobId, String projectPath) {
        try {
            jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.IN_PROGRESS, List.of(), null));

            List<Path> javaFiles = scanner.scanJavaFiles(projectPath);
            List<FileAnalysisResult> results = new ArrayList<>();

            for (Path filePath : javaFiles) {
                String code = Files.readString(filePath);
                String analysis = analyzerService.analyze(code);
                results.add(new FileAnalysisResult(filePath.toString(), analysis));
            }

            jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.COMPLETED, results, null));
        } catch (Exception e) {
            jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.FAILED, List.of(), e.getMessage()));
        }
    }

    public AnalysisJobResponse getJobStatus(String jobId) {
        return jobs.get(jobId);
    }
}