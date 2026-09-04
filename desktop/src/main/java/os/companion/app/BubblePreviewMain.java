package os.companion.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import os.companion.character.rendering.CharacterOverlay;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public final class BubblePreviewMain extends Application {

    @Override
    public void start(javafx.stage.Stage stage) {
        double w = 340;
        double h = 180;
        Canvas canvas = new Canvas(w, h);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#f3dbe6"));
        gc.fillRect(0, 0, w, h);
        CharacterOverlay.paintBubble(gc, w, List.of("Приветик! Я Lily ♥", "чем займёмся сегодня?"));

        WritableImage img = new WritableImage((int) w, (int) h);
        canvas.snapshot(null, img);
        try {
            BufferedImage bimg = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = img.getPixelReader();
            for (int y = 0; y < (int) h; y++) {
                for (int x = 0; x < (int) w; x++) {
                    bimg.setRGB(x, y, pr.getArgb(x, y));
                }
            }
            File out = new File("build/bubble_preview.png");
            out.getParentFile().mkdirs();
            ImageIO.write(bimg, "png", out);
            System.out.println("wrote " + out.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("preview failed: " + e);
        }
        Platform.exit();
    }

    public static final class Launcher {
        public static void main(String[] args) {
            Application.launch(BubblePreviewMain.class, args);
        }
    }
}
