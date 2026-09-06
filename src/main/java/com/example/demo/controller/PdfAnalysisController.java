package com.example.demo.controller;

import com.example.demo.model.AuditReport;
import com.example.demo.model.AuditResult;
import com.example.demo.model.AuditRunResponse;
import com.example.demo.model.Feature;
import com.example.demo.orchestrator.MultiAgentOrchestrator;
import com.example.demo.repository.FeatureRepository;
import com.example.demo.service.DocumentIngestionService;
import com.example.demo.service.RetryEventCollector;
import com.example.demo.service.TokenUsageAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * REST controller for PDF document analysis.
 */
@RestController
@RequestMapping("/api")
public class PdfAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(PdfAnalysisController.class);

    private final MultiAgentOrchestrator orchestrator;
    private final DocumentIngestionService ingestionService;
    private final FeatureRepository featureRepository;

    public PdfAnalysisController(MultiAgentOrchestrator orchestrator,
                                 DocumentIngestionService ingestionService,
                                 FeatureRepository featureRepository) {
        this.orchestrator = orchestrator;
        this.ingestionService = ingestionService;
        this.featureRepository = featureRepository;
    }

    /**
     * Analyzes a PDF document and returns the audit report as a compiled PDF.
     * If LaTeX compilation fails, returns the .tex file.
     *
     * <p>Endpoint: POST /api/analyze
     * <p>Content-Type: multipart/form-data
     * <p>Parameter: file (PDF to analyze)
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyze(@RequestParam("file") MultipartFile file) {
        // ── Input validation ──
        if (file.isEmpty()) {
            return badRequest("Empty file. Please upload a valid PDF file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return badRequest("Invalid format. Only PDF files accepted.");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            return badRequest("File too large. Maximum size: 50MB.");
        }

        log.info("Received analysis request for '{}' ({} bytes)", filename, file.getSize());

        TokenUsageAccumulator usage = TokenUsageAccumulator.start();
        try {
            AuditResult result = orchestrator.analyze(file);

            long oaiIn  = usage.getOpenAiInputTokens();
            long oaiOut = usage.getOpenAiOutputTokens();
            long antIn  = usage.getAnthropicInputTokens();
            long antOut = usage.getAnthropicOutputTokens();
            log.info("Token usage — OpenAI: {}in/{}out (tot {}), Anthropic: {}in/{}out (tot {})",
                    oaiIn, oaiOut, oaiIn + oaiOut, antIn, antOut, antIn + antOut);

            // If the PDF was compiled, return it
            if (result.pdfFile() != null && Files.exists(result.pdfFile())) {
                Resource resource = new FileSystemResource(result.pdfFile());
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-report.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .header("X-OpenAI-Input-Tokens",      String.valueOf(oaiIn))
                        .header("X-OpenAI-Output-Tokens",     String.valueOf(oaiOut))
                        .header("X-OpenAI-Total-Tokens",      String.valueOf(oaiIn + oaiOut))
                        .header("X-Anthropic-Input-Tokens",   String.valueOf(antIn))
                        .header("X-Anthropic-Output-Tokens",  String.valueOf(antOut))
                        .header("X-Anthropic-Total-Tokens",   String.valueOf(antIn + antOut))
                        .body(resource);
            }

            // Fallback: return the .tex file if PDF compilation failed
            if (result.texFile() != null && Files.exists(result.texFile())) {
                Resource resource = new FileSystemResource(result.texFile());
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-report.tex\"")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("X-Warning",                  "PDF compilation failed, returning LaTeX source file")
                        .header("X-OpenAI-Input-Tokens",      String.valueOf(oaiIn))
                        .header("X-OpenAI-Output-Tokens",     String.valueOf(oaiOut))
                        .header("X-OpenAI-Total-Tokens",      String.valueOf(oaiIn + oaiOut))
                        .header("X-Anthropic-Input-Tokens",   String.valueOf(antIn))
                        .header("X-Anthropic-Output-Tokens",  String.valueOf(antOut))
                        .header("X-Anthropic-Total-Tokens",   String.valueOf(antIn + antOut))
                        .body(resource);
            }

            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No output file generated"));

        } catch (Exception e) {
            log.error("Error during analysis of '{}'", filename, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Error during analysis",
                            "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ));
        } finally {
            TokenUsageAccumulator.clear();
        }
    }

    /**
     * Analyzes a PDF document and returns only the JSON report (without generating LaTeX/PDF).
     * Useful for debugging, integration with other systems, or when PDF is not needed.
     *
     * <p>Endpoint: POST /api/analyze/json
     */
    @PostMapping(value = "/analyze/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> analyzeJson(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return badRequest("Empty file. Please upload a valid PDF file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return badRequest("Invalid format. Only PDF files accepted.");
        }

        log.info("Received JSON analysis request for '{}' ({} bytes)", filename, file.getSize());

        try {
            AuditResult result = orchestrator.analyze(file);
            AuditReport report = result.report();
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("Error during JSON analysis of '{}'", filename, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Error during analysis",
                            "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ));
        }
    }

    /**
     * Analyzes a PDF and returns everything the execution produced, in one response.
     * <p>
     * This is the endpoint an automated evaluation uses. The two older ones each
     * provide half of what is needed: {@code /analyze} the compiled document without the
     * structured report, {@code /analyze/json} the report without compiling anything.
     * Calling both would run the models twice on the same document, at double the cost
     * and with two outcomes that need not agree.
     * <p>
     * Failures are reported as fields rather than as exceptions: a run that did not
     * produce a document is a legitimate outcome to record, not an error of this
     * service. Only an unexpected fault returns a 500.
     *
     * <p>Endpoint: POST /api/analyze/full
     */
    @PostMapping(value = "/analyze/full", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> analyzeFull(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return badRequest("Empty file. Please upload a valid PDF file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return badRequest("Invalid format. Only PDF files accepted.");
        }

        log.info("Received full analysis request for '{}' ({} bytes)", filename, file.getSize());

        TokenUsageAccumulator.start();
        RetryEventCollector retries = RetryEventCollector.start();
        try {
            AuditResult result = orchestrator.analyze(file);

            boolean texGenerated = result.texFile() != null && Files.exists(result.texFile());
            boolean pdfGenerated = result.pdfFile() != null && Files.exists(result.pdfFile());

            String latexSource = null;
            if (texGenerated) {
                try {
                    latexSource = Files.readString(result.texFile(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    // The file exists but cannot be read: report it rather than let the
                    // response claim a source that is not there.
                    log.warn("Generated LaTeX source could not be read: {}", e.getMessage());
                    texGenerated = false;
                }
            }

            AuditRunResponse response = new AuditRunResponse(
                    result.report(),
                    texGenerated,
                    pdfGenerated,
                    latexSource,
                    result.compileError(),
                    retries.events());

            log.info("Full analysis completed for '{}' — tex={}, pdf={}, retries={} (of which {} infrastructural)",
                    filename, texGenerated, pdfGenerated,
                    response.retryEvents().size(), response.infrastructuralRetries());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during full analysis of '{}'", filename, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Error during analysis",
                            "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                            "retryEvents", retries.events()
                    ));
        } finally {
            TokenUsageAccumulator.clear();
            RetryEventCollector.clear();
        }
    }

    /**
     * Returns the expected-feature knowledge base as it stands.
     * <p>
     * That collection determines <em>what the feature-check agent looks for</em>, so it
     * is an input of any evaluation rather than infrastructure: it has to be frozen and
     * archived alongside the results. Exposing it here spares the caller from connecting
     * to this system's database with its own driver and credentials merely to read a
     * list.
     *
     * <p>Endpoint: GET /api/knowledge-base
     */
    @GetMapping(value = "/knowledge-base", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Feature>> knowledgeBase() {
        List<Feature> features = featureRepository.findAll();
        log.info("Knowledge base export requested: {} features", features.size());
        return ResponseEntity.ok(features);
    }

    /**
     * Checks the status of dependent services (Flask PDF extractor).
     *
     * <p>Endpoint: GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean pdfServiceUp = ingestionService.isServiceAvailable();
        return ResponseEntity.ok(Map.of(
                "status", pdfServiceUp ? "ok" : "degraded",
                "service", "SWE-Audit-Agent",
                "pdfExtractor", pdfServiceUp ? "up" : "down"
        ));
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
