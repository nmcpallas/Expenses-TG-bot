package com.cpallas.expenses.reporting.contract;

public record ReportArtifact(
        String bucket,
        String objectKey,
        String fileName,
        String contentType,
        long size,
        String sha256,
        String downloadUrl
) {
}
