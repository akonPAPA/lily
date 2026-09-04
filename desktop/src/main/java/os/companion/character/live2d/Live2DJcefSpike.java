package os.companion.character.live2d;

import com.sun.net.httpserver.HttpServer;
import me.friwi.jcefmaven.CefAppBuilder;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Live2DJcefSpike {

    public static void main(String[] args) throws Exception {
        Path dir = resolveLive2dDir();
        int port = startServer(dir);
        String url = "http://127.0.0.1:" + port + "/index.html";
        System.out.println("serving " + dir + " at " + url);

        boolean osr = Boolean.getBoolean("companion.osr");

        CefAppBuilder builder = new CefAppBuilder();
        builder.getCefSettings().windowless_rendering_enabled = osr;
        builder.setInstallDir(new File("jcef-bundle"));
        builder.setProgressHandler((state, percent) ->
                System.out.println("[jcef] " + state + (percent >= 0 ? " " + (int) percent + "%" : "")));
        builder.addJcefArgs("--ignore-gpu-blocklist", "--enable-gpu-rasterization",
                "--enable-unsafe-swiftshader");

        System.out.println("initializing CEF (first run downloads Chromium ~150-200MB)...");
        CefApp cefApp = builder.build();
        CefClient client = cefApp.createClient();

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser b, org.cef.CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                System.out.println("[console] " + message);
                return false;
            }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser b, CefFrame frame, int httpStatusCode) {
                System.out.println("[loadend] http=" + httpStatusCode + " url=" + b.getURL());
            }
            @Override
            public void onLoadError(CefBrowser b, CefFrame frame, ErrorCode errorCode,
                                    String errorText, String failedUrl) {
                System.out.println("[loaderror] " + errorCode + " " + errorText + " url=" + failedUrl);
            }
        });

        CefBrowser browser = client.createBrowser(url, osr, osr);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CompanionOS-Live2D");
            if (osr) {
                frame.setUndecorated(true);
                frame.setBackground(new Color(0, 0, 0, 0));
                frame.setAlwaysOnTop(true);
            }

            frame.setSize(420, 620);
            frame.setLocationRelativeTo(null);
            frame.add(browser.getUIComponent());
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    CefApp.getInstance().dispose();
                    frame.dispose();
                    System.exit(0);
                }
            });
            frame.setVisible(true);
            System.out.println("overlay window shown");
        });

        new Thread(() -> {
            try {
                Thread.sleep(9000);
            } catch (InterruptedException e) {
                return;
            }
            String js = "console.log('DIAG status='+window.__live2dStatus"
                    + "+' model='+(!!window.model)+' pixi='+(typeof PIXI)"
                    + "+' l2d='+(!!(window.PIXI&&PIXI.live2d&&PIXI.live2d.Live2DModel))"
                    + "+' gl='+((function(){try{var c=document.createElement('canvas');"
                    + "return !!(c.getContext('webgl')||c.getContext('webgl2'));}catch(e){return 'err';}})()));";
            browser.executeJavaScript(js, browser.getURL(), 0);
        }, "live2d-diag").start();
    }

    private static Path resolveLive2dDir() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("live2d");
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("index.html"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return Paths.get("live2d").toAbsolutePath();
    }

    private static int startServer(Path root) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String p = exchange.getRequestURI().getPath();
            if (p.equals("/") || p.isBlank()) {
                p = "/index.html";
            }
            Path file = root.resolve(p.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static String contentType(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
