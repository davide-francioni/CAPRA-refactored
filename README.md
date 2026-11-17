# CAPRA Backend (SWE-Audit-Agent)

CAPRA (Configurable Architecture Proficiency Report Assessment) is a multi-agent LLM backend that analyzes Software Engineering architecture reports (PDF), verifies evidence grounding, and produces structured feedback as JSON and LaTeX/PDF.

## What This Repository Implements

This repository contains the Spring Boot backend orchestration layer.

Extraction is delegated to an external Python/Flask microservice (configured at `audit.pdf-service.base-url`, default `http://localhost:5001`) that returns enriched text (including image/UML descriptions).

## Architecture

### CAPRA 4-stage architecture (paper)

1. Document Parsing
2. Parallel Verification Agents
3. Evidence Anchoring + Deduplication
4. Report Generation

### Current runtime pipeline in this codebase (7 steps)

1. **Text extraction** via `DocumentIngestionService` (HTTP call to Flask `/extract`, `mode=full`)
2. **Parallel agent analysis** via `MultiAgentOrchestrator`:
   - `RequirementsAgent`
   - `TestAuditorAgent`
   - `FeatureCheckAgent`
   - `TraceabilityMatrixAgent`
   - `GlossaryConsistencyAgent`
3. **Evidence Anchoring** via `EvidenceAnchoringService` (deterministic fuzzy quote verification; applied to REQ/TST issues)
4. **Confidence filtering** (`confidenceScore >= 0.65`)
5. **LLM cross-verification + deduplication** via `ConsistencyManager`
6. **LaTeX generation** via `LatexReportService`
7. **PDF compilation** via `LatexCompilerService` (`pdflatex`, 2 passes)

```text
PDF Upload (REST)
    |
    v
DocumentIngestionService  -> Flask extractor (/extract, mode=full)
    |
    v
Parallel agents (5)
    |
    +--> RequirementsAgent ---------+
    +--> TestAuditorAgent ----------+--> candidate issues (REQ/TST)
    +--> FeatureCheckAgent ---------+--> feature coverage
    +--> TraceabilityMatrixAgent ---+--> traceability matrix
    +--> GlossaryConsistencyAgent --+--> glossary issues
    |
    v
EvidenceAnchoringService (deterministic quote check)
    |
    v
Confidence filter (>= 0.65)
    |
    v
ConsistencyManager (LLM verify + deduplicate + renumber)
    |
    v
LatexReportService (.tex)
    |
    v
LatexCompilerService (pdflatex, 2 passes) -> PDF
```

## Reliability and Anti-Hallucination Controls

- Deterministic model settings (`temperature: 0.0`, OpenAI `seed: 42`)
- Evidence anchoring with normalized matching:
  - similarity `< 0.45`: discard issue
  - `0.45 - 0.70`: keep with confidence penalty
  - `>= 0.70`: confidence boost (up to +15%)
  - missing quote: confidence x `0.5`
  - quote length `< 15`: confidence x `0.7`
- `ResilientLlmCaller`:
  - lenient JSON parsing (trailing commas, comments, single quotes, unquoted fields)
  - retry with backoff (up to 3 total attempts: 2s, 4s)
- Final cross-check and dedup via `ConsistencyManager`

## Tech Stack (Current)

- Java 25
- Spring Boot 4.0.3
- Spring AI 2.0.0-M2
- OpenAI Chat model (configured default: `gpt-5.1`)
- Anthropic Chat model (configured default: `claude-haiku-4-5-20251001`)
- MongoDB (features knowledge base)
- External Flask parsing service
- `pdflatex` for final report compilation

## Project Structure

