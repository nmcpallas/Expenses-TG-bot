# Bot Flow v2: User Flow

Документ описывает текущие пользовательские сценарии Telegram-бота после разделения flow-логики на `UpdateHandler`, `FlowDispatcher` и отдельные flow-сервисы.

Настройки запуска, сборки, gRPC и mock ML описаны отдельно: [SETUP.md](SETUP.md).

## Общая схема обработки

Входная точка: `UpdateHandler`.

`UpdateHandler` отвечает за:

1. Получить `chatId` из `Update`.
2. Найти активную `UserSession` в cache.
3. Если session есть, продолжить текущий flow.
4. Если session нет и пришел текст, попробовать быстрый ввод траты.
5. Если session нет и пришел callback, стартовать flow из callback-data.
6. После обработки удалить session, если `session.step == DONE`.

Упрощенно:

```text
Telegram Update
    -> UpdateHandler
        -> active session exists? continueProcess(update, session)
        -> text message? QuickExpenseFlowService.tryStartQuickExpense(...)
        -> callback? create session from Step.valueOf(callbackData)
        -> otherwise "Я вас не понимаю"
```

`FlowDispatcher` отвечает за маршрутизацию по `FlowType`:

```text
ADD_EXPENSE       -> AddExpenseFlowService
QUICK_EXPENSE     -> QuickExpenseFlowService
ADD_CATEGORY      -> AddCategoryFlowService
SET_MONTH_LIMIT   -> MonthLimitFlowService
SET_MONTH_START   -> MonthStartDayFlowService
DOWNLOAD_EXCEL    -> ExcelExportFlowService
GENERAL_MENU      -> status/general menu handling
```

Если в session нет `flow`, `FlowDispatcher` восстанавливает его через `FlowTypeResolver`.

## Главное меню

Меню создается в `GeneralMenu`.

Доступные действия:

| Кнопка | Step | FlowType |
|---|---|---|
| Ввести одну трату | `START_ADD_EXPENSE` | `ADD_EXPENSE` |
| Текущий статус по тратам | `SHOW_CURRENT_STATUS` | `GENERAL_MENU` |
| Добавить месячное ограничение | `START_SET_MONTH_LIMIT` | `SET_MONTH_LIMIT` |
| Добавить день начала/конца месяца | `START_SET_MONTH_START_DAY` | `SET_MONTH_START` |
| Добавить категорию | `START_ADD_CATEGORY` | `ADD_CATEGORY` |
| Получить траты в виде excel-файла | `START_DOWNLOAD_EXCEL` | `DOWNLOAD_EXCEL` |

Кнопка "Назад к главному меню" использует:

```text
SHOW_GENERAL_MENU
```

При этом `FlowDispatcher` отправляет главное меню и переводит session в `DONE`.

## Быстрый ввод траты

Сервис: `QuickExpenseFlowService`.

Пользователь пишет текст свободного формата:

```text
250 кофе
```

Flow:

```text
text message without active session
    -> QuickExpenseParser.parse(...)
    -> ExpenseService.getCategories(chatId)
    -> QuickExpenseEligibilityService.resolveMode(...)
    -> ExpenseMlClient.predict(...)
```

Если текст не похож на быструю трату, quick-flow не стартует, и бот показывает главное меню.

### Доступность быстрого ввода

Быстрый ввод открывается постепенно и зависит от количества пользовательских данных.

Режимы:

```text
DISABLED
REVIEW_ONLY
AUTO_SAVE_ALLOWED
```

Правила:

| Условие | Режим | Поведение |
|---|---|---|
| меньше 2 категорий или меньше 10 трат | `DISABLED` | quick-flow не стартует |
| 2+ категории и 10+ трат, но меньше 3 категорий или меньше 30 трат | `REVIEW_ONLY` | бот предлагает категорию, но всегда просит подтверждение |
| 3+ категории и 30+ трат | `AUTO_SAVE_ALLOWED` | бот может сохранить трату автоматически, если ML уверен |

Если quick-flow отключен, `UpdateHandler` считает текст обычным сообщением без active session и показывает главное меню.

### Нет категорий

Если у чата нет категорий:

```text
У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату
```

Бот показывает кнопку добавления категории:

