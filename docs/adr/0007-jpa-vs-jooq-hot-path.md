# ADR-0007: JPA для домена, JdbcClient для hot-path (без jOOQ в MVP)

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Разраб Бэк (BE-1)

## Контекст

`02-backend.md` допускает Spring Data JPA / Hibernate для CRUD-домена и `JdbcClient` **или jOOQ** для горячих запросов (диспетчер `FOR UPDATE SKIP LOCKED`, bulk-обновления публикаций). Нужно зафиксировать выбор до BE-2/BE-4, чтобы не плодить два SQL-DSL без необходимости.

Hot-path требования:
- батчи по 500 строк, 2–4 реплики scheduler, пик 2 000 publications/с;
- точный SQL с `SKIP LOCKED`, partial indexes, партиции;
- короткие транзакции; без лишней гидрации сущностей Hibernate.

## Решение

1. **Доменный CRUD (API, админка, биллинг, каналы, посты):** Spring Data JPA / Hibernate 6, `ddl-auto=validate`, сущности в `libs/persistence`.
2. **Hot-path публикаций (dispatcher claim, reaper, точечные state transitions в publisher):** Spring **`JdbcClient`** (и при необходимости `NamedParameterJdbcTemplate`) с явным SQL в `libs/persistence` (пакеты `…jdbc` / `…hotpath`).
3. **jOOQ в MVP не подключаем.** Пересмотр — если появятся сложные динамические SQL-отчёты/конструкторы запросов, где type-safe DSL окупит codegen и зависимость; тогда отдельный ADR (supersede).
4. Граница: сервисы `domain` зависят от портов (интерфейсов); JPA-репозитории и JdbcClient-адаптеры живут в `persistence` / apps. Domain не импортирует Hibernate/JPA (ArchUnit, ADR-0002).

## Альтернативы

- **jOOQ сразу для hot-path** — отложено: выигрыш в type-safety меньше стоимости codegen/Flyway-синхронизации на старте; `JdbcClient` + тесты на Testcontainers достаточны для фиксированных SQL.
- **Только JPA / Criteria / `@Query` native везде** — отвергнуто для dispatcher: `SKIP LOCKED` + `RETURNING` + минимальный mapping надёжнее и прозрачнее через JDBC.
- **MyBatis** — лишняя абстракция при малом числе hot-query.

## Последствия

- Flyway — единственный источник схемы; Hibernate только валидирует.
- SQL hot-path покрывается интеграционными тестами (BE-2/BE-4).
- При введении jOOQ позже codegen должен брать схему из Flyway, без второй «истины».
