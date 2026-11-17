# Seed-данные QA

Логический генератор тестовых данных для local/staging (**QA-4**).

| Файл | Назначение |
|---|---|
| `seed_spec.yaml` / `seed_spec.json` | Спецификация пользователей, ролей, объёмов |
| `generate_seed.py` | Генератор JSON (без SQL) |
| `out/` | Локальный вывод (в `.gitignore`) |

```bash
python3 tests/seed/generate_seed.py --profile smoke
python3 tests/seed/generate_seed.py --profile load --dry-run
```

Документ: [`docs/qa/test-data.md`](../../docs/qa/test-data.md).

**TODO(BE-2):** привязка к схеме БД и SQL/JDBC loader — после слияния доменных Flyway-миграций.
