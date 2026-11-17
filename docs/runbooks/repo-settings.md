# Настройки репозитория GitHub (branch protection, метки, доска)

> **DEV-2.** Этот runbook описывает, что должен применить **владелец** репозитория `Viacheslav666/posts`.
> Облачный агент **не** меняет настройки репозитория сам — только готовит конфиги и скрипты в Git.
> После применения сверьтесь с чеклистом в конце.

## 1. Branch protection для `main`

Путь в UI: **Settings → Branches → Add / Edit branch protection rule**  
Pattern: `main`

| Параметр | Значение | Комментарий |
|---|---|---|
| Require a pull request before merging | ✅ | Без прямых push в `main` |
| Require approvals | `1` | Один approve (агент другой роли или владелец) |
| Dismiss stale PR approvals when new commits are pushed | ✅ рекомендуется | |
| Require review from Code Owners | ✅ рекомендуется | См. корневой `CODEOWNERS` |
| Require status checks to pass before merging | ✅ | |
| Require branches to be up to date before merging | ✅ рекомендуется | |
| **Status checks that are required** | см. таблицу ниже | Имена = `jobs.<id>` из workflows |
| Require conversation resolution before merging | ✅ рекомендуется | |
| Require signed commits | по решению владельца | |
| Require linear history | ✅ (вместе со squash) | |
| Allow force pushes | ❌ | Запретить |
| Allow deletions | ❌ | Запретить |
| Do not allow bypassing the above settings | ✅ рекомендуется | Включая admins, если политика команды это допускает |
| Lock branch | ❌ | Не блокируем чтение/PR |

### 1.1. Обязательные status checks

Имена сверены с `.github/workflows/ci.yml` (поле `jobs.<id>` — именно его показывает GitHub в списке checks):

| Required check name | Workflow / job | Когда запускается |
|---|---|---|
| `backend` | `CI` → `jobs.backend` | Изменения `backend/**`, `docs/api/openapi.yaml`, workflow CI; **всегда** на `main` |
| `frontend` | `CI` → `jobs.frontend` | Изменения `frontend/**`, `docs/api/openapi.yaml`, workflow CI; **всегда** на `main` |
| `contract` | `CI` → `jobs.contract` | Изменения API/контракта (`backend/apps/api/**`, `frontend/packages/api-client/**`, OpenAPI); **всегда** на `main` |
| `security` | `CI` → `jobs.security` | Каждый PR и push в `main` (gitleaks) |

**Не** делать обязательными:

| Check | Почему |
|---|---|
| `changes` | Служебный paths-filter job, не сигнализирует качество |
| `Analyze` / `Analyze (java)` / `Analyze (javascript-typescript)` | Job из `.github/workflows/codeql.yml`; тяжёлый, идёт параллельно; включать в required позже по решению владельца |

> **Path filters:** jobs `backend` / `frontend` / `contract` могут быть **skipped**, если paths-filter не сработал. В современных правилах GitHub skipped required checks **не блокируют** merge. Если защита начнёт требовать «зелёный» даже при skip — добавьте no-op success step или уберите path-filter для required jobs. На `main` эти три job всегда запускаются (`github.ref == refs/heads/main`).

### 1.2. Правила слияния PR

Путь: **Settings → General → Pull Requests**

| Параметр | Значение |
|---|---|
| Allow merge commits | ❌ |
| Allow squash merging | ✅ (единственный способ) |
| Squash commit message default | Default / PR title |
| Allow rebase merging | ❌ |
| Always suggest updating pull request branches | ✅ рекомендуется |
| Allow auto-merge | по решению владельца |
| Automatically delete head branches | ✅ рекомендуется |

### 1.3. CLI-эквивалент (для владельца)

```bash
# Требует admin:repo. Запускать от аккаунта владельца.
REPO="Viacheslav666/posts"

gh api -X PUT "repos/${REPO}/branches/main/protection" \
  --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["backend", "frontend", "contract", "security"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_linear_history": true,
  "required_conversation_resolution": true
}
EOF
```

> GitHub API для status checks: в новых репозиториях предпочтительны **checks** (`checks` + `contexts`). Если `contexts` не принимает имена job, в UI выберите checks вручную после первого успешного прогона CI на PR.

