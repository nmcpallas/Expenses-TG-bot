# ---------- Stage 1: Build ----------
FROM eclipse-temurin:21-jdk as build

WORKDIR /app

# Копируем Maven wrapper и pom.xml отдельно, чтобы закешировать зависимости
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Загружаем зависимости, чтобы они кешировались
RUN ./mvnw -q dependency:go-offline

# Теперь копируем исходники
COPY src ./src

# Сборка без тестов
RUN ./mvnw -q -DskipTests package

# ---------- Stage 2: Run ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Забираем JAR из Stage 1
COPY --from=build /app/target/*.jar app.jar

# Оптимизированный запуск для Spring Boot / Telegram бота
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
