package os.companion.nativeplatform;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

public final class ScreenGrabber {

    private final PlatformBridge platform;
    private Robot robot;

    public ScreenGrabber(PlatformBridge platform) {
        this.platform = platform;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                this.robot = new Robot();
            }
        } catch (Exception e) {
            this.robot = null;
        }
    }

    public boolean isAvailable() {
        return robot != null;
    }

    public String captureForegroundDataUrl(int maxDim) {
        if (robot == null) {
            return null;
        }
        Rectangle rect = foregroundRect();
        if (rect == null || rect.width < 8 || rect.height < 8) {
            return null;
        }
        try {
            BufferedImage shot = robot.createScreenCapture(rect);
            BufferedImage scaled = downscale(shot, maxDim);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private Rectangle foregroundRect() {
        int[] r = platform != null ? platform.foregroundWindowRect() : null;
        if (r != null && r.length == 4 && r[2] > r[0] && r[3] > r[1]) {

            int x = Math.max(0, r[0]);
            int y = Math.max(0, r[1]);
            int w = Math.min(r[2] - r[0], 8192);
            int h = Math.min(r[3] - r[1], 8192);
            if (w >= 8 && h >= 8) {
                return new Rectangle(x, y, w, h);
            }
        }
        try {
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, d.width, d.height);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage downscale(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        double s = Math.min(1.0, (double) maxDim / Math.max(w, h));
        if (s >= 1.0) {
            return src;
        }
        int nw = Math.max(1, (int) Math.round(w * s));
        int nh = Math.max(1, (int) Math.round(h * s));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }
}
