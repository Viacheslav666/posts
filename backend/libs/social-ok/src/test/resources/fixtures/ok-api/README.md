# OK API contract fixtures (QA-13 / BE-23)

Shapes aligned with official OK API (`apiok.ru` / `api.ok.ru/fb.do`).

| File | Method | Doc note |
|---|---|---|
| `mediatopic-post-success.json` | `mediatopic.post` | `{ "id": "<topicId>", "success": true }` |
| `stream-get-topics.json` | `stream.get` | media topics + entities map used for reconciliation |
| `photos-v2-getUploadUrl.json` | `photosV2.getUploadUrl` | upload URL for media |

Used by `OkSocialAdapterQa13ContractTest` — adapters must parse these shapes without `/_control`.
