# Тест-кейсы MVP по эпикам

| Файл | Эпик | Префиксы ID |
|---|---|---|
| [E1.md](E1.md) | Аккаунт и вход | TC-AUTH-* |
| [E2.md](E2.md) | Workspace, команда, RBAC | TC-RBAC-*, TC-IDOR-*, TC-WS-* |
| [E3.md](E3.md) | Каналы | TC-CH-* |
| [E4.md](E4.md) | Редактор и планирование | TC-POST-* |
| [E5.md](E5.md) | Публикации и уведомления | TC-PUB-*, TC-NOTIF-* |
| [E10-ad-marking.md](E10-ad-marking.md) | Маркировка / ОРД (паритет) | TC-AD-* |
| [E11-stories.md](E11-stories.md) | Отложенные истории | TC-STORY-* |
| [E17-grabber.md](E17-grabber.md) | Граббер / RSS Ingest | TC-GRAB-* |

Шаблон полей: [`../templates/test-case.md`](../templates/test-case.md).  
План: [`../test-plan-mvp.md`](../test-plan-mvp.md).  
Спеки паритета: [`../../product/features/`](../../product/features/).

**Правило QA-4:** каждый P0 AC stories E1–E5 → ≥ 1 TC; у каждого TC указано **авто** / **ручной** / **кандидат в авто**.

**QA-5:** матрица TC-PUB → автотест - [`../traceability-tc-pub.md`](../traceability-tc-pub.md).

**AN-P / QA-10…12:** кейсы E10/E11/E17 заведены Аналитиком; автоматизация - задачи QA-10…12.
