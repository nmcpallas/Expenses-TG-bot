package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.ReportArtifact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class HttpReportArtifactDownloader implements ReportArtifactDownloader {

    private final HttpClient httpClient;
    private final int maximumBytes;

    public HttpReportArtifactDownloader(
            @Value("${expense.reporting.artifact-max-bytes:20971520}") int maximumBytes
    ) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.maximumBytes = maximumBytes;
    }

    @Override
    public byte[] download(ReportArtifact artifact) throws Exception {
        URI uri = URI.create(artifact.downloadUrl());
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Report artifact URL must use HTTP or HTTPS.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Unable to download report artifact: HTTP " + response.statusCode()
            );
        }
        byte[] content;
        try (InputStream body = response.body()) {
            content = body.readNBytes(maximumBytes + 1);
        }
        if (content.length > maximumBytes) {
            throw new IllegalStateException("Report artifact exceeds configured size limit.");
        }
        if (artifact.size() != content.length) {
            throw new IllegalStateException("Report artifact size does not match event metadata.");
        }
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
        );
        if (!checksum.equalsIgnoreCase(artifact.sha256())) {
            throw new IllegalStateException("Report artifact checksum does not match event metadata.");
        }
        return content;
    }
}
