package os.companion.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import os.companion.config.AppConfig;

import java.time.Duration;
import java.util.List;

public final class AiSmokeMain {

    private static final Logger log = LoggerFactory.getLogger(AiSmokeMain.class);

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.discover();
        String persona = new PersonaLoader().loadOrDefault(
                config.characterDir("lily").resolve("persona.md"), "Lily");

        log.info("backend : Ollama {} model={}", AppConfig.OLLAMA_BASE_URL, AppConfig.OLLAMA_MODEL);

        try (LocalModel model = LocalModel.forExternalServer(
                AppConfig.OLLAMA_BASE_URL, AppConfig.OLLAMA_MODEL,
                persona,  6)) {

            log.info("checking server / warming model (first token can take ~20-30s)...");
            model.start(Duration.ofSeconds(120));

            for (String utterance : List.of(
                    "Hi Lily, are you awake?",
                    "What should I do this evening?",
                    "Alright, thanks!")) {
                long t0 = System.nanoTime();
                CompanionResponse r = model.ask(utterance);
                double ms = (System.nanoTime() - t0) / 1_000_000.0;
                System.out.printf("%nUSER : %s%n", utterance);
                System.out.printf("LILY : %s%n", r.speech());
                System.out.printf("  emotion=%s gesture=%s energy=%.2f  (%.0f ms)%n",
                        r.emotion(), r.gesture(), r.energy(), ms);
            }
        }
    }
}
