package os.companion.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class LlamaServerManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LlamaServerManager.class);

    private final Path serverExe;
    private final Path modelPath;
    private final String host;
    private final int port;
    private final int contextSize;
    private final int gpuLayers;

    private Process process;
    private Thread logPump;

    public LlamaServerManager(Path serverExe, Path modelPath, String host, int port,
                              int contextSize, int gpuLayers) {
        this.serverExe = serverExe;
        this.modelPath = modelPath;
        this.host = host;
        this.port = port;
        this.contextSize = contextSize;
        this.gpuLayers = gpuLayers;
    }

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public void start(Duration readyTimeout) throws IOException {
        if (isRunning()) {
            return;
        }
        if (!Files.isRegularFile(serverExe)) {
            throw new IOException("llama-server not found: " + serverExe
                    + " (run the fetch step; on Windows it may be blocked by Defender)");
        }
        if (!Files.isRegularFile(modelPath)) {
            throw new IOException("model not found: " + modelPath
                    + " (run: python ai/tools/fetch_model.py)");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(serverExe.toString());
        cmd.add("-m");
        cmd.add(modelPath.toString());
        cmd.add("--host");
        cmd.add(host);
        cmd.add("--port");
        cmd.add(Integer.toString(port));
        cmd.add("-c");
        cmd.add(Integer.toString(contextSize));
        cmd.add("-ngl");
        cmd.add(Integer.toString(gpuLayers));
        cmd.add("--no-webui");

        log.info("starting llama-server: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(serverExe.getParent().toFile());
        process = pb.start();
        startLogPump();

        if (!waitForHealth(readyTimeout)) {
            stop();
            throw new IOException("llama-server did not become healthy within " + readyTimeout);
        }
        log.info("llama-server healthy at {}", baseUrl());
    }

    private void startLogPump() {
        logPump = new Thread(() -> {
            try (var reader = process.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[llama-server] {}", line);
                }
            } catch (IOException ignored) {

            }
        }, "llama-server-log");
        logPump.setDaemon(true);
        logPump.start();
    }

    private boolean waitForHealth(Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                return false;
            }
            try {
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return true;
                }
            } catch (Exception e) {

            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        process = null;
        log.info("llama-server stopped");
    }

    @Override
    public void close() {
        stop();
    }
}
