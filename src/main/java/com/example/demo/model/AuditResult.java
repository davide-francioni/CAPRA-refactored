package com.example.demo.model;

import java.nio.file.Path;

/**
 * Complete result of the audit pipeline: report + generated files.
 *
 * @param report       Structured audit report
 * @param texFile      Path of the generated LaTeX file
 * @param pdfFile      Path of the compiled PDF (null if compilation failed)
 * @param compileError Why compilation failed, or {@code null} if it succeeded. Carried
 *                     here rather than left in the log because a failed compilation is
 *                     a blocking outcome for an automated evaluation, and the caller
 *                     needs to know why without reading the server's diary
 */
public record AuditResult(
        AuditReport report,
        Path texFile,
        Path pdfFile,
        String compileError
) {
    /** Convenience for the ordinary path, where compilation succeeded. */
    public AuditResult(AuditReport report, Path texFile, Path pdfFile) {
        this(report, texFile, pdfFile, null);
    }
}
