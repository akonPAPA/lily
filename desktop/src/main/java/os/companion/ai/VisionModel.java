package os.companion.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class VisionModel {

    private static final Logger log = LoggerFactory.getLogger(VisionModel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GLANCE_PROMPT =
            "You are glancing at the user's screen. In ONE short sentence (max 15 words), "
            + "say what the user appears to be doing. No preamble, no markdown.";

    private final String baseUrl;
    private final String model;
    private final HttpClient http;
    private volatile boolean warm = false;

    public VisionModel(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isWarm() {
        return warm;
    }

    public boolean ensureReady() {
        if (warm) {
            return true;
        }
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            warm = http.send(r, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            warm = false;
        }
        return warm;
    }

    public String describe(String imageDataUrl, Duration timeout) {
        if (imageDataUrl == null) {
            return null;
        }
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", model);
            ArrayNode messages = root.putArray("messages");
            ObjectNode um = messages.addObject();
            um.put("role", "user");
            ArrayNode content = um.putArray("content");
            content.addObject().put("type", "text").put("text", GLANCE_PROMPT);
            ObjectNode img = content.addObject();
            img.put("type", "image_url");
            img.putObject("image_url").put("url", imageDataUrl);
            root.put("temperature", 0.2);
            root.put("max_tokens", 64);

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                warm = false;
                log.debug("vision HTTP {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode choices = MAPPER.readTree(resp.body()).path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String s = choices.get(0).path("message").path("content").asText("").strip();
                return s.isBlank() ? null : s;
            }
            return null;
        } catch (Exception e) {
            warm = false;
            log.debug("vision describe failed: {}", e.toString());
            return null;
        }
    }
}
