# VK API 5.199 contract fixtures (QA-13 / BE-23)

Shapes recorded from official VK API docs (`dev.vk.com/method/*`, API version **5.199**).

| File | Method | Doc note |
|---|---|---|
| `wall-post-success.json` | `wall.post` | `{ "response": { "post_id": <int> } }` |
| `wall-get-items.json` | `wall.get` | `{ "response": { "count", "items":[{id,owner_id,date,text,…}] } }` |
| `photos-save-wall-photo.json` | `photos.saveWallPhoto` | `{ "response": [ { id, album_id, owner_id, … } ] }` |
| `photos-get-wall-upload-server.json` | `photos.getWallUploadServer` | `{ "response": { "upload_url", "album_id", "user_id" } }` |
| `wall-pin-success.json` | `wall.pin` / `wall.unpin` | `{ "response": 1 }` |
| `polls-create-success.json` | `polls.create` | `{ "response": { "id", "owner_id", "question" } }` |

Used by `VkSocialAdapterQa13ContractTest` — adapters must parse these shapes without `/_control`.
