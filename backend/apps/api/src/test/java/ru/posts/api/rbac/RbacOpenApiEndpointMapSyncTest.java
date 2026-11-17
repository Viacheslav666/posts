package ru.posts.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * QA-6: каждый operationId из OpenAPI обязан иметь запись в docs/qa/rbac-endpoint-map.yaml.
 * Рассинхрон → падение теста (новый эндпоинт без строки в карте).
 */
class RbacOpenApiEndpointMapSyncTest {

    private static final Pattern OPERATION_ID = Pattern.compile("^\\s+operationId:\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    @Test
    @DisplayName("OpenAPI operationIds ⊆ rbac-endpoint-map.yaml")
    void everyOpenApiOperationHasMapEntry() throws IOException {
        Path root = RepoRoots.find();
        Path openApi = root.resolve("docs/api/openapi.yaml");
        Path mapFile = root.resolve("docs/qa/rbac-endpoint-map.yaml");
        assertThat(openApi).exists();
        assertThat(mapFile).exists();

        Set<String> fromOpenApi = parseOperationIds(Files.readString(openApi, StandardCharsets.UTF_8));
        Map<String, Object> mapRoot = new Yaml().load(Files.readString(mapFile, StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) mapRoot.get("endpoints");
        assertThat(endpoints).isNotEmpty();

        Set<String> fromMap = endpoints.stream()
                .map(e -> String.valueOf(e.get("operationId")))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> missingInMap = new LinkedHashSet<>(fromOpenApi);
        missingInMap.removeAll(fromMap);
        Set<String> extraInMap = new LinkedHashSet<>(fromMap);
        extraInMap.removeAll(fromOpenApi);

        assertThat(missingInMap)
                .as("Add operationIds to docs/qa/rbac-endpoint-map.yaml (run generate_rbac_endpoint_map.py)")
                .isEmpty();
        assertThat(extraInMap).as("Remove stale operationIds from endpoint map").isEmpty();
        assertThat(fromMap).hasSameSizeAs(fromOpenApi);
    }

    @Test
    @DisplayName("matrix endpoints reference known RBAC actions")
    void matrixActionsExistInRolesPermissions() throws IOException {
        Path root = RepoRoots.find();
        Map<String, Object> roles =
                new Yaml().load(Files.readString(root.resolve("docs/product/roles-permissions.yaml")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) roles.get("actions");
        Set<String> known = actions.stream()
                .map(a -> String.valueOf(a.get("id")))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> mapRoot = new Yaml().load(Files.readString(root.resolve("docs/qa/rbac-endpoint-map.yaml")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) mapRoot.get("endpoints");
        for (Map<String, Object> ep : endpoints) {
            if (!"matrix".equals(String.valueOf(ep.get("rbac")))) {
                continue;
            }
            String action = String.valueOf(ep.get("action"));
            assertThat(known)
                    .as("endpoint %s action %s", ep.get("operationId"), action)
                    .contains(action);
        }
    }

    private static Set<String> parseOperationIds(String yaml) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = OPERATION_ID.matcher(yaml);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        assertThat(ids).isNotEmpty();
        return ids;
    }

    /** Shared repo-root locator for QA-6 sync tests. */
    static final class RepoRoots {
        private RepoRoots() {}

        static Path find() {
            Path dir = Path.of("").toAbsolutePath();
            for (int i = 0; i < 10; i++) {
                if (Files.isRegularFile(dir.resolve("docs/api/openapi.yaml"))
                        && Files.isDirectory(dir.resolve("backend"))) {
                    return dir;
                }
                dir = dir.getParent();
                if (dir == null) {
                    break;
                }
            }
            Path fromModule = Path.of("../../..").toAbsolutePath().normalize();
            if (Files.isRegularFile(fromModule.resolve("docs/api/openapi.yaml"))) {
                return fromModule;
            }
            throw new IllegalStateException("Cannot locate repo root");
        }
    }
}
