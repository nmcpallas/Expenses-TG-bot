# Local Setup

Локальный сценарий предназначен для проверки Telegram user-flow с debugger в IntelliJ IDEA.

Идея:

```text
Postgres -> Docker Compose
Java bot -> локально из IntelliJ IDEA
ML -> MockExpenseMlClient
```

Пользовательские сценарии описаны отдельно: [README.md](README.md).
Production-настройки описаны отдельно: [PROD_SETUP.md](PROD_SETUP.md).

## Требования

- Docker Desktop запущен.
- Создан отдельный Telegram test bot token.
- Java-приложение запускается локально, не в контейнере.

Важно: не используй production Telegram token локально. Telegram long polling должен иметь только одного активного consumer на один bot token.

## Docker Compose

Compose-файл:

```text
docker-compose.yml
```

Сейчас он поднимает только Postgres:

```yaml
services:
  postgres:
    image: postgres:15-alpine
    container_name: local-postgres
    environment:
      POSTGRES_DB: tg_budget
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
    ports:
      - "5542:5432"
```

## Spring Boot Docker Compose

В `application-local.yaml` включено:

```yaml
spring:
  docker:
    compose:
      enabled: true
      file: docker-compose.yml
      lifecycle-management: start_only
```

Это значит:

- при старте приложения с profile `local` Spring Boot выполнит `docker compose up`;
- Postgres будет доступен на `localhost:5542`;
- Java останется локальным процессом, поэтому breakpoints в IDEA работают;
- при остановке Java-приложения контейнер не останавливается из-за `start_only`.

## Local database

`application-local.yaml`:

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

При старте приложения Flyway применит миграции автоматически.

## Local ML mock

Для локальной проверки flow без Python ML-сервиса включи:

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

## Запуск из IntelliJ IDEA

Run configuration:

```text
Main class: com.cpallas.expenses.ExpensesTgBotApplication
Active profiles: local
Environment:
  TG_BOT_TOKEN=<token тестового Telegram-бота>
  EXPENSE_ML_MOCK_ENABLED=true
```

Flow запуска:

```text
Run in IDEA
    -> Spring Boot starts docker-compose.yml
    -> Postgres listens on localhost:5542
    -> Flyway applies migrations
    -> Java bot starts locally
    -> debugger works in IDEA
```

## Запуск из Maven

```bash
TG_BOT_TOKEN=<token тестового Telegram-бота> \
EXPENSE_ML_MOCK_ENABLED=true \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Тестирование flow в Telegram

1. Запусти приложение с profile `local`.
2. Открой тестового Telegram-бота.
3. Проверь quick-flow:

```text
250 кофе
```

4. Проверь кнопочные flow из главного меню:

- обычная трата;
- добавление категории;
- месячное ограничение;
- день начала/конца месяца;
- текущий статус;
- Excel-экспорт.

## Остановка локальной БД

Так как используется `lifecycle-management: start_only`, Spring Boot не остановит контейнер при завершении приложения.

Остановить вручную:

```bash
docker compose down
```

Если нужно удалить данные:

```bash
docker compose down -v
```
