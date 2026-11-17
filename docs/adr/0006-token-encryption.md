# ADR-0006: Шифрование токенов соцсетей

- Статус: accepted
- Дата: 2026-11-17
- Авторы: Posts Разраб Бэк (BE-1)

## Контекст

В БД хранятся access/refresh-токены соцсетей и токены сообществ (`social_accounts.access_token_enc`, `refresh_token_enc`, `channels.group_token_enc`). Требования: 152-ФЗ (данные в РФ), невозможность чтения токенов при утечке бэкапа/реплики, отсутствие токенов в логах и кэшах Hibernate. Источник: `02-backend.md` §8.

## Решение

**Envelope encryption:**

1. На каждый workspace (или на ротационный период) генерируется **DEK** (AES-256-GCM, JCA).
2. DEK шифруется **KEK** из KMS: Yandex KMS в проде / Vault Transit; локально — dev-KEK из env (`POSTS_DEV_KEK`, только profile `local`/`test`).
3. В БД: `ciphertext || nonce` как `bytea`; метаданные (key version, algorithm) — рядом или в префиксе blob.
4. **Явный сервис** `TokenCryptoService` в `libs/security`: `seal` / `unseal`. Токены расшифровываются только в памяти на время вызова адаптера.
5. **JPA `AttributeConverter` для токенов не используется** — чтобы plaintext не попадал в L2-кэш, dirty checking logs и accidental `toString` сущностей. Поля сущности хранят только `bytea`; расшифровка — в application-сервисе канала/publisher.
6. Логи: маскирование (`***` / последние 4 символа запрещены для bearer); MDC без секретов.
7. Ротация KEK: версия в метаданных; re-wrap DEK без перешифровки всех токенов; компрометация DEK workspace → re-encrypt токенов этого workspace.

## Альтернативы

- **Transparent Data Encryption диска / pgcrypto одним ключом приложения** — слабее: один ключ на весь кластер, сложнее ротация и audit по workspace.
- **AttributeConverter + `@Convert`** — отвергнуто из‑за риска утечки в кэш/логирование сущностей.
- **Хранение только у провайдера (не кэшировать токены)** — невозможно для server-side публикации по расписанию.

## Последствия

- Зависимость от Vault/KMS в staging/prod (External Secrets); в CI/local — тестовый KEK из `.env.example`.
- Runbook: ротация KEK, процедура при компрометации workspace DEK.
- Безопасность: секреты не коммитятся; gitleaks в CI.
- Реализация — в задачах BE-5/BE-7 вместе с подключением каналов; каркас сервиса — `libs/security`.
