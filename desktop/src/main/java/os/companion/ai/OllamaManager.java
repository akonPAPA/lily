package os.companion.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public final class OllamaManager {

    private static final Logger log = LoggerFactory.getLogger(OllamaManager.class);

    private final String exe;
    private final String baseUrl;
    private final String model;
    private final Path modelfile;
    private final Path modelsDir;
    private final HttpClient http = HttpClient.newHttpClient();

    private volatile boolean startedServe = false;
    private volatile boolean modelReady = false;

    public OllamaManager(String baseUrl, String model, Path modelfile) {
        this.exe = findExe();
        this.baseUrl = baseUrl;
        this.model = model;
        this.modelfile = modelfile;
        this.modelsDir = modelfile != null ? modelfile.getParent() : null;
    }

    public boolean ensureUp(Duration budget) {
        if (ping()) {
            ensureModel();
            return true;
        }
        if (!startServe()) {
            return false;
        }
        boolean up = waitUntilUp(budget);
        if (up) {
            ensureModel();
        }
        return up;
    }

    private static String findExe() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            Path p = Paths.get(local, "Programs", "Ollama", "ollama.exe");
            if (Files.isRegularFile(p)) {
                return p.toString();
            }
        }
        return "ollama";
    }

    private boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean startServe() {
        if (startedServe) {
            return true;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(exe, "serve");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            startedServe = true;
            log.info("started `ollama serve` ({})", exe);
            return true;
        } catch (Exception e) {
            log.warn("could not start ollama ({}): {}", exe, e.toString());
            return false;
        }
    }

    private boolean waitUntilUp(Duration budget) {
        long deadline = System.nanoTime() + budget.toNanos();
        while (System.nanoTime() < deadline) {
            if (ping()) {
                return true;
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

    private synchronized void ensureModel() {
        if (modelReady || modelfile == null || !Files.isRegularFile(modelfile)) {
            return;
        }
        try {
            String list = runCapture(20, exe, "list");
            if (list != null && list.contains(baseModelName())) {
                modelReady = true;
                return;
            }
            log.info("importing SLM model '{}' into Ollama (first run, ~a minute)…", model);
            ProcessBuilder pb = new ProcessBuilder(exe, "create", model, "-f", modelfile.toString());
            if (modelsDir != null) {
                pb.directory(modelsDir.toFile());
            }
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            if (p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                modelReady = true;
                log.info("SLM model '{}' ready", model);
            } else {
                log.warn("ollama create for '{}' did not finish cleanly", model);
            }
        } catch (Exception e) {
            log.warn("ensureModel failed: {}", e.toString());
        }
    }

    private String baseModelName() {
        int c = model.indexOf(':');
        return c > 0 ? model.substring(0, c) : model;
    }

    private String runCapture(int timeoutSec, String... args) {
        try {
            Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
