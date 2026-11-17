package ru.posts.api.rbac;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Loads docs/qa/rbac-endpoint-map.yaml for QA-6 HTTP matrix tests. */
final class RbacEndpointMapLoader {

    record Endpoint(
            String operationId,
            String method,
            String path,
            String rbac,
            String action,
            Map<String, String> pathParams,
            Map<String, String> query,
            boolean destructive,
            boolean implementedHint) {}

    private RbacEndpointMapLoader() {}

    static List<Endpoint> loadMatrixEndpoints() {
        return loadAll().stream().filter(e -> "matrix".equals(e.rbac())).toList();
    }

    static List<Endpoint> loadAll() {
        Path file = findRepoRoot().resolve("docs/qa/rbac-endpoint-map.yaml");
        try {
            Map<String, Object> root = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = (List<Map<String, Object>>) root.get("endpoints");
            List<Endpoint> out = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                out.add(parse(row));
            }
            return List.copyOf(out);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + file, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Endpoint parse(Map<String, Object> row) {
        Map<String, String> pathParams = castStringMap(row.get("pathParams"));
        Map<String, String> query = castStringMap(row.get("query"));
        return new Endpoint(
                String.valueOf(row.get("operationId")),
                String.valueOf(row.get("method")),
                String.valueOf(row.get("path")),
                String.valueOf(row.get("rbac")),
                row.get("action") == null ? null : String.valueOf(row.get("action")),
                pathParams,
                query,
                Boolean.TRUE.equals(row.get("destructive")),
                Boolean.TRUE.equals(row.get("implementedHint")));
    }

    private static Map<String, String> castStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return Map.copyOf(out);
    }

    static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10; i++) {
            if (Files.isRegularFile(dir.resolve("docs/qa/rbac-endpoint-map.yaml"))
                    && Files.isDirectory(dir.resolve("backend"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        Path fromModule = Path.of("../../..").toAbsolutePath().normalize();
        if (Files.isRegularFile(fromModule.resolve("docs/qa/rbac-endpoint-map.yaml"))) {
            return fromModule;
        }
        throw new IllegalStateException("Cannot locate repo root for rbac-endpoint-map.yaml");
    }
}
