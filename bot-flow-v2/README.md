# Bot Flow v2

Новый flow добавляет быструю запись расхода свободным текстом.

## Happy path

Пользователь пишет:

```text
250 кофе
```

Java-бот:

1. Парсит сумму `250` и описание `кофе`.
2. Загружает категории текущего чата.
3. Вызывает Python ML-сервис по gRPC `Predict`.
4. Если `needs_review=false`, сохраняет трату сразу.
5. Отвечает пользователю:

```text
Трата сохранена: 250 · Кафе · кофе
```

## Low confidence path

Если Python-сервис вернул `needs_review=true`, бот показывает варианты:

```text
Не уверен в категории для траты "кофе". Выберите категорию или введите свою.
```

Кнопки:

- категории из `alternatives`;
- `Ввести свою категорию`.

Если пользователь выбирает предложенную категорию, бот сохраняет расход.

Если пользователь выбирает `Ввести свою категорию`, бот просит название, создает категорию в текущем чате и сохраняет расход в нее.

## gRPC contract

Java-контракт лежит в:

```text
src/main/proto/expense_classifier.proto
```

Он синхронизирован с Python-проектом:

```text
/Users/unknown1/IdeaProjects/Expense-ML-service/proto/expense_classifier.proto
```

## Runtime config

```yaml
expense:
  ml:
    enabled: true
    host: localhost
    port: 50051
    deadline-ms: 1500
```
