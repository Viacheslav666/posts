# Runbook: Антиспам (BE-35)

## Симптомы

- Спам не удаляется / удаляется с задержкой
- Алерт / уведомление `antispam.spam_attack`
- Метрика `antispam_verdict_latency` p99 &gt; 5 мс
- Рост `antispam_actions_total{result="failed"}` / `tg_delete_window_expired`

## Быстрая диагностика

1. Feature flag `automation.antispam` включён для workspace (`feature_flags` / override).
2. Правило `antispam_rules.status=active`, подписка `channel_event_subscriptions.consumers` содержит `antispam`.
3. Очередь `social_remote_actions` (source=`antispam`): `pending` / `failed_*` / `unknown_outcome`.
4. Лог `antispam_log` по `channel_id` — `state`, `reasons`, `remote_action_ids`.
5. Для VK: есть user token у связанного `social_accounts` (иначе activate → 422 `antispam_vk_permissions_missing`).

## Спам-атака

При &gt; 60 действий/мин на канал пишется outbox `antispam.spam_attack`. Действия продолжают идти через лимитер BE-33 (VK `execute`). Проверить Redis rate-limit и длину `actions.moderation.vk`.

## Восстановление

Только VK: `POST /api/v1/antispam/log/{id}/restore`. TG/Max → 422 `antispam_restore_unsupported`. Ошибка срока VK → `restore_failed`.

## Партиции

`AntispamPartitionJob` (scheduler): retention 90 дней, monthly partitions `antispam_log_YYYY_MM`.
