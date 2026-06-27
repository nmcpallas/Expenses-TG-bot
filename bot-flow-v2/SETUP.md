# Bot Flow v2: Настройки и сборка

Документ описывает runtime-настройки, gRPC-интеграцию с ML-сервисом, mock mode и команды сборки для Java-бота.

Пользовательские сценарии описаны отдельно: [README.md](README.md).

## Runtime config

Основные настройки находятся в:

```text
src/main/resources/application.yaml
src/main/resources/application-local.yaml
```

Базовые параметры:

```yaml
server:
  port: 8080

telegram:
  enabled: true
  bot:
    username: expenses_statistic_bot
    token: ${TG_BOT_TOKEN}

system:
  dispatcher-capacity: ${DISPATCHER-CAPACITY:100}
  chat-blocking-duration: ${CHAT_BLOCKING_DURATION:1000}
```

## Database

Production/default config использует env-переменные:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

Локальный профиль `application-local.yaml` сейчас настроен на:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5542/tg_budget
    username: test
    password: test
```

Flyway migrations лежат в:

```text
src/main/resources/migrations
```

## ML integration config

ML-настройки:

```yaml
expense:
  ml:
    enabled: ${EXPENSE_ML_ENABLED:true}
    mock-enabled: ${EXPENSE_ML_MOCK_ENABLED:false}
    host: ${EXPENSE_ML_HOST:localhost}
    port: ${EXPENSE_ML_PORT:50051}
    deadline-ms: ${EXPENSE_ML_DEADLINE_MS:1500}
```

Поля:

| Property | Env | Назначение |
|---|---|---|
| `expense.ml.enabled` | `EXPENSE_ML_ENABLED` | включает/выключает реальные ML-вызовы внутри `GrpcExpenseMlClient` |
| `expense.ml.mock-enabled` | `EXPENSE_ML_MOCK_ENABLED` | поднимает `MockExpenseMlClient` вместо gRPC-клиента |
| `expense.ml.host` | `EXPENSE_ML_HOST` | host Python ML-сервиса |
| `expense.ml.port` | `EXPENSE_ML_PORT` | gRPC port Python ML-сервиса |
| `expense.ml.deadline-ms` | `EXPENSE_ML_DEADLINE_MS` | deadline gRPC-вызова |

## Real gRPC ML mode

По умолчанию:

```yaml
expense:
  ml:
    mock-enabled: false
```

В этом режиме Spring создает:

```text
ManagedChannel
ExpenseClassifierBlockingStub
GrpcExpenseMlClient
```

Если `expense.ml.enabled=false`, `GrpcExpenseMlClient` остается Spring bean, но реальные вызовы не выполняются:

- `predict(...)` возвращает `reviewOnly`;
- `uploadTrainingData(...)` возвращает результат с сообщением, что интеграция выключена.

## Mock ML mode

Для локальной разработки без Python ML-сервиса:

```yaml
expense:
  ml:
    mock-enabled: true
```

Или через env:

```text
EXPENSE_ML_MOCK_ENABLED=true
```

В этом режиме Spring создает:

```text
MockExpenseMlClient
```

И не создает:

```text
ManagedChannel
ExpenseClassifierBlockingStub
GrpcExpenseMlClient
```

Поведение mock:

- если описание траты содержит название категории, mock возвращает эту категорию как принятую с confidence `0.95`;
- если совпадения нет, mock возвращает `needsReview=true` и до трех категорий как alternatives;
- upload training data не отправляется во внешний сервис и возвращает успешный mock-result.

Пример:

```text
Категория: Кофе
Текст пользователя: 250 кофе
```

Mock вернет категорию `Кофе` как принятую.

```text
Категории: Кофе, Такси, Продукты
Текст пользователя: 250 булочка
```

Mock вернет `needsReview=true` и alternatives.

## gRPC contract

Java proto contract:

```text
src/main/proto/expense_classifier.proto
```

Он должен быть синхронизирован с Python ML-проектом:

```text
/Users/unknown1/IdeaProjects/Expense-ML-service/proto/expense_classifier.proto
```

## Генерация gRPC Java-классов

gRPC/protobuf Java-классы генерируются Maven protobuf plugin.

Команда:

```bash
./mvnw generate-sources
```

Также генерация запускается как часть:

```bash
./mvnw compile
./mvnw test
./mvnw package
```

Сгенерированные классы находятся в `target/generated-sources`.

Если IDE не видит generated classes:

1. Выполнить `./mvnw generate-sources`.
2. В IntelliJ IDEA сделать Maven Reload.
3. Проверить, что `target/generated-sources/protobuf/java` и `target/generated-sources/protobuf/grpc-java` помечены как generated sources.

## Сборка и запуск

Сборка:

```bash
./mvnw package
```

Запуск локального профиля:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Запуск с mock ML:

```bash
EXPENSE_ML_MOCK_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Запуск с реальным ML-сервисом:

```bash
EXPENSE_ML_MOCK_ENABLED=false \
EXPENSE_ML_HOST=localhost \
EXPENSE_ML_PORT=50051 \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Тесты

Команда:

```bash
./mvnw test
```

Примечание: если Maven падает на очистке `target/protoc-dependencies`, проблема относится к protobuf plugin cleanup. Обычно помогает удалить `target` вручную или перезапустить команду вне ограниченного окружения.
