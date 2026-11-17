package ru.posts.security.rbac;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Loads {@link RbacMatrix} from classpath {@code rbac/roles-permissions.yaml}. */
public final class RbacMatrixLoader {

    public static final String DEFAULT_CLASSPATH = "rbac/roles-permissions.yaml";

    private RbacMatrixLoader() {}

    public static RbacMatrix loadDefault() {
        return load(DEFAULT_CLASSPATH);
    }

    @SuppressWarnings("unchecked")
    public static RbacMatrix load(String classpathLocation) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = RbacMatrixLoader.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("RBAC matrix not found on classpath: " + classpathLocation);
            }
            Map<String, Object> root = new Yaml().load(in);
            String version = String.valueOf(root.getOrDefault("version", "unknown"));
            List<Map<String, Object>> matrix = (List<Map<String, Object>>) root.get("matrix");
            if (matrix == null) {
                throw new IllegalStateException("RBAC matrix missing 'matrix' key");
            }
            List<RbacMatrixEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : matrix) {
                entries.add(parseEntry(row));
            }
            return new RbacMatrix(version, entries);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load RBAC matrix", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static RbacMatrixEntry parseEntry(Map<String, Object> row) {
        String action = String.valueOf(row.get("action"));
        Map<RbacRole, RbacEffect> effects = new EnumMap<>(RbacRole.class);
        for (RbacRole role : RbacRole.values()) {
            Object raw = row.get(role.name());
            if (raw != null) {
                effects.put(role, RbacEffect.from(String.valueOf(raw)));
            }
        }
        RbacScope scope = RbacScope.from(row.get("scope") == null ? null : String.valueOf(row.get("scope")));
        Map<RbacRole, List<String>> conditions = new EnumMap<>(RbacRole.class);
        Object conditionsRaw = row.get("conditions");
        if (conditionsRaw instanceof Map<?, ?> conditionsMap) {
            for (Map.Entry<?, ?> e : conditionsMap.entrySet()) {
                RbacRole role = RbacRole.from(String.valueOf(e.getKey()));
                List<String> values = new ArrayList<>();
                if (e.getValue() instanceof List<?> list) {
                    for (Object item : list) {
                        values.add(String.valueOf(item));
                    }
                }
                conditions.put(role, List.copyOf(values));
            }
        }
        return new RbacMatrixEntry(action, Map.copyOf(effects), scope, Map.copyOf(conditions));
    }
}
