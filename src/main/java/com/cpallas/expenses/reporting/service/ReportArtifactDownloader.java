package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.ReportArtifact;

public interface ReportArtifactDownloader {

    byte[] download(ReportArtifact artifact) throws Exception;
}
