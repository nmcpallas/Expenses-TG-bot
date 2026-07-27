package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpensePredictionAlternative;
import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultCategoryClassifier {

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("Кафе", List.of(
                "кофе", "кафе", "ресторан", "обед", "ужин", "завтрак", "доставка", "фастфуд"
        ));
        KEYWORDS.put("Продукты", List.of(
                "продукт", "супермаркет", "магазин", "молоко", "хлеб", "овощ", "фрукт", "рынок"
        ));
        KEYWORDS.put("Транспорт", List.of(
                "такси", "метро", "автобус", "бензин", "топливо", "парков", "проезд"
        ));
        KEYWORDS.put("Дом", List.of(
                "аренда", "квартир", "коммунал", "электр", "газ", "вода", "интернет", "ремонт"
        ));
        KEYWORDS.put("Здоровье", List.of(
                "аптек", "лекар", "врач", "клиник", "анализ", "стоматолог", "здоров"
        ));
        KEYWORDS.put("Развлечения", List.of(
                "кино", "театр", "игр", "концерт", "подписк", "развлеч"
        ));
        KEYWORDS.put("Покупки", List.of(
                "одежд", "обув", "техника", "подар", "маркетплейс", "аксессуар"
        ));
    }

    public Optional<ExpenseCategoryPrediction> predict(QuickExpense expense, List<CategoryJpa> categories) {
        String description = normalize(expense.description());

        Optional<CategoryJpa> exactCategory = categories.stream()
                .filter(category -> description.contains(normalize(category.getName())))
                .findFirst();
        if (exactCategory.isPresent()) {
            return Optional.of(accepted(exactCategory.get(), 0.99, categories));
        }

        for (Map.Entry<String, List<String>> rule : KEYWORDS.entrySet()) {
            boolean matched = rule.getValue().stream().anyMatch(description::contains);
            if (!matched) {
                continue;
            }
            Optional<CategoryJpa> category = categories.stream()
                    .filter(candidate -> normalize(candidate.getName()).equals(normalize(rule.getKey())))
                    .findFirst();
            if (category.isPresent()) {
                return Optional.of(accepted(category.get(), 0.90, categories));
            }
        }
        return Optional.empty();
    }

    private ExpenseCategoryPrediction accepted(CategoryJpa category,
                                                double confidence,
                                                List<CategoryJpa> categories) {
        List<ExpensePredictionAlternative> alternatives = categories.stream()
                .filter(candidate -> !candidate.getId().equals(category.getId()))
                .limit(2)
                .map(candidate -> new ExpensePredictionAlternative(
                        candidate.getId(),
                        candidate.getName(),
                        0.0
                ))
                .toList();
        return new ExpenseCategoryPrediction(
                category.getId(),
                category.getName(),
                confidence,
                false,
                alternatives
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
