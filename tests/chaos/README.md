# tests/chaos

Хаос- и DR-сценарии публикационного конвейера (см. `docs/specs/05-qa.md` §7, задача QA-5 / QA-8).

| Артефакт | Назначение |
|---|---|
| [`tc-pub-chaos.md`](tc-pub-chaos.md) | Матрица TC-PUB-07…10: IT-прототип vs staging |
| [`scripts/kill-publisher.sh`](scripts/kill-publisher.sh) | Убийство `publisher-tg` mid-flight на compose `full` |

Интеграционные аналоги без K8s: `backend/apps/publisher/src/integrationTest/.../pipeline/`.
Кейсы TC-PUB-09/10 помечены «ожидает BE-10».
