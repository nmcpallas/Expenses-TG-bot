# Production Setup

Production/runtime setup предполагает, что Java-бот запускается в окружении с внешней БД, production Telegram token и реальным Python ML-сервисом по gRPC.

Пользовательские сценарии описаны отдельно: [README.md](README.md).
Локальная разработка описана отдельно: [LOCAL_SETUP.md](LOCAL_SETUP.md).

## Runtime config

Основной файл:

```text
src/main/resources/application.yaml
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

Обязательные env:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

Flyway migrations:

```text
src/main/resources/migrations
```

При старте приложения Flyway применит миграции автоматически.

## Telegram

Env:

```text
TG_BOT_TOKEN
```

Важно: один Telegram bot token должен использоваться только одним активным приложением с long polling. Для локальной разработки используй отдельный test bot token.

## ML integration

Production-настройки:

```yaml
expense:
  ml:
    enabled: ${EXPENSE_ML_ENABLED:true}
    mock-enabled: ${EXPENSE_ML_MOCK_ENABLED:false}
    host: ${EXPENSE_ML_HOST:localhost}
    port: ${EXPENSE_ML_PORT:50051}
    deadline-ms: ${EXPENSE_ML_DEADLINE_MS:1500}
```

Рекомендуемые production env:

```text
EXPENSE_ML_ENABLED=true
EXPENSE_ML_MOCK_ENABLED=false
EXPENSE_ML_HOST=<ml-service-host>
EXPENSE_ML_PORT=50051
EXPENSE_ML_DEADLINE_MS=1500
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

По умолчанию `mock-enabled=false`.

В этом режиме Spring создает:

```text
ManagedChannel
ExpenseClassifierBlockingStub
GrpcExpenseMlClient
```

Если `expense.ml.enabled=false`, `GrpcExpenseMlClient` остается Spring bean, но реальные вызовы не выполняются:

- `predict(...)` возвращает `reviewOnly`;
- `uploadTrainingData(...)` возвращает результат с сообщением, что интеграция выключена.

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

## Сборка

```bash
./mvnw package
```

## Тесты

```bash
./mvnw test
```

Примечание: если Maven падает на очистке `target/protoc-dependencies`, проблема относится к protobuf plugin cleanup. Обычно помогает удалить `target` вручную или перезапустить команду вне ограниченного окружения.
