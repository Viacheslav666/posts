# ADR-0001: Стек Java 21 + Spring Boot 3

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Разраб Бэк (BE-1)

## Контекст

Нужен единый бэкенд-стек для SMM-платформы Posts с целевой нагрузкой 100 000 пользователей (запас ×3), пиком 2 000 публикаций/с и p99 задержки публикации ≤ 60 с. Сервисы должны быть stateless, горизонтально масштабируемыми, с надёжным блокирующим I/O к соцсетям и БД. Решение фиксирует рантайм, фреймворк и ключевые библиотеки из `docs/specs/00-overview.md` и `docs/specs/02-backend.md`.

## Решение

1. **Язык / рантайм:** Java 21 LTS (Eclipse Temurin). Virtual threads включены в `api`, `publisher`, `ingest`, `notifications` для блокирующего HTTP/JDBC/AMQP.
2. **Фреймворк:** Spring Boot 3.4.x (BOM): Spring Web MVC + `RestClient`, Spring Security 6 (OAuth2 Client + Resource Server/JWT), Validation, Actuator, WebSocket (STOMP), Spring AMQP, Spring Data JPA / Redis.
3. **Сборка:** Gradle (Kotlin DSL) multi-module в `backend/`, version catalog `gradle/libs.versions.toml`, convention-плагины в `build-logic/` (`posts.java-conventions`, `posts.spring-app`, `posts.integration-test`). Spotless (palantir-java-format), Error Prone, JaCoCo, ArchUnit, Jib → distroless Java 21.
4. **Данные и инфраструктурные клиенты:** PostgreSQL 16 + Flyway; Redis 7; RabbitMQ 3.13 (quorum); ClickHouse (фаза 2); S3 SDK v2; Resilience4j + Bucket4j; ShedLock; Micrometer + OpenTelemetry; Sentry.
5. **Тесты:** JUnit 5, AssertJ, Mockito, Testcontainers (`@ServiceConnection`), WireMock, ArchUnit.

Версии библиотек ведутся в `libs.versions.toml`; апгрейд минорных/патч-версий Spring Boot допустим без нового ADR, major — только новым ADR.

## Альтернативы

- **Kotlin / Quarkus / Micronaut** — отвергнуто: экосистема Spring + найм/агентные навыки, зрелость AMQP/Security/JPA.
- **Java 17** — отвергнуто: нужны virtual threads и LTS 21 как целевой рантайм.
- **WebFlux / Netty повсюду** — отвергнуто: адаптеры соцсетей и JDBC удобнее на блокирующем I/O + virtual threads; изоляция отказов достигается очередями и CB, а не реактивным стеком.

## Последствия

- Единый toolchain для всех `apps/*` и `libs/*`; образы Jib/distroless; graceful shutdown и virtual threads — обязательные настройки приложений.
- Hot-path диспетчера и выбор JPA vs JDBC/jOOQ — см. ADR-0007; топология очередей — ADR-0005; шифрование токенов — ADR-0006.
- DEV-1 зафиксировал практические отклонения скелета (OpenAPI-экспорт, Testcontainers 1.21.4) в ADR-0008; они остаются в силе, пока не supersede.
