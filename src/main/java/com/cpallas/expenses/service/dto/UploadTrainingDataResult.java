package com.cpallas.expenses.service.dto;

public record UploadTrainingDataResult(
        int acceptedCount,
        int skippedCount,
        boolean trained,
        String message
) {
}
