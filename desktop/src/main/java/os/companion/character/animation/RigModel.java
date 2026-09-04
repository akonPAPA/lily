package os.companion.character.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.image.Image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RigModel {

    public record Pivot(double x, double y) { }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int frameWidth;
    private final int frameHeight;
    private final int renderScale;
    private final FrameAtlas.MouthRegion mouth;
    private final List<String> zorder;
    private final Map<String, Image> parts;
    private final Map<String, Pivot> pivots;

    private RigModel(int fw, int fh, int scale, FrameAtlas.MouthRegion mouth,
                     List<String> zorder, Map<String, Image> parts, Map<String, Pivot> pivots) {
        this.frameWidth = fw;
        this.frameHeight = fh;
        this.renderScale = scale;
        this.mouth = mouth;
        this.zorder = List.copyOf(zorder);
        this.parts = Map.copyOf(parts);
        this.pivots = Map.copyOf(pivots);
    }

    public static RigModel loadIfPresent(Path dir) throws IOException {
        Path rigJson = dir.resolve("rig.json");
        if (!Files.isRegularFile(rigJson)) {
            return null;
        }
        JsonNode root = MAPPER.readTree(Files.readAllBytes(rigJson));
        JsonNode meta = root.path("meta");
        int fw = meta.path("frameWidth").asInt();
        int fh = meta.path("frameHeight").asInt();
        int scale = meta.path("renderScale").asInt(1);

        FrameAtlas.MouthRegion mouth = null;
        JsonNode m = meta.get("mouth");
        if (m != null && m.isObject()) {
            mouth = new FrameAtlas.MouthRegion(
                    m.path("x").asDouble(0.5), m.path("y").asDouble(0.38),
                    m.path("w").asDouble(0.04), m.path("h").asDouble(0.04));
        }

        List<String> zorder = new ArrayList<>();
        root.path("zorder").forEach(n -> zorder.add(n.asText()));
        if (zorder.isEmpty()) {
            zorder.addAll(List.of("body", "armL", "armR", "head"));
        }

        Map<String, Image> parts = new LinkedHashMap<>();
        for (String name : zorder) {
            Path png = dir.resolve(name + ".png");
            if (!Files.isRegularFile(png)) {
                throw new IOException("rig layer missing: " + png);
            }
            try (InputStream in = Files.newInputStream(png)) {
                Image img = new Image(in, 0, 0, true, false);
                if (img.isError()) {
                    throw new IOException("failed to decode " + png, img.getException());
                }
                parts.put(name, img);
            }
        }

        Map<String, Pivot> pivots = new LinkedHashMap<>();
        JsonNode pv = root.path("pivots");
        pv.fields().forEachRemaining(e -> pivots.put(e.getKey(),
                new Pivot(e.getValue().path("x").asDouble(0.5), e.getValue().path("y").asDouble(0.5))));

        return new RigModel(fw, fh, scale, mouth, zorder, parts, pivots);
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

    public FrameAtlas.MouthRegion mouth() {
        return mouth;
    }

    public List<String> zorder() {
        return zorder;
    }

    public Image part(String name) {
        return parts.get(name);
    }

    public Pivot pivotFor(String part) {
        return switch (part) {
            case "head" -> pivots.getOrDefault("neck", new Pivot(0.5, 0.35));
            case "armL" -> pivots.getOrDefault("shoulderL", new Pivot(0.3, 0.4));
            case "armR" -> pivots.getOrDefault("shoulderR", new Pivot(0.7, 0.4));
            default -> new Pivot(0.5, 1.0);
        };
    }
}
