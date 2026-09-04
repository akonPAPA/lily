package os.companion.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import os.companion.character.animation.SpriteSheet;
import os.companion.character.rendering.CharacterOverlay;
import os.companion.config.AppConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class AnimSnapshotMain extends Application {

    @Override
    public void start(javafx.stage.Stage stage) throws Exception {
        AppConfig config = AppConfig.discover();
        SpriteSheet sheet = SpriteSheet.loadFromDirectory(config.characterDir("lily"));
        CharacterOverlay overlay = new CharacterOverlay(sheet, "idle");
        overlay.update(0, 0, 4000, 0, 4000);

        overlay.render();
        save(overlay.canvas(), "build/blink_open.png");

        overlay.debugForceBlink();
        overlay.render();
        save(overlay.canvas(), "build/blink_closed.png");

        Platform.exit();
    }

    private static void save(Canvas canvas, String path) throws Exception {
        int w = (int) canvas.getWidth();
        int h = (int) canvas.getHeight();
        WritableImage img = new WritableImage(w, h);
        canvas.snapshot(null, img);
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                b.setRGB(x, y, pr.getArgb(x, y));
            }
        }
        File out = new File(path);
        out.getParentFile().mkdirs();
        ImageIO.write(b, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    public static final class Launcher {
        public static void main(String[] args) {
            Application.launch(AnimSnapshotMain.class, args);
        }
    }
}
