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
import java.util.List;

public final class InferenceEngine {

    private static final Logger log = LoggerFactory.getLogger(InferenceEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final HttpClient http;
    private final ModelResponseParser parser = new ModelResponseParser();

    private double temperature = 0.7;
    private int maxTokens = 200;

    public InferenceEngine(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public InferenceEngine temperature(double t) {
        this.temperature = t;
        return this;
    }

    public InferenceEngine maxTokens(int n) {
        this.maxTokens = n;
        return this;
    }

    public CompanionResponse complete(List<ChatMessage> messages, Duration timeout) throws Exception {
        String body = buildRequest(messages);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new java.io.IOException("llama-server HTTP " + resp.statusCode() + ": " + resp.body());
        }
        String content = extractContent(resp.body());
        log.debug("model content: {}", content);
        return parser.parse(content);
    }

    private String buildRequest(List<ChatMessage> messages) {
        ObjectNode root = MAPPER.createObjectNode();
        if (model != null && !model.isBlank()) {
            root.put("model", model);
        }
        ArrayNode arr = root.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode o = arr.addObject();
            o.put("role", m.role());
            o.put("content", m.content());
        }
        root.put("temperature", temperature);
        root.put("max_tokens", maxTokens);
        root.put("cache_prompt", true);
        root.set("response_format", responseSchema());
        return root.toString();
    }

    private static ObjectNode responseSchema() {
        ObjectNode rf = MAPPER.createObjectNode();
        rf.put("type", "json_schema");
        ObjectNode js = rf.putObject("json_schema");
        js.put("name", "companion_response");
        js.put("strict", true);

        ObjectNode schema = js.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        props.putObject("speech").put("type", "string");
        putEnum(props.putObject("emotion"), Emotion.values());
        putEnum(props.putObject("gesture"), Gesture.values());
        ObjectNode energy = props.putObject("energy");
        energy.put("type", "number");
        energy.put("minimum", 0);
        energy.put("maximum", 1);
        schema.putArray("required").add("speech").add("emotion").add("gesture").add("energy");
        return rf;
    }

    private static void putEnum(ObjectNode node, Enum<?>[] values) {
        node.put("type", "string");
        ArrayNode arr = node.putArray("enum");
        for (Enum<?> v : values) {
            arr.add(v.name());
        }
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText("");
        }
        return "";
    }
}
