package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpenseTrainingExample;
import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.service.dto.UploadTrainingDataResult;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;

import java.util.List;

public interface ExpenseMlClient {

    ExpenseCategoryPrediction predict(ChatId chatId, QuickExpense expense, List<CategoryJpa> categories);

    UploadTrainingDataResult uploadTrainingData(ChatId chatId,
                                                boolean replaceChatData,
                                                boolean trainAfterUpload,
                                                List<ExpenseTrainingExample> examples);
}
