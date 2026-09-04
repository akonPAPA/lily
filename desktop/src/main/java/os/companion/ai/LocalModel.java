package os.companion.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

public final class LocalModel implements AutoCloseable {

    private final LlamaServerManager server;
    private final String baseUrl;
    private final InferenceEngine engine;
    private final ContextBuilder contextBuilder = new ContextBuilder();
    private final ConversationBuffer history;
    private final String persona;

    private final Duration inferenceTimeout = Duration.ofSeconds(120);

    private volatile boolean warm = false;

    private volatile String screenNote = null;

    private LocalModel(LlamaServerManager server, String baseUrl, String model,
                       String persona, int contextTurns) {
        this.server = server;
        this.baseUrl = baseUrl;
        this.engine = new InferenceEngine(baseUrl, model);
        this.history = new ConversationBuffer(contextTurns);
        this.persona = persona;
    }

    public static LocalModel forExternalServer(String baseUrl, String model,
                                               String persona, int contextTurns) {
        return new LocalModel(null, baseUrl, model, persona, contextTurns);
    }

    public static LocalModel forManagedServer(Path serverExe, Path modelPath, String host, int port,
                                              String persona, int contextTurns, int gpuLayers, int ctxSize) {
        LlamaServerManager mgr = new LlamaServerManager(serverExe, modelPath, host, port, ctxSize, gpuLayers);
        return new LocalModel(mgr, mgr.baseUrl(), null, persona, contextTurns);
    }

    public void start(Duration readyTimeout) throws Exception {
        if (server != null) {
            server.start(readyTimeout);
        } else if (!pingExternal(readyTimeout)) {
            throw new java.io.IOException("no SLM server reachable at " + baseUrl
                    + " (start Ollama and `ollama create companion-phi4-mini -f models/Phi4Mini.Modelfile`)");
        }
    }

    public boolean isReady() {
        if (server != null) {
            return server.isRunning();
        }
        return pingExternal(Duration.ofSeconds(2));
    }

    public boolean isWarm() {
        return warm;
    }

    public boolean ensureReady() {
        if (warm) {
            return true;
        }
        synchronized (this) {
            if (warm) {
                return true;
            }
            try {
                if (server != null) {
                    if (!server.isRunning()) {
                        server.start(Duration.ofSeconds(30));
                    }
                    warm = server.isRunning();
                } else {
                    warm = pingExternal(Duration.ofSeconds(3));
                }
            } catch (Exception e) {
                warm = false;
            }
            return warm;
        }
    }

    public CompanionResponse ask(String userText) throws Exception {
        var messages = contextBuilder.build(persona, history.recent(), userText, screenNote);
        CompanionResponse response;
        try {
            response = engine.complete(messages, inferenceTimeout);
        } catch (Exception e) {
            warm = false;
            throw e;
        }
        history.addUser(userText);
        history.addAssistant(response.speech());
        return response;
    }

    public CompanionResponse spontaneous(String cue) throws Exception {
        var messages = contextBuilder.build(persona, history.recent(), cue, screenNote);
        CompanionResponse response;
        try {
            response = engine.complete(messages, inferenceTimeout);
        } catch (Exception e) {
            warm = false;
            throw e;
        }
        history.addAssistant(response.speech());
        return response;
    }

    public void resetConversation() {
        history.clear();
    }

    public void setScreenContext(String note) {
        this.screenNote = (note == null || note.isBlank()) ? null : note;
    }

    private boolean pingExternal(Duration timeout) {

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            try {
                HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {

            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() < deadline);
        return false;
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop();
        }
    }
}