## 2. Метки (labels)

Создать скриптом (идемпотентно):

```bash
./infra/scripts/gh-labels.sh
# или явно:
./infra/scripts/gh-labels.sh Viacheslav666/posts
```

### Каталог меток

| Метка | Цвет (hex) | Назначение |
|---|---|---|
| `role:analyst` | `5319E7` | Задача агента «Аналитик» |
| `role:backend` | `1D76DB` | Задача агента «Разраб Бэк» |
| `role:frontend` | `B60205` | Задача агента «Разраб Фронт» |
| `role:devops` | `0E8A16` | Задача агента «Devops» |
| `role:qa` | `D93F0B` | Задача агента «Тестировщик» |
| `phase:0` | `C5DEF5` | Фаза 0 — фундамент |
| `phase:1` | `0075CA` | Фаза 1 — MVP |
| `phase:2` | `0052CC` | Фаза 2 — превосходство |
| `phase:3` | `0B3D91` | Фаза 3 — экосистема |
| `P0` | `B60205` | Критичный приоритет |
| `P1` | `FBCA04` | Высокий приоритет |
| `P2` | `C2E0C6` | Обычный / позже |
| `contract-change` | `E99695` | Несовместимое изменение OpenAPI / TS-клиента |
| `dependencies` | `0366D6` | PR от Renovate / обновления зависимостей |
| `e2e` | `BFDADC` | Запуск e2e-джоба по метке (см. CI-план) |

Правила из `00-overview.md`: каждая задача — Issue с `role:*`, `phase:*`, `P0|P1|P2`; breaking API — дополнительно `contract-change`.

## 3. GitHub Project (доска)

1. **Projects → New project** → Board.
2. Имя: `Posts` (или `Posts — Delivery`).
3. Привязать к репозиторию `Viacheslav666/posts`.
4. Колонки (Status), слева направо:

| Колонка | Смысл |
|---|---|
| **Backlog** | Попало в бэклог, ещё не готово к взятию |
| **Ready** | Готово к работе (зависимости закрыты, критерии ясны) |
| **In progress** | Агент/человек взял в работу, есть ветка/PR draft |
| **Review** | PR открыт, ждёт ревью другой роли / владельца |
| **QA** | Код смержен или на staging, приёмка Тестировщиком |
| **Done** | Принято, DoD выполнен |

Рекомендуемые поля: Priority (`P0`/`P1`/`P2`), Role, Phase, Size (S/M/L), Linked PR.

Автоматизации (опционально): Issue добавлен → Backlog; PR opened → Review; PR merged → QA или Done.

## 4. Renovate

- Конфиг в корне: [`renovate.json`](../../renovate.json).
- Владелец устанавливает GitHub App **Mend Renovate** (или self-hosted) на репозиторий.
- Расписание в конфиге: еженедельно, `Europe/Moscow`, `before 6am on monday`.
- Группы PR: **Spring Boot**, **Testcontainers**, **Next/React**, **GitHub Actions**.
- Метка на PR: `dependencies`.

Проверка валидности конфига (локально, при наличии Node):

```bash
npx --yes renovate-config-validator
# или
npx --yes --package renovate -c 'renovate-config-validator'
```

## 5. Secret scanning и смежное (рекомендуется в том же заходе)

| Настройка | Значение |
|---|---|
| Secret scanning | ✅ |
| Push protection | ✅ |
| Dependabot alerts | ✅ (даже при Renovate — для advisory) |
| CodeQL default setup | workflow `codeql.yml` уже в репо |

## 6. Чеклист применения владельцем

- [ ] Branch protection `main`: PR + 1 approve + required checks `backend`, `frontend`, `contract`, `security`
- [ ] Force-push и deletion на `main` запрещены
- [ ] Squash — единственный merge-метод; linear history
- [ ] `./infra/scripts/gh-labels.sh` выполнен; метки на месте
- [ ] GitHub Project с колонками Backlog → Ready → In progress → Review → QA → Done
- [ ] Renovate App установлен; `renovate.json` подхвачен
- [ ] Secret scanning + push protection включены
