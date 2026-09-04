package os.companion.character.animation;

import javafx.scene.image.Image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpriteSheet {

    private final Image image;
    private final FrameAtlas atlas;

    private SpriteSheet(Image image, FrameAtlas atlas) {
        this.image = image;
        this.atlas = atlas;
    }

    public static SpriteSheet loadFromDirectory(Path characterDir) throws IOException {
        Path framesJson = characterDir.resolve("frames.json");
        FrameAtlas atlas = FrameAtlas.load(framesJson);
        Path imagePath = characterDir.resolve(atlas.imageFile());
        if (!Files.isRegularFile(imagePath)) {
            throw new IOException("sprite image not found: " + imagePath);
        }

        try (InputStream in = Files.newInputStream(imagePath)) {
            Image img = new Image(in, 0, 0, true, false);
            if (img.isError()) {
                throw new IOException("failed to decode " + imagePath, img.getException());
            }
            return new SpriteSheet(img, atlas);
        }
    }

    public Image image() {
        return image;
    }

    public FrameAtlas atlas() {
        return atlas;
    }

    public double sourceX(int frameIndex) {
        return (double) frameIndex * atlas.frameWidth();
    }

    public double sourceY(FrameAtlas.Action action) {
        return (double) action.row() * atlas.frameHeight();
    }
}