```text
src/main/java/com/example/demo
  agent/
    ConsistencyManager.java
    FeatureCheckAgent.java
    GlossaryConsistencyAgent.java
    RequirementsAgent.java
    TestAuditorAgent.java
    TraceabilityMatrixAgent.java
  config/
    AiConfig.java
    AuditProperties.java
    MongoConfig.java
  controller/
    PdfAnalysisController.java
  model/
    AuditIssue.java
    AuditReport.java
    Feature.java
    FeatureCoverage.java
    GlossaryIssue.java
    TraceabilityEntry.java
    ...
  orchestrator/
    MultiAgentOrchestrator.java
  repository/
    FeatureRepository.java
  service/
    DocumentIngestionService.java
    EvidenceAnchoringService.java
    LatexCompilerService.java
    LatexReportService.java
    ResilientLlmCaller.java
    TokenUsageAccumulator.java
```

## Configuration

Main configuration is in `src/main/resources/application.yaml`.

```yaml
spring:
  application:
    name: swe-audit-agent
  data:
    mongodb:
      host: localhost
      port: 27017
      database: features_repo
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-5.1
          max-completion-tokens: 16000
          temperature: 0.0
          seed: 42
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-haiku-4-5-20251001
          max-tokens: 8192
          temperature: 0.0

server:
  port: 8085

audit:
  pdf-service:
    base-url: http://localhost:5001
  latex:
    output-dir: ./reports
    pdflatex-path: pdflatex
```

Important note on MongoDB:

- `MongoConfig` explicitly creates `mongodb://localhost:27017/features_repo`.
- If you need another Mongo URI/database, update `src/main/java/com/example/demo/config/MongoConfig.java` as well.

## Prerequisites

- Java 25
- MongoDB running on `localhost:27017`
- External Flask extraction service running on `localhost:5001`
- `pdflatex` installed and available in `PATH`
- Environment variables:
  - `OPENAI_API_KEY`
  - `ANTHROPIC_API_KEY`

## MongoDB Data Expected by FeatureCheckAgent

Collection: `summary_features` in database `features_repo`.

Each document should contain at least:

- `feature` (string)
- `description` (string)
- `checklist` (array of strings)

Example document:

```json
{
  "feature": "UI & Interaction Design",
  "description": "Contains UI mockups and clear navigation structures",
  "checklist": [
    "UI mockups for key interfaces",
    "Clear navigation elements",
    "Input validation mechanisms"
  ]
}
```

## Run Locally

```bash
./mvnw spring-boot:run
```

Service starts on `http://localhost:8085`.

## REST API

| Method | Endpoint            | Description                                                                              |
| ------ | ------------------- | ---------------------------------------------------------------------------------------- |
| POST   | `/api/analyze`      | Analyze PDF and return compiled PDF report (or `.tex` fallback if PDF compilation fails) |
| POST   | `/api/analyze/json` | Analyze PDF and return structured `AuditReport` JSON                                     |
| GET    | `/api/health`       | Health check (mainly verifies Flask extractor availability)                              |

### Analyze and download report

```bash
curl -X POST http://localhost:8085/api/analyze \
  -F "file=@document.pdf" \
  -o audit-report.pdf
```

Response headers include token usage:

- `X-OpenAI-Input-Tokens`
- `X-OpenAI-Output-Tokens`
- `X-OpenAI-Total-Tokens`
- `X-Anthropic-Input-Tokens`
- `X-Anthropic-Output-Tokens`
- `X-Anthropic-Total-Tokens`

### Analyze JSON only

```bash
curl -X POST http://localhost:8085/api/analyze/json \
  -F "file=@document.pdf"
```

Top-level JSON structure:

```json
{
  "documentName": "document.pdf",
  "timestamp": "2026-03-01T12:34:56",
  "issues": [],
  "totalIssues": 0,
  "severityDistribution": {},
  "featureCoverage": [],
  "traceabilityMatrix": [],
  "glossaryIssues": []
}
```

## Batch Analysis Utility

`analyze_kb.sh` scans `kb/<subfolder>/`, sends each file to `/api/analyze`, and writes:

- `logs.txt` (full request/response diagnostics)
- `usage.txt` (token and timing summaries)

Run:

```bash
./analyze_kb.sh
```
