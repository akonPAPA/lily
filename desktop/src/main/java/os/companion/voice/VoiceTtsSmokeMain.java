package os.companion.voice;

import os.companion.config.AppConfig;

import java.nio.file.Path;

public final class VoiceTtsSmokeMain {

    public static void main(String[] args) {
        AppConfig cfg = AppConfig.discover();
        Path tts = cfg.speechDir().resolve("tts");
        System.out.println("loading voices from " + tts);

        try (TextToSpeech voice = TextToSpeech.load(
                tts.resolve("vits-piper-en_US-amy-medium-int8"), "en_US-amy-medium.onnx",
                tts.resolve("vits-piper-ru_RU-irina-medium-int8"), "ru_RU-irina-medium.onnx")) {

            System.out.println("EN: speaking...");
            voice.speak("Hi! I'm Lily, your desktop companion. Nice to meet you.",
                    LanguageRouter.Lang.EN);

            System.out.println("RU: speaking...");
            voice.speak("Привет! Я Лили, твой компаньон на рабочем столе.",
                    LanguageRouter.Lang.RU);

            System.out.println("OK — both voices spoke without error.");
        }
    }
}
