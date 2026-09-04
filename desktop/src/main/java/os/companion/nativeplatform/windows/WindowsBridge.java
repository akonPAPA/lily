package os.companion.nativeplatform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import os.companion.nativeplatform.PlatformBridge;

import java.util.ArrayList;
import java.util.List;

public final class WindowsBridge implements PlatformBridge {

    private static final Logger log = LoggerFactory.getLogger(WindowsBridge.class);

    private static final HWND HWND_TOPMOST = new HWND(Pointer.createConstant(-1));
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_SHOWWINDOW = 0x0040;

    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TOPMOST = 0x00000008;
    private static final int WS_EX_LAYERED = 0x00080000;
    private static final int WS_EX_TRANSPARENT = 0x00000020;

    private static final int MIN_WINDOW_PX = 16;

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public boolean isSupported() {
        return isWindows();
    }

    @Override
    public boolean setAlwaysOnTop(String windowTitle) {

        List<HWND> windows = currentProcessVisibleWindows();
        if (windows.isEmpty()) {
            return false;
        }
        boolean allTopmost = true;
        for (HWND hwnd : windows) {
            User32.INSTANCE.SetWindowPos(
                    hwnd, HWND_TOPMOST, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW);

            int exStyle = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE);
            allTopmost = allTopmost && (exStyle & WS_EX_TOPMOST) != 0;
        }
        return allTopmost;
    }

    @Override
    public void setClickThrough(String windowTitle, boolean clickThrough) {
        for (HWND hwnd : currentProcessVisibleWindows()) {
            try {
                int exStyle = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE);
                if (clickThrough) {
                    exStyle |= WS_EX_LAYERED | WS_EX_TRANSPARENT;
                } else {
                    exStyle &= ~WS_EX_TRANSPARENT;
                }
                User32.INSTANCE.SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
            } catch (Throwable t) {
                log.debug("setClickThrough failed: {}", t.toString());
            }
        }
    }

    @Override
    public int[] cursorPosition() {
        if (!isWindows()) {
            return null;
        }
        try {
            POINT p = new POINT();
            if (User32.INSTANCE.GetCursorPos(p)) {
                return new int[]{p.x, p.y};
            }
        } catch (Throwable t) {
            log.debug("GetCursorPos failed: {}", t.toString());
        }
        return null;
    }

    @Override
    public void moveCursor(int x, int y) {
        if (!isWindows()) {
            return;
        }
        try {
            User32.INSTANCE.SetCursorPos(x, y);
        } catch (Throwable t) {
            log.debug("SetCursorPos failed: {}", t.toString());
        }
    }

    @Override
    public int[] foregroundWindowRect() {
        HWND hwnd = foreground();
        if (hwnd == null) {
            return null;
        }
        try {
            RECT r = new RECT();
            if (User32.INSTANCE.GetWindowRect(hwnd, r)) {
                return new int[]{r.left, r.top, r.right, r.bottom};
            }
        } catch (Throwable t) {
            log.debug("GetWindowRect failed: {}", t.toString());
        }
        return null;
    }

    @Override
    public String foregroundWindowTitle() {
        HWND hwnd = foreground();
        if (hwnd == null) {
            return "";
        }
        try {
            char[] buf = new char[512];
            int len = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
            return len > 0 ? new String(buf, 0, len) : "";
        } catch (Throwable t) {
            log.debug("GetWindowText failed: {}", t.toString());
            return "";
        }
    }

    @Override
    public boolean isKeyDown(int virtualKey) {
        if (!isWindows()) {
            return false;
        }
        try {
            return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & 0x8000) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void moveForegroundWindow(int dx, int dy) {
        HWND hwnd = foreground();
        if (hwnd == null || isOwnWindow(hwnd)) {
            return;
        }
        try {
            RECT r = new RECT();
            if (!User32.INSTANCE.GetWindowRect(hwnd, r)) {
                return;
            }
            User32.INSTANCE.SetWindowPos(hwnd, null, r.left + dx, r.top + dy, 0, 0,
                    SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
        } catch (Throwable t) {
            log.debug("moveForegroundWindow failed: {}", t.toString());
        }
    }

    private boolean isOwnWindow(HWND hwnd) {
        try {
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            return pid.getValue() == Kernel32.INSTANCE.GetCurrentProcessId();
        } catch (Throwable t) {
            return false;
        }
    }

    private HWND foreground() {
        if (!isWindows()) {
            return null;
        }
        try {
            return User32.INSTANCE.GetForegroundWindow();
        } catch (Throwable t) {
            return null;
        }
    }

    private List<HWND> currentProcessVisibleWindows() {
        List<HWND> result = new ArrayList<>();
        if (!isWindows()) {
            return result;
        }
        try {
            int myPid = Kernel32.INSTANCE.GetCurrentProcessId();
            WNDENUMPROC cb = (hwnd, data) -> {
                try {
                    IntByReference pid = new IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
                    if (pid.getValue() == myPid
                            && User32.INSTANCE.IsWindowVisible(hwnd)
                            && isBigEnough(hwnd)) {
                        result.add(hwnd);
                    }
                } catch (Throwable ignored) {

                }
                return true;
            };
            User32.INSTANCE.EnumWindows(cb, null);
        } catch (Throwable t) {
            log.debug("EnumWindows failed: {}", t.toString());
        }
        return result;
    }

    private boolean isBigEnough(HWND hwnd) {
        RECT r = new RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, r)) {
            return false;
        }
        return (r.right - r.left) >= MIN_WINDOW_PX && (r.bottom - r.top) >= MIN_WINDOW_PX;
    }
}
