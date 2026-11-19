package ru.posts.testsupport.emulator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Starts the QA-3 social-emulator boot jar on an ephemeral port for pipeline integration tests.
 */
public final class SocialEmulatorProcess implements AutoCloseable {

    private final Process process;
    private final int port;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private SocialEmulatorProcess(Process process, int port) {
        this.process = process;
        this.port = port;
    }

    public static SocialEmulatorProcess start() throws Exception {
        Path jar = resolveBootJar();
        int port = freePort();
        String publicBase = "http://127.0.0.1:" + port;
        Process process = new ProcessBuilder(
                        ProcessHandle.current().info().command().orElse("java"),
                        "-jar",
                        jar.toAbsolutePath().toString(),
                        "--server.port=" + port,
                        // VK/OK stories upload_url must point back at this ephemeral listener.
                        "--posts.emulator.public-base-url=" + publicBase)
                .redirectErrorStream(true)
                .start();
        SocialEmulatorProcess emulator = new SocialEmulatorProcess(process, port);
        try {
            emulator.waitUntilReady(Duration.ofSeconds(60));
            return emulator;
        } catch (Exception ex) {
            emulator.close();
            throw ex;
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    public int port() {
        return port;
    }

    public void reset() throws IOException, InterruptedException {
        post("/_control/reset?scenario=true", "");
    }

    public void putScenario(String json) throws IOException, InterruptedException {
        put("/_control/scenario", json);
    }

    public void put(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "PUT " + path + " failed: " + response.statusCode() + " " + response.body());
        }
    }

    public void post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "POST " + path + " failed: " + response.statusCode() + " " + response.body());
        }
    }

    public String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "GET " + path + " failed: " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    private void waitUntilReady(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                String output =
                        new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                throw new IllegalStateException("social-emulator exited early:\n" + output);
            }
            try {
                get("/_control/health");
                return;
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("social-emulator did not become ready", last);
    }

    private static Path resolveBootJar() throws Exception {
        Path repoRoot = Path.of(System.getProperty("posts.repo.root", "."))
                .toAbsolutePath()
                .normalize();
        // Prefer already-built jar; otherwise build it.
        Path jarDir = repoRoot.resolve("tests/social-emulator/build/libs");
        Path jar = findJar(jarDir);
        if (jar == null) {
            Process build = new ProcessBuilder("./gradlew", "bootJar", "-q")
                    .directory(repoRoot.resolve("tests/social-emulator").toFile())
                    .inheritIO()
                    .start();
            if (!build.waitFor(5, TimeUnit.MINUTES) || build.exitValue() != 0) {
                throw new IllegalStateException("Failed to build social-emulator bootJar");
            }
            jar = findJar(jarDir);
        }
        if (jar == null) {
            throw new IllegalStateException("social-emulator bootJar not found in " + jarDir);
        }
        return jar;
    }

    private static Path findJar(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jar")
                                && !name.endsWith("-plain.jar")
                                && !name.contains("javadoc")
                                && !name.contains("sources");
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
