package os.companion.nativeplatform;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;

public final class ClipboardReader {

    private static final int MAX_CHARS = 180;

    public String readText() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return null;
            }
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb == null || !cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }
            Object data = cb.getData(DataFlavor.stringFlavor);
            if (!(data instanceof String s)) {
                return null;
            }
            String text = s.strip();
            if (text.isEmpty()) {
                return null;
            }
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "…" : text;
        } catch (Throwable t) {
            return null;
        }
    }
}
