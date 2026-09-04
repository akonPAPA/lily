package os.companion.character.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FrameAtlas {

    public record MouthRegion(double x, double y, double w, double h) { }

    public record Action(String name, int row, int frames, int fps, boolean loop) {
        public Action {
            if (frames <= 0) throw new IllegalArgumentException("action '" + name + "' needs >=1 frame");
            if (fps <= 0) throw new IllegalArgumentException("action '" + name + "' needs fps > 0");
        }

        public double frameDurationMs() {
            return 1000.0 / fps;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int frameWidth;
    private final int frameHeight;
    private final String imageFile;
    private final String style;
    private final int renderScale;
    private final MouthRegion mouth;
    private final List<MouthRegion> eyes;
    private final MouthRegion hand;
    private final Map<String, Action> actions;

    private FrameAtlas(int frameWidth, int frameHeight, String imageFile, String style, int renderScale,
                       MouthRegion mouth, List<MouthRegion> eyes, MouthRegion hand,
                       Map<String, Action> actions) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.imageFile = imageFile;
        this.style = style;
        this.renderScale = renderScale;
        this.mouth = mouth;
        this.eyes = List.copyOf(eyes);
        this.hand = hand;
        this.actions = Map.copyOf(actions);
    }

    public static FrameAtlas of(int frameWidth, int frameHeight, String imageFile,
                                Map<String, Action> actions) {
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("frame size must be positive");
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("at least one action required");
        }
        return new FrameAtlas(frameWidth, frameHeight, imageFile, "", 0, null, List.of(), null, actions);
    }

    public static FrameAtlas load(Path framesJson) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readAllBytes(framesJson));
        JsonNode meta = required(root, "meta", framesJson);
        int fw = required(meta, "frameWidth", framesJson).asInt();
        int fh = required(meta, "frameHeight", framesJson).asInt();
        int renderScale = meta.path("renderScale").asInt(0);
        String image = required(meta, "image", framesJson).asText();
        String style = meta.path("style").asText("");
        MouthRegion mouth = null;
        JsonNode m = meta.get("mouth");
        if (m != null && m.isObject()) {
            mouth = new MouthRegion(
                    m.path("x").asDouble(0.5), m.path("y").asDouble(0.35),
                    m.path("w").asDouble(0.05), m.path("h").asDouble(0.035));
        }
        List<MouthRegion> eyes = new ArrayList<>();
        JsonNode eyesNode = meta.get("eyes");
        if (eyesNode != null && eyesNode.isArray()) {
            eyesNode.forEach(e -> eyes.add(new MouthRegion(
                    e.path("x").asDouble(0.5), e.path("y").asDouble(0.34),
                    e.path("w").asDouble(0.05), e.path("h").asDouble(0.04))));
        }
        MouthRegion hand = null;
        JsonNode h = meta.get("hand");
        if (h != null && h.isObject()) {
            hand = new MouthRegion(
                    h.path("x").asDouble(0.5), h.path("y").asDouble(0.45), 0, 0);
        }
        if (fw <= 0 || fh <= 0) {
            throw new IOException("frame size must be positive in " + framesJson);
        }

        JsonNode actionsNode = required(root, "actions", framesJson);
        Map<String, Action> actions = new LinkedHashMap<>();
        actionsNode.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode a = entry.getValue();
            actions.put(name, new Action(
                    name,
                    a.path("row").asInt(0),
                    a.path("frames").asInt(1),
                    a.path("fps").asInt(8),
                    a.path("loop").asBoolean(true)));
        });
        if (actions.isEmpty()) {
            throw new IOException("no actions defined in " + framesJson);
        }
        return new FrameAtlas(fw, fh, image, style, renderScale, mouth, eyes, hand, actions);
    }

    private static JsonNode required(JsonNode node, String field, Path src) throws IOException {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            throw new IOException("missing '" + field + "' in " + src);
        }
        return child;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public int renderScale() {
        return renderScale;
    }

    public MouthRegion mouth() {
        return mouth;
    }

    public List<MouthRegion> eyes() {
        return eyes;
    }

    public MouthRegion hand() {
        return hand;
    }

    public String imageFile() {
        return imageFile;
    }

    public String style() {
        return style;
    }

    public boolean isSmooth() {
        return style.equals("portrait-image");
    }

    public boolean hasAction(String name) {
        return actions.containsKey(name);
    }

    public Action action(String name) {
        return actions.get(name);
    }

    public Map<String, Action> actions() {
        return actions;
    }
}