```text
START_ADD_CATEGORY
```

### ML уверен в категории

Если ML-сервис вернул `needsReview=false` и `acceptedCategoryId` есть:

```text
expenseService.addSpending(...)
```

Ответ пользователю:

```text
Трата сохранена: 250 · Кофе · кофе
```

В этом сценарии session не остается активной.

### ML требует подтверждение

Если ML-сервис вернул `needsReview=true`:

```text
session.step = AWAITING_QUICK_EXPENSE_CATEGORY
session.flow = QUICK_EXPENSE
session.rawText = "250 кофе"
session.amount = 250
session.description = "кофе"
```

Ответ пользователю:

```text
Не уверен в категории для траты "кофе". Выберите категорию или введите свою.
```

Кнопки:

- категории из `alternatives`, если они есть;
- иначе все категории чата;
- `Ввести свою категорию`.

### Пользователь выбрал категорию

Входной step:

```text
AWAITING_QUICK_EXPENSE_CATEGORY
```

Callback-data содержит `UUID` категории.

Действия:

```text
session.categoryId = selected category
expenseService.addSpending(...)
session.step = DONE
```

### Пользователь вводит свою категорию

Если пользователь нажал `Ввести свою категорию`:

```text
session.step = AWAITING_QUICK_EXPENSE_CATEGORY_NAME
session.flow = QUICK_EXPENSE
```

Бот просит:

```text
Введите название категории
```

После текстового ответа:

```text
expenseService.createCategory(...)
session.categoryId = new category
expenseService.addSpending(...)
session.step = DONE
```

## Обычный ввод траты

Сервис: `AddExpenseFlowService`.

Старт из главного меню:

```text
START_ADD_EXPENSE
```

Flow:

```text
START_ADD_EXPENSE
    -> AWAITING_EXPENSE_AMOUNT
    -> AWAITING_EXPENSE_CATEGORY
    -> AWAITING_EXPENSE_DESCRIPTION
    -> DONE
```

### Шаг 1. Запрос суммы

На `START_ADD_EXPENSE` бот отвечает:

```text
Отправьте потраченную сумму
```

Session:

```text
session.step = AWAITING_EXPENSE_AMOUNT
session.flow = ADD_EXPENSE
```

### Шаг 2. Ввод суммы

Пользователь отправляет сумму:

```text
100
```

или:

```text
100,50
```

Бот сохраняет:

```text
session.amount = parsed amount
session.step = AWAITING_EXPENSE_CATEGORY
```

Если сумма некорректная:

```text
Введено некорректное значение суммы, попробуйте еще раз
```

Step остается:

```text
AWAITING_EXPENSE_AMOUNT
```

### Шаг 3. Выбор категории

Если категорий нет:

```text
У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату
```

Session завершается:

```text
session.step = DONE
```

Если категории есть, бот показывает список категорий и кнопку:

```text
Добавить категорию -> START_ADD_CATEGORY
```

Если пользователь выбрал существующую категорию:

```text
session.categoryId = selected category
session.step = AWAITING_EXPENSE_DESCRIPTION
```

Бот отвечает:

```text
Введите описание траты
```

Если пользователь нажал `Добавить категорию`, flow переключается:

```text
session.step = AWAITING_CATEGORY_NAME
session.flow = ADD_CATEGORY
```

### Шаг 4. Описание траты

Пользователь вводит описание:

```text
кофе
```

Бот вызывает:

```text
expenseService.addSpending(userId, chatId, session)
```

Session:

```text
session.step = DONE
session.flow = ADD_EXPENSE
```

Ответ:

```text
Трата успешно сохранена
```

## Добавление категории

Сервис: `AddCategoryFlowService`.

Старт:

```text
START_ADD_CATEGORY
```

Flow:

```text
START_ADD_CATEGORY
    -> AWAITING_CATEGORY_NAME
    -> DONE
```

На старте бот просит:

```text
Введите название категории
```

После текстового ответа:

```text
expenseService.createCategory(chatId, userId, categoryName)
session.step = DONE
session.flow = ADD_CATEGORY
```

Ответ:

```text
Категория успешно добавлена
```

## Месячное ограничение

Сервис: `MonthLimitFlowService`.

Старт:

