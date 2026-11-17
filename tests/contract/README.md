# tests/contract

Контрактные прогоны **Schemathesis** по `docs/api/openapi.yaml` против живого `posts-api` (задача **QA-2**).

## Скрипты

| Скрипт | Назначение |
|---|---|
| `run-smoke.sh` | Только `GET /api/v1/ping` (быстрый гейт PR/CI) |
| `run-nightly.sh` | Шире по OpenAPI (ограниченные examples), JUnit-отчёт |

## Переменные

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `API_BASE_URL` | `http://127.0.0.1:8080` | база поднятого API |
| `OPENAPI_PATH` | `docs/api/openapi.yaml` | путь к контракту (от корня репо) |
| `REPORT_DIR` | `tests/contract/reports` | куда писать JUnit/HTML |

## Локально

```bash
# API без внешней БД (stubs + ping):
cd backend && ./gradlew :apps:api:bootJar
java -jar apps/api/build/libs/api-*.jar --spring.profiles.active=openapi &

pip install 'schemathesis>=3.39,<5'
./tests/contract/run-smoke.sh
```

Либо против compose: `make up-full`, затем те же скрипты.

## Nightly

`.github/workflows/nightly.yml` поднимает API через docker-compose profile `full`, запускает `run-nightly.sh`, грузит артефакты из `tests/contract/reports/`.
