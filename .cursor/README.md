# Cursor cloud agents — окружение Posts

Требования: **JDK 21**, **Node 22**, **pnpm**, **Docker**.

Полезные команды:

```bash
make up
cd backend && ./gradlew build integrationTest
pnpm --dir frontend install
pnpm --dir frontend -r test
make openapi
```

Для Testcontainers на Docker Engine 29:

```bash
echo 'api.version=1.44' > ~/.docker-java.properties
```
