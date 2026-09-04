package os.companion.character.behaviour;

import os.companion.character.animation.SpriteSheet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OutfitSet {

    public static final String BASE = "base";

    private final Map<String, SpriteSheet> sheets = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();

    private OutfitSet() { }

    public static OutfitSet load(Path characterDir) throws IOException {
        OutfitSet set = new OutfitSet();
        set.add(BASE, SpriteSheet.loadFromDirectory(characterDir));
        Path outfits = characterDir.resolve("outfits");
        if (Files.isDirectory(outfits)) {
            try (var dirs = Files.list(outfits)) {
                dirs.filter(Files::isDirectory)
                    .filter(d -> Files.isRegularFile(d.resolve("frames.json")))
                    .sorted()
                    .forEach(d -> {
                        try {
                            set.add(d.getFileName().toString(), SpriteSheet.loadFromDirectory(d));
                        } catch (IOException e) {

                        }
                    });
            }
        }
        return set;
    }

    private void add(String name, SpriteSheet sheet) {
        sheets.put(name, sheet);
        order.add(name);
    }

    public List<String> names() {
        return List.copyOf(order);
    }

    public boolean has(String name) {
        return sheets.containsKey(name);
    }

    public SpriteSheet get(String name) {
        return sheets.get(name);
    }

    public String next(String current) {
        int i = order.indexOf(current);
        return order.isEmpty() ? BASE : order.get((i + 1) % order.size());
    }
}
