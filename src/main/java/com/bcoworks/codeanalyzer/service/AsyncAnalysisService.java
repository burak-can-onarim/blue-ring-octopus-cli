package com.bcoworks.codeanalyzer.service;

import com.bcoworks.codeanalyzer.dto.AnalysisJobResponse;
import com.bcoworks.codeanalyzer.dto.FileAnalysisResult;
import com.bcoworks.codeanalyzer.dto.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arka planda asenkron olarak kaynak kod analizi yürüten servis sınıfı.
 * İşlem (Job) bazlı çalışarak uzun süren LLM sorgularının yönetilmesini sağlar.
 */
@Slf4j
@Service
public class AsyncAnalysisService {

    private final ICodeAnalyzerService analyzerService;
    private final SourceCodeScanner scanner;
    private final Map<String, AnalysisJobResponse> jobs = new ConcurrentHashMap<>();

    public AsyncAnalysisService(ICodeAnalyzerService analyzerService, SourceCodeScanner scanner) {
        this.analyzerService = analyzerService;
        this.scanner = scanner;
    }

    /**
     * Yeni bir analiz işi (job) oluşturur ve başlangıç durumuna (PENDING) çeker.
     *
     * @return Oluşturulan işe ait benzersiz (UUID) kimlik numarası.
     */
    public String createJob() {
        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.PENDING, List.of(), null));
        log.info("Yeni analiz işi oluşturuldu. Job ID: {}", jobId);
        return jobId;
    }

    /**
     * Verilen proje dizinindeki tüm Java dosyalarını bulur ve asenkron olarak sırayla LLM'e gönderir.
     * Her adımda job durumunu günceller.
     *
     * @param jobId       İşlemin takip edileceği kimlik numarası.
     * @param projectPath Analiz edilecek projenin kök dizin yolu.
     */
    @Async
    public void processProjectAnalysisAsync(String jobId, String projectPath) {
        log.info("Asenkron analiz başlatılıyor... Job ID: {}, Proje Yolu: {}", jobId, projectPath);

        try {
            jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.IN_PROGRESS, List.of(), null));

            List<Path> javaFiles = scanner.scanJavaFiles(projectPath);
            log.info("Job ID: {} için toplam {} adet Java dosyası bulundu.", jobId, javaFiles.size());

            List<FileAnalysisResult> results = new ArrayList<>();

            for (int i = 0; i < javaFiles.size(); i++) {
                Path filePath = javaFiles.get(i);
                log.debug("Analiz ediliyor ({}/{}): {}", (i + 1), javaFiles.size(), filePath.getFileName());

                String code = Files.readString(filePath);
                String analysis = analyzerService.analyze(code);

                results.add(new FileAnalysisResult(filePath.toString(), analysis));
                log.info("Başarıyla analiz edildi: {}", filePath.getFileName());
            }

            jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.COMPLETED, results, null));
            log.info("Analiz işi başarıyla tamamlandı. Job ID: {}", jobId);

        } catch (Exception e) {
            log.error("Analiz işi sırasında hata oluştu. Job ID: {}", jobId, e);
            jobs.put(jobId, new AnalysisJobResponse(jobId, JobStatus.FAILED, List.of(), e.getMessage()));
        }
    }

    /**
     * Belirtilen kimlik numarasına sahip analiz işinin güncel durumunu getirir.
     *
     * @param jobId Sorgulanacak işin kimlik numarası.
     * @return İşin mevcut durumunu içeren model veya bulunamazsa null.
     */
    public AnalysisJobResponse getJobStatus(String jobId) {
        AnalysisJobResponse status = jobs.get(jobId);
        log.debug("Job durumu sorgulandı. Job ID: {}, Durum: {}", jobId, (status != null ? status.status() : "BULUNAMADI"));
        return status;
    }
}