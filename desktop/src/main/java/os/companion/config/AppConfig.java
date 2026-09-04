package os.companion.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppConfig {

    public static final String DEFAULT_MODEL_NAME = "phi-4-mini-instruct";
    public static final String DEFAULT_MODEL_QUANTIZATION = "Q4_K_M";
    public static final String DEFAULT_MODEL_FILE = "Phi-4-mini-instruct-Q4_K_M.gguf";

    public static final String LLAMA_SERVER_HOST = "127.0.0.1";
    public static final int LLAMA_SERVER_PORT = 8081;

    public static final String OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    public static final String OLLAMA_MODEL = "companion-phi4-mini";

    public static final String OLLAMA_VISION_MODEL = "moondream";

    private final Path assetsDir;

    private AppConfig(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    public static AppConfig discover() {
        return new AppConfig(resolveAssetsDir());
    }

    private static Path resolveAssetsDir() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("assets");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }

        return Paths.get("").toAbsolutePath().resolve("assets");
    }

    public Path assetsDir() {
        return assetsDir;
    }

    public Path characterDir(String name) {
        return assetsDir.resolve("characters").resolve(name);
    }

    public Path modelsDir() {
        return assetsDir.getParent().resolve("models");
    }

    public Path speechDir() {
        return assetsDir.getParent().resolve("speech");
    }

    public Path defaultModelPath() {
        return modelsDir().resolve(DEFAULT_MODEL_FILE);
    }
}
