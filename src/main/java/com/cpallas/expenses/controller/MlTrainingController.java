package com.cpallas.expenses.controller;

import com.cpallas.expenses.service.ml.ExpenseTrainingDataJobService;
import com.cpallas.expenses.service.ml.ExpenseTrainingDataJobService.TrainingDataUploadSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ml/training")
public class MlTrainingController {

    private final ExpenseTrainingDataJobService expenseTrainingDataJobService;

    @PostMapping("/run")
    public TrainingDataUploadSummary runTraining() {
        return expenseTrainingDataJobService.uploadTrainingData();
    }
}
