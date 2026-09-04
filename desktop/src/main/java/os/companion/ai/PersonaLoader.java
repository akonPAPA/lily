package os.companion.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PersonaLoader {

    private static final int MAX_PERSONA_CHARS = 8_000;

    public String load(Path personaMd) throws IOException {
        if (!Files.isRegularFile(personaMd)) {
            throw new IOException("persona.md not found: " + personaMd);
        }
        String text = Files.readString(personaMd);
        if (text.length() > MAX_PERSONA_CHARS) {
            text = text.substring(0, MAX_PERSONA_CHARS);
        }
        return text.strip();
    }

    public String loadOrDefault(Path personaMd, String characterName) {
        try {
            return load(personaMd);
        } catch (IOException e) {
            return "# Identity\nName: " + characterName + "\nRole: Desktop companion\n";
        }
    }
}
