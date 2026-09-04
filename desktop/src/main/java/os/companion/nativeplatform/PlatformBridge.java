package os.companion.nativeplatform;

public interface PlatformBridge {

    boolean setAlwaysOnTop(String windowTitle);

    void setClickThrough(String windowTitle, boolean clickThrough);

    int[] cursorPosition();

    void moveCursor(int x, int y);

    int[] foregroundWindowRect();

    String foregroundWindowTitle();

    void moveForegroundWindow(int dx, int dy);

    boolean isKeyDown(int virtualKey);

    boolean isSupported();

    static PlatformBridge noop() {
        return new PlatformBridge() {
            @Override public boolean setAlwaysOnTop(String windowTitle) { return false; }
            @Override public void setClickThrough(String windowTitle, boolean clickThrough) { }
            @Override public int[] cursorPosition() { return null; }
            @Override public void moveCursor(int x, int y) { }
            @Override public int[] foregroundWindowRect() { return null; }
            @Override public String foregroundWindowTitle() { return ""; }
            @Override public void moveForegroundWindow(int dx, int dy) { }
            @Override public boolean isKeyDown(int virtualKey) { return false; }
            @Override public boolean isSupported() { return false; }
        };
    }
}
