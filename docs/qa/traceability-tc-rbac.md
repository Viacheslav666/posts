# Матрица трассировки TC-RBAC / TC-IDOR → автотест (QA-6)

Обновлено: 2026-11-17. Источники: `docs/product/roles-permissions.yaml`, `docs/qa/rbac-endpoint-map.yaml`, `docs/api/openapi.yaml`.

Инварианты: deny на своём workspace → **403**; чужой workspace/ресурс → **404** (не 403); stub → **skip** до ответа ≠ 501.

| TC | Автотест | Набор | Статус |
|---|---|---|---|
| TC-RBAC-06 (полная матрица роль×action) | `RbacMatrixFullCoverageTest` | CI (`:libs:security:test`) | авто |
| TC-RBAC-02 (author schedule conditions) | `RbacPermissionEvaluatorTest#tcRbac02_authorCannotSchedule` | CI | авто |
| Scope `assigned_projects` / `own_posts_only` | `RbacPermissionEvaluatorTest` | CI | авто |
| TC-RBAC-01 editor grants | `WorkspaceRbacIntegrationTest#tcRbac01_editorAssignedProjects` | CI IT | авто (BE-6, сценарий) |
| TC-RBAC-03/04 invite+audit | `WorkspaceRbacIntegrationTest#tcRbac03_04_viewerAndAudit` | CI IT | авто (BE-6, сценарий) |
| TC-RBAC-05 owner-only delete | `WorkspaceRbacIntegrationTest#tcRbac05_ownerOnlyDelete` | CI IT | авто (BE-6, сценарий) |
| TC-RBAC роль×эндпоинт (OpenAPI) | `RbacEndpointMatrixIT#roleTimesEndpoint` | CI IT | авто (генерация из карты) |
| TC-IDOR-01 workspace | `WorkspaceRbacIntegrationTest#tcIdor01_foreignWorkspace` | CI IT | авто (BE-6, сценарий) |
| TC-IDOR матрица (пост/канал/медиа/проект/…) | `RbacIdorMatrixIT#foreignResourceIsNotFound` | CI IT | авто; skip при 501 |
| Рассинхрон OpenAPI ↔ карта | `RbacOpenApiEndpointMapSyncTest` | CI (`:apps:api:test`) | авто |
| Рассинхрон product YAML ↔ classpath | `RbacMatrixDocsSyncTest` | CI (`:libs:security:test`) | авто |
| Отчёт покрытие роль×эндпоинт | `docs/qa/rbac-coverage-report.md` | артефакт PR | `generate_rbac_coverage_report.py` |

## Генерация

```bash
python3 docs/qa/generate_rbac_endpoint_map.py
python3 docs/qa/generate_rbac_coverage_report.py
cd backend && ./gradlew :libs:security:test :apps:api:test \
  :apps:api:integrationTest --tests 'ru.posts.api.rbac.*' --tests '*WorkspaceRbac*'
```

## Сведение с BE-6

- Булева матрица YAML - только `RbacMatrixFullCoverageTest` (не дублировать CSV в evaluator-тесте).
- HTTP-сценарии приглашений/аудита/удаления - только `WorkspaceRbacIntegrationTest`.
- Полный перебор роль×operationId - только `RbacEndpointMatrixIT`.
