package os.companion.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ModelResponseParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CompanionResponse parse(String rawModelText) {
        if (rawModelText == null || rawModelText.isBlank()) {
            return CompanionResponse.fallback("");
        }
        String json = extractJsonObject(rawModelText);
        if (json == null) {
            log.debug("no JSON object in model output; using raw text as speech");
            return CompanionResponse.fallback(rawModelText.trim());
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            String speech = node.path("speech").asText("").trim();
            Emotion emotion = Emotion.fromOrNeutral(node.path("emotion").asText(null));
            Gesture gesture = Gesture.fromOrNone(node.path("gesture").asText(null));
            double energy = node.path("energy").asDouble(0.5);
            if (speech.isEmpty()) {

                speech = stripJson(rawModelText, json);
            }
            return new CompanionResponse(speech, emotion, gesture, energy);
        } catch (Exception e) {
            log.debug("failed to parse extracted JSON: {}", e.toString());

            String salvaged = salvageSpeech(json);
            if (salvaged != null && !salvaged.isBlank()) {
                return CompanionResponse.fallback(salvaged);
            }
            return CompanionResponse.fallback(rawModelText.trim());
        }
    }

    private static final java.util.regex.Pattern SPEECH =
            java.util.regex.Pattern.compile("\"speech\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    static String salvageSpeech(String json) {
        if (json == null) {
            return null;
        }
        var m = SPEECH.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
        }
        return null;
    }

    static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String stripJson(String raw, String json) {
        String s = raw.replace(json, "").replace("```json", "").replace("```", "").trim();
        return s.isEmpty() ? "" : s;
    }
}