```text
START_SET_MONTH_LIMIT
```

Flow:

```text
START_SET_MONTH_LIMIT
    -> AWAITING_MONTH_LIMIT
    -> DONE
```

На старте бот просит:

```text
Отправьте сумму ограничения
```

После ответа:

```text
expenseService.setOrUpdateLimitation(userId, chatId, limitationText)
```

При успехе:

```text
session.step = DONE
session.flow = SET_MONTH_LIMIT
```

Ответ:

```text
Ограничение успешно установлено
```

При `WrongFormat`:

```text
Ошибка в формате суммы ограничения. Используйте, пожалуйста, только цифры. Попробуйте еще раз
```

Session остается на:

```text
AWAITING_MONTH_LIMIT
```

## День начала/конца месяца

Сервис: `MonthStartDayFlowService`.

Старт:

```text
START_SET_MONTH_START_DAY
```

Flow:

```text
START_SET_MONTH_START_DAY
    -> AWAITING_MONTH_START_DAY
    -> DONE
```

На старте бот просит:

```text
Отправьте день начала/окончания месяца
```

После ответа:

```text
expenseService.saveInputStartDay(userId, chatId, day)
```

При успехе:

```text
session.step = DONE
session.flow = SET_MONTH_START
```

Ответ:

```text
День успешно установлен
```

При `WrongFormat`:

```text
Ошибка в формате суммы ограничения. Используйте, пожалуйста, только цифры. Попробуйте еще раз
```

Session остается на:

```text
AWAITING_MONTH_START_DAY
```

## Текущий статус

Сервис: `StatusFlowService`.

Старт:

```text
SHOW_CURRENT_STATUS
```

FlowType:

```text
GENERAL_MENU
```

Действия:

```text
expenseService.getStatus(chatId, userId)
session.step = DONE
session.flow = GENERAL_MENU
```

Ответ содержит:

```text
Месячное ограничение: ...
Потрачено на данный момент: ...
Остаток в этом месяце: ...
<категория>: <сумма>
```

## Excel-экспорт

Сервис: `ExcelExportFlowService`.

Старт:

```text
START_DOWNLOAD_EXCEL
```

Flow:

```text
START_DOWNLOAD_EXCEL
    -> AWAITING_EXCEL_MONTH
    -> send document
```

На старте бот показывает календарь месяцев:

```text
Выберите месяц в текущем году
```

После выбора месяца callback-data содержит enum `Month`, например:

```text
MARCH
```

Действия:

```text
expenseService.getExpenses(chatId, Month.MARCH)
ExpenseExcelExporter.exportExpensesToExcel(...)
telegramClient.execute(SendDocument)
```

Ответ-документ:

```text
expenses.xlsx
```

Caption:

```text
Вот ваши расходы в формате Excel
```

## Сессии и завершение

Session хранится в cache в `UpdateHandler`.

После каждого продолжения flow:

```text
if (session.step == DONE) {
    removeSession(chatId)
}
```

Это значит:

- завершенные сценарии не продолжаются случайно;
- следующий текст пользователя без active session снова сначала проверяется как quick expense;
- callback из меню создает новую session.

## Список Step

Текущие значения `Step`:

```text
START_QUICK_EXPENSE
START_ADD_EXPENSE
START_DOWNLOAD_EXCEL
SHOW_CURRENT_STATUS
START_SET_MONTH_START_DAY
START_ADD_CATEGORY
START_SET_MONTH_LIMIT
AWAITING_MONTH_START_DAY
AWAITING_EXCEL_MONTH
AWAITING_EXPENSE_AMOUNT
AWAITING_EXPENSE_CATEGORY
AWAITING_QUICK_EXPENSE_CATEGORY
AWAITING_CATEGORY_NAME
AWAITING_EXPENSE_DESCRIPTION
AWAITING_QUICK_EXPENSE_CATEGORY_NAME
AWAITING_MONTH_LIMIT
SHOW_GENERAL_MENU
DONE
```

## Список FlowType

Текущие значения `FlowType`:

```text
ADD_EXPENSE
QUICK_EXPENSE
ADD_CATEGORY
SET_MONTH_LIMIT
SET_MONTH_START
DOWNLOAD_EXCEL
GENERAL_MENU
```
