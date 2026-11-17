# ADR-0002: Модульная структура бэкенда

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Разраб Бэк (BE-1)

## Контекст

Монорепозиторий Posts требует явных границ между доменной логикой, адаптерами соцсетей и deployable-приложениями. Без enforcement модули быстро срастутся (apps ↔ apps, domain → Spring Web/JPA). Нужна структура, согласованная с `00-overview.md` §7 и правилами ArchUnit из `02-backend.md` §1 п.8.

## Решение

### Deployable-приложения (`backend/apps/*`)

| Модуль | Артефакт | Назначение |
|---|---|---|
| `api` | posts-api | REST `/api/v1`, WebSocket, auth |
| `scheduler` | posts-scheduler | Диспетчер `SKIP LOCKED`, reaper, ShedLock-задачи, outbox-relay |
| `publisher` | posts-publisher | Один образ, Deployment на сеть (`POSTS_PUBLISHER_NETWORK`) |
| `media` | posts-media | Пост-обработка медиа |
| `ingest` | posts-ingest | Вебхуки → нормализованные события |
| `notifications` | posts-notifications | Email, TG-бот, Web Push, WS fan-out |

`analytics` и `social-rutube` — фаза 2, не входят в MVP-каркас.

### Библиотеки (`backend/libs/*`)

| Модуль | Содержимое |
|---|---|
| `common` | RFC 7807 helpers, UUIDv7, время/TZ, JSON-утилиты |
| `domain` | Доменные сервисы и порты (без Spring Web/AMQP/JPA-адаптеров) |
| `persistence` | JPA-сущности, репозитории, JdbcClient hot-path, Flyway |
| `messaging` | Spring AMQP: топология, сообщения, outbox-relay |
| `security` | JWT, RBAC, envelope-шифрование токенов |
| `ratelimit` | Bucket4j/Redis + Resilience4j-конфиг |
| `content` | Рендеринг контента под сети, лимиты |
| `social-api` | `SocialAdapter`, общая модель, классификация ошибок |
| `social-vk` / `social-telegram` / `social-ok` / `social-max` | Адаптеры сетей |
| `test-support` | Testcontainers-фикстуры, ArchUnit-правила, общие тестовые утилиты |

### Границы (проверяются ArchUnit в `libs/test-support`)

1. `ru.posts.domain..` не зависит от Spring Web, Spring AMQP, JPA/Hibernate.
2. Пакеты `ru.posts.api|scheduler|publisher|media|ingest|notifications..` не зависят друг от друга; зависят только от `libs/*`.
3. `ru.posts.social.{vk|telegram|ok|max}..` зависят только от `ru.posts.social.api..`, `ru.posts.common..`, `ru.posts.ratelimit..` (из пакетов Posts).

Gradle: `apps` → `implementation(project(":libs:…"))`; apps не включают другие apps. ArchUnit-сьют подключён к `test` всех модулей через `posts.java-conventions` (`testImplementation` на `:libs:test-support`).

Модуль `libs/architecture` (черновик DEV-1) **удалён**: правила перенесены в `test-support` согласно BE-1.

## Альтернативы

- **Один модуль / multi-package** — отвергнуто: нет compile-time границ.
- **Отдельный микросервисный репозиторий на приложение** — отвергнуто для MVP: общий домен, Flyway и контракт проще в монорепо.
- **Отдельный модуль только под ArchUnit** — отвергнуто BE-1: правила живут рядом с Testcontainers-фикстурами в `test-support`.

## Последствия

- Новые библиотеки/приложения добавляются в `settings.gradle.kts` и в список импорта ArchUnit.
- Нарушение границ ломает `./gradlew build` / CI job `backend`.
- Фаза 2 (`analytics`, `social-rutube`) подключается тем же шаблоном модулей и правил.
